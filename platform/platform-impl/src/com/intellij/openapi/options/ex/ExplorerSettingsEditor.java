/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options.ex;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.ui.search.DefaultSearchableConfigurable;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.Set;

public class ExplorerSettingsEditor extends DialogWrapper {
  /** When you visit the same editor next time you see the same selected configurable. */
  private static final TObjectIntHashMap<String> ourGroup2LastConfigurableIndex = new TObjectIntHashMap<String>();
  private static String ourLastGroup;

  private final Project myProject;
  private int myKeySelectedConfigurableIndex;

  private final ConfigurableGroup[] myGroups;

  /** Configurable which is currently selected. */
  private Configurable mySelectedConfigurable;
  private ConfigurableGroup mySelectedGroup;
  private JPanel myOptionsPanel;

  private final Map<Configurable, JComponent> myInitializedConfigurables2Component;
  private final Dimension myPreferredSize;
  private final Map<Configurable, Dimension> myConfigurable2PrefSize;
  private JButton myHelpButton;
  private JPanel myComponentPanel;
  private SearchUtil.ConfigurableSearchTextField mySearchField;
  private Set<Configurable> myOptionContainers = null;
  private final Alarm mySearchUpdater = new Alarm();
  private JTree myTree;
  @NonNls private final DefaultMutableTreeNode myRoot = new DefaultMutableTreeNode("Root");
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.ex.ExplorerSettingsEditor");

  private final JBPopup [] myPopup = new JBPopup[2];

  private static final TObjectHashingStrategy<Configurable> UNWRAPPING_STRATEGY = new TObjectHashingStrategy<Configurable>() {
    public int computeHashCode(final Configurable c) {
      return unwrap(c).hashCode();
    }

    public boolean equals(final Configurable c1, final Configurable c2) {
      return unwrap(c1) == unwrap(c2);
    }

    private Configurable unwrap(Configurable c) {
      if (c instanceof DefaultSearchableConfigurable) {
        return ((DefaultSearchableConfigurable)c).getDelegate();
      }
      return c;
    }
  };

  public ExplorerSettingsEditor(Project project, ConfigurableGroup[] group) {
    super(project, true);
    myProject = project;
    myPreferredSize = new Dimension(800, 600);
    myGroups = group;

    if (myGroups.length == 0) {
      throw new IllegalStateException("number of configurables must be more then zero");
    }

    myInitializedConfigurables2Component = new THashMap<Configurable, JComponent>(UNWRAPPING_STRATEGY);
    myConfigurable2PrefSize = new THashMap<Configurable, Dimension>(UNWRAPPING_STRATEGY);

    init();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.options.ex.ExplorerSettingsEditor";
  }

  protected final void init() {
    super.init();

    int lastGroup = 0;
    for (int i = 0; i < myGroups.length; i++) {
      ConfigurableGroup group = myGroups[i];
      if (Comparing.equal(group.getShortName(), ourLastGroup)) {
        lastGroup = i;
        break;
      }
    }

    selectGroup(lastGroup);
  }

  private void selectGroup(int groupIdx) {
    final String shortName = myGroups[groupIdx].getShortName();
    int lastIndex = ourGroup2LastConfigurableIndex.get(shortName);
    if (lastIndex == -1) lastIndex = 0;
    selectGroup(groupIdx,lastIndex);
  }
  private void selectGroup(int groupIdx, int indexToSelect) {
    rememberLastUsedPage();

    mySelectedGroup = myGroups[groupIdx];
    ourLastGroup = mySelectedGroup.getShortName();

    final DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode)myRoot.getChildAt(groupIdx);
    myTree.expandPath(new TreePath(groupNode.getPath()));
    TreeUtil.selectNode(myTree, groupNode.getChildAt(indexToSelect));

    Configurable[] configurables = mySelectedGroup.getConfigurables();
    Configurable toSelect = configurables[indexToSelect];

    selectConfigurable(toSelect, indexToSelect);

    requestFocusForMainPanel();
  }

  private void rememberLastUsedPage() {
    if (mySelectedGroup != null) {
      Configurable[] configurables = mySelectedGroup.getConfigurables();
      int index = -1;
      for (int i = 0; i < configurables.length; i++) {
        Configurable configurable = configurables[i];
        if (UNWRAPPING_STRATEGY.equals(configurable, mySelectedConfigurable)) {
          index = i;
          break;
        }
      }
      ourGroup2LastConfigurableIndex.put(mySelectedGroup.getShortName(), index);
    }
  }

  private void updateTitle() {
    if (mySelectedConfigurable == null) {
      setTitle(OptionsBundle.message("settings.panel.title"));
    }
    else {
      String displayName = mySelectedConfigurable.getDisplayName();
      setTitle(mySelectedGroup.getDisplayName() + " - " + (displayName != null ? displayName.replace('\n', ' ') : ""));
      if (myHelpButton != null) {
        myHelpButton.setEnabled(mySelectedConfigurable.getHelpTopic() != null);
      }
    }
  }

  /**
   * @return false if failed
   */
  protected boolean apply() {
    if (mySelectedConfigurable == null || !mySelectedConfigurable.isModified()) {
      return true;
    }

    try {
      mySelectedConfigurable.apply();
      return true;
    }
    catch (ConfigurationException e) {
      if (e.getMessage() != null) {
        Messages.showMessageDialog(e.getMessage(), e.getTitle(), Messages.getErrorIcon());
      }
      return false;
    }
  }

  public final void dispose() {
    for (JBPopup popup : myPopup) {
      if (popup != null) {
        popup.cancel();
      }
    }
    mySearchUpdater.cancelAllRequests();
    myAlarm.cancelAllRequests();
    rememberLastUsedPage();

    //do not dispose resources if components weren't initialized
    for (Configurable configurable : myInitializedConfigurables2Component.keySet()) {
      configurable.disposeUIResources();
    }

    mySelectedConfigurable = null;
    myOptionContainers = null;
    myInitializedConfigurables2Component.clear();
    super.dispose();
  }

  public JComponent getPreferredFocusedComponent() {
    return myComponentPanel;
  }

  protected final JComponent createCenterPanel() {
    myComponentPanel = new JPanel(new BorderLayout());

    // myOptionPanel contains all configurables. When it updates its UI we also need to update
    // UIs of all created but not currently visible configurables.

    myOptionsPanel = new JPanel(new BorderLayout()) {
      public void updateUI() {
        super.updateUI();
        for (Configurable configurable : myInitializedConfigurables2Component.keySet()) {
          if (configurable.equals(mySelectedConfigurable)) { // don't update visible component (optimization)
            continue;
          }
          JComponent component = myInitializedConfigurables2Component.get(configurable);
          SwingUtilities.updateComponentTreeUI(component);
        }
      }
    };

    initTree();
    initToolbar();
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

    TreeUtil.expandAll(myTree);
    final Dimension preferredSize = new Dimension(myTree.getPreferredSize().width + 20,
                                                  scrollPane.getPreferredSize().height);
    scrollPane.setPreferredSize(preferredSize);
    scrollPane.setMinimumSize(preferredSize);
    TreeUtil.collapseAll(myTree, 1);

    final JPanel leftPane = new JPanel(new BorderLayout());
    leftPane.setBorder(BorderFactory.createRaisedBevelBorder());
    leftPane.add(scrollPane, BorderLayout.CENTER);
    myComponentPanel.add(leftPane, BorderLayout.WEST);

    myOptionsPanel.setBorder(BorderFactory.createEmptyBorder(15, 5, 2, 5));

    JScrollPane optionsScrollForTinyScreens = ScrollPaneFactory.createScrollPane(myOptionsPanel);
    optionsScrollForTinyScreens.setBorder(null);
    
    myComponentPanel.add(optionsScrollForTinyScreens, BorderLayout.CENTER);

    optionsScrollForTinyScreens.setPreferredSize(myPreferredSize);

    myComponentPanel.setFocusable(true);
    final KeyAdapter keyAdapter = new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        Configurable[] configurables = mySelectedGroup.getConfigurables();
        int index = myKeySelectedConfigurableIndex;
        if (index == -1) return;
        int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_UP) {
          index--;
          if (index == -1) {
            final int groupIdx = ArrayUtil.find(myGroups, mySelectedGroup);
            if (groupIdx > 0) {
              selectGroup(groupIdx - 1, myGroups[groupIdx - 1].getConfigurables().length - 1);
              return;
            }
          }
        }
        else if (keyCode == KeyEvent.VK_DOWN) {
          index++;
          if (index == configurables.length) {
            final int groupIdx = ArrayUtil.find(myGroups, mySelectedGroup);
            if (groupIdx < myGroups.length - 1) {
              selectGroup(groupIdx + 1, 0);
              return;
            }
          }
        }
        else {
          Configurable configurableFromMnemonic = ControlPanelMnemonicsUtil.getConfigurableFromMnemonic(e, myGroups);
          if (configurableFromMnemonic == null) return;
          int keyGroupIndex = -1;
          ConfigurableGroup keyGroup = null;
          int keyIndexInGroup = 0;
          for (int i = 0; i < myGroups.length; i++) {
            ConfigurableGroup group = myGroups[i];
            int ingroupIdx = ArrayUtil.find(group.getConfigurables(), configurableFromMnemonic);
            if (ingroupIdx != -1) {
              keyGroupIndex = i;
              keyGroup = group;
              keyIndexInGroup = ingroupIdx;
              break;
            }
          }
          if (mySelectedGroup != keyGroup) {
            selectGroup(keyGroupIndex, keyIndexInGroup);
            return;
          }
          index = ControlPanelMnemonicsUtil.getIndexFromKeycode(keyCode, mySelectedGroup == myGroups[0]);
        }
        if (index == -1 || index >= configurables.length) return;
        final TreeNode groupNode = myRoot.getChildAt(ArrayUtil.find(myGroups, mySelectedGroup));
        TreeUtil.selectPath(myTree, new TreePath(new TreeNode[]{myRoot, groupNode, groupNode.getChildAt(index)}));
      }
    };
    myComponentPanel.addKeyListener(keyAdapter);
    Disposer.register(myDisposable, new Disposable() {
      public void dispose() {
        myComponentPanel.removeKeyListener(keyAdapter);
      }
    });

    return myComponentPanel;
  }

  private void initTree() {
    myTree = new Tree(myRoot){
      public Dimension getPreferredScrollableViewportSize() {
        Dimension size = super.getPreferredScrollableViewportSize();
        size = new Dimension(size.width + 10, size.height);
        return size;
      }
    };
    //noinspection NonStaticInitializer
    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
      {
        setFocusBorderAroundIcon(true);
      }
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode){
          Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          if (userObject instanceof Pair){
            final Pair configurableWithMnemonics = ((Pair)userObject);
            final Configurable configurable = (Configurable)configurableWithMnemonics.first;
            setIcon(configurable.getIcon());
            append(configurable.getDisplayName().replaceAll("\n", " "), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            append(" ( " + configurableWithMnemonics.second + " )", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          } else if (userObject instanceof String){
            setIcon(null);
            append((String)userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          }
        }
      }
    });

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final Object node = myTree.getLastSelectedPathComponent();
        if (node instanceof DefaultMutableTreeNode) {
          final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
          final Object userObject = treeNode.getUserObject();
          if (userObject instanceof Pair){
            final Pair configurableWithMnemonic = (Pair)userObject;
            final Configurable configurable = (Configurable)configurableWithMnemonic.first;
            final TreeNode[] nodes = treeNode.getPath();
            LOG.assertTrue(nodes != null && nodes.length > 0 && nodes[1] != null);
            final int groupIdx = myRoot.getIndex(nodes[1]);
            selectConfigurableLater(configurable, ArrayUtil.find(myGroups[groupIdx].getConfigurables(), configurable));
            rememberLastUsedPage();
            mySelectedGroup = myGroups[groupIdx];
            ourLastGroup = mySelectedGroup.getShortName();
          }
        }
      }
    });
    myTree.setRowHeight(32);
    TreeUtil.installActions(myTree);
    UIUtil.setLineStyleAngled(myTree);
    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(false);
  }

  protected JComponent createNorthPanel() {
    final Consumer<String> selectConfigurable = new Consumer<String>() {
      public void consume(final String configurableId) {
        if (myOptionContainers != null) {
          for (int groupIdx = 0; groupIdx < myGroups.length; groupIdx++) {
            final ConfigurableGroup group = myGroups[groupIdx];
            final Configurable[] configurables = group.getConfigurables();
            int idx = 0;
            for (Configurable configurable : configurables) {
              if (myOptionContainers.contains(configurable)) {
                if (Comparing.strEqual(configurable.getDisplayName(), configurableId)) {
                  rememberLastUsedPage();
                  mySelectedGroup = myGroups[groupIdx];
                  ourLastGroup = mySelectedGroup.getShortName();

                  final DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode)myRoot.getChildAt(groupIdx);
                  myTree.expandPath(new TreePath(groupNode.getPath()));
                  TreeUtil.selectNode(myTree, groupNode.getChildAt(idx));

                  selectConfigurable(configurable, idx);

                  requestFocusForMainPanel();
                  return;
                }
                idx++;
              }
            }
          }
        }
      }
    };
    final SearchableOptionsRegistrar optionsRegistrar = SearchableOptionsRegistrar.getInstance();
    final JPanel panel = new JPanel(new GridBagLayout());
    mySearchField = new SearchUtil.ConfigurableSearchTextField();
    final DocumentAdapter documentAdapter = new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        mySearchUpdater.cancelAllRequests();
        mySearchUpdater.addRequest(new Runnable() {
          public void run() {
            final @NonNls String searchPattern = mySearchField.getText();
            if (searchPattern != null && searchPattern.length() > 0) {
              myOptionContainers = optionsRegistrar.getConfigurables(myGroups, e.getType(), myOptionContainers, searchPattern, myProject).getContentHits();
            }
            else {
              myOptionContainers = null;
            }
            SearchUtil.showHintPopup(mySearchField, myPopup, mySearchUpdater, selectConfigurable, myProject);
            initToolbar();
            TreeUtil.expandAll(myTree);
            if (mySelectedConfigurable instanceof SearchableConfigurable) {
              selectOption(new DefaultSearchableConfigurable((SearchableConfigurable)mySelectedConfigurable));
            }
            myComponentPanel.revalidate();
            myComponentPanel.repaint();
          }
        }, 300, ModalityState.defaultModalityState());
      }
    };
    mySearchField.addDocumentListener(documentAdapter);
    Disposer.register(myDisposable, new Disposable(){
      public void dispose() {
        if (mySearchField != null) {
          panel.remove(mySearchField);
          mySearchField.removeDocumentListener(documentAdapter);
          mySearchField = null;
        }
      }
    });
    SearchUtil.registerKeyboardNavigation(mySearchField, myPopup, mySearchUpdater, selectConfigurable, myProject);
    final GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    panel.add(Box.createHorizontalBox(), gc);

    gc.gridx++;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    final JLabel label = new JLabel(IdeBundle.message("search.textfield.title"));
    panel.add(label, gc);
    label.setLabelFor(mySearchField);

    gc.gridx++;
    final int height = mySearchField.getPreferredSize().height;
    mySearchField.setPreferredSize(new Dimension(100, height));
    panel.add(mySearchField, gc);

    return panel;
  }

  private void requestFocusForMainPanel() {
    myComponentPanel.requestFocus();
  }

  private void initToolbar() {
    myRoot.removeAllChildren();
    char mnemonicStartChar = '1';
    for (ConfigurableGroup group : myGroups) {
      DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(group.getDisplayName());
      final Configurable[] configurables = group.getConfigurables();
      for (int i = 0; i < configurables.length; i++){
        Configurable configurable = configurables[i];
        if (myOptionContainers == null || myOptionContainers.contains(configurable)) {
          groupNode.add(new DefaultMutableTreeNode(Pair.create(configurable, (char)(mnemonicStartChar + i))));
        }
      }
      mnemonicStartChar = 'A';
      myRoot.add(groupNode);
    }
    ((DefaultTreeModel)myTree.getModel()).reload();
  }

  private final Alarm myAlarm = new Alarm();
  private void selectConfigurableLater(final Configurable configurable, final int index) {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        if (isShowing()) {
          selectConfigurable(configurable, index);
        }
      }
    }, 400);
    myKeySelectedConfigurableIndex = index;

    myComponentPanel.repaint();
  }

  /**
   * Selects configurable with specified <code>class</code>. If there is no configurable of <code>class</code>
   * then the method does nothing.
   */
  private void selectConfigurable(Configurable configurable, int index) {
    // If nothing to be selected then clear panel with configurable's options.
    if (configurable == null) {
      mySelectedConfigurable = null;
      myKeySelectedConfigurableIndex = 0;
      updateTitle();
      myOptionsPanel.removeAll();
      validate();
      repaint();
      return;
    }

    // Save changes if any
    Dimension currentOptionsSize = myOptionsPanel.getSize();

    if (mySelectedConfigurable != null && mySelectedConfigurable.isModified()) {
      int exitCode = Messages.showYesNoDialog(OptionsBundle.message("options.page.modified.save.message.text"),
                                              OptionsBundle.message("options.save.changes.message.title"),
                                              Messages.getQuestionIcon());
      if (exitCode == 0) {
        try {
          mySelectedConfigurable.apply();
        }
        catch (ConfigurationException exc) {
          if (exc.getMessage() != null) {
            Messages.showMessageDialog(exc.getMessage(), exc.getTitle(), Messages.getErrorIcon());
          }
          return;
        }
      }
    }

    if (mySelectedConfigurable != null) {
      Dimension savedPrefferedSize = myConfigurable2PrefSize.get(mySelectedConfigurable);
      if (savedPrefferedSize != null) {
        myConfigurable2PrefSize.put(mySelectedConfigurable, new Dimension(currentOptionsSize));
      }
    }

    // Show new configurable
    myComponentPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

    myOptionsPanel.removeAll();

    mySelectedConfigurable = configurable;
    myKeySelectedConfigurableIndex = index;

    if (configurable instanceof SearchableConfigurable){
      configurable = new DefaultSearchableConfigurable((SearchableConfigurable)configurable);
    }

    JComponent component = myInitializedConfigurables2Component.get(configurable);
    if (component == null) {
      component = configurable.createComponent();
      myInitializedConfigurables2Component.put(configurable, component);
    }

    Dimension compPrefSize;
    if (myConfigurable2PrefSize.containsKey(configurable)) {
      compPrefSize = myConfigurable2PrefSize.get(configurable);
    }
    else {
      compPrefSize = component.getPreferredSize();
      myConfigurable2PrefSize.put(configurable, compPrefSize);
    }
    int widthDelta = Math.max(compPrefSize.width - currentOptionsSize.width, 0);
    int heightDelta = Math.max(compPrefSize.height - currentOptionsSize.height, 0);
    myOptionsPanel.add(component, BorderLayout.CENTER);
    if (widthDelta > 0 || heightDelta > 0) {
      setSize(getSize().width + widthDelta, getSize().height + heightDelta);
//      centerRelativeToParent();
    }

    configurable.reset();

    updateTitle();
    validate();
    repaint();

    requestFocusForMainPanel();
    myComponentPanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    if (configurable instanceof DefaultSearchableConfigurable){
      selectOption((DefaultSearchableConfigurable)configurable);
    }
  }

  private void selectOption(final DefaultSearchableConfigurable searchableConfigurable) {
    searchableConfigurable.clearSearch();
    if (myOptionContainers == null || myOptionContainers.isEmpty()) return; //do not highlight current editor when nothing can be selected
    @NonNls final String filter = mySearchField.getText();
    if (filter != null && filter.length() > 0 ){
      searchableConfigurable.enableSearch(filter);
    }
  }

  protected final Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), new ApplyAction(), getHelpAction()};
  }

  protected JButton createJButtonForAction(Action action) {
    JButton button = super.createJButtonForAction(action);
    if (action == getHelpAction()) {
      myHelpButton = button;
    }
    return button;
  }

  protected Action[] createLeftSideActions() {
    return new Action[]{new SwitchToDefaultViewAction()};
  }

  protected final void doOKAction() {
    boolean ok = apply();
    if (ok) {
      super.doOKAction();
    }
  }

  protected final void doHelpAction() {
    if (mySelectedConfigurable != null) {
      String helpTopic = mySelectedConfigurable.getHelpTopic();
      if (helpTopic != null) {
        HelpManager.getInstance().invokeHelp(helpTopic);
      }
    }
  }

  private final class ApplyAction extends AbstractAction {
    private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    public ApplyAction() {
      super(OptionsBundle.message("options.apply.button"));
      final Runnable updateRequest = new Runnable() {
        public void run() {
          if (!isShowing()) return;
          setEnabled(mySelectedConfigurable != null && mySelectedConfigurable.isModified());
          addUpdateRequest(this);
        }
      };

      addUpdateRequest(updateRequest);
    }

    private void addUpdateRequest(final Runnable updateRequest) {
      myUpdateAlarm.addRequest(updateRequest, 500, ModalityState.stateForComponent(getWindow()));
    }

    public void actionPerformed(ActionEvent e) {
      if (myPerformAction) return;
      myPerformAction = true;
      try {
        if (apply()) {
          setCancelButtonText(CommonBundle.getCloseButtonText());
        }
      }
      finally {
        myPerformAction = false;
      }
    }
  }

  private class SwitchToDefaultViewAction extends AbstractAction {
    public SwitchToDefaultViewAction() {
      putValue(Action.NAME, OptionsBundle.message("explorer.panel.default.view.button"));
    }

    public void actionPerformed(ActionEvent e) {
      switchToDefaultView(mySelectedConfigurable);
    }
  }
  private void switchToDefaultView(final Configurable preselectedConfigurable) {
    if (preselectedConfigurable != null) {
      preselectedConfigurable.disposeUIResources();
    }
    close(OK_EXIT_CODE);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ShowSettingsUtilImpl.showControlPanelOptions(myProject, myGroups, null);
      }
    }, ModalityState.NON_MODAL);
  }
}
