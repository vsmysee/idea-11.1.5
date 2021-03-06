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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public abstract class ChangesTreeList<T> extends JPanel {
  private final Tree myTree;
  private final JBList myList;
  private final JScrollPane myTreeScrollPane;
  private final JScrollPane myListScrollPane;
  protected final Project myProject;
  private final boolean myShowCheckboxes;
  private final boolean myHighlightProblems;
  private boolean myShowFlatten;

  private final Collection<T> myIncludedChanges;
  private Runnable myDoubleClickHandler = EmptyRunnable.getInstance();
  private boolean myAlwaysExpandList;

  @NonNls private static final String TREE_CARD = "Tree";
  @NonNls private static final String LIST_CARD = "List";
  @NonNls private static final String ROOT = "root";
  private final CardLayout myCards;

  @NonNls private final static String FLATTEN_OPTION_KEY = "ChangesBrowser.SHOW_FLATTEN";

  private final Runnable myInclusionListener;
  @Nullable private ChangeNodeDecorator myChangeDecorator;
  private Runnable myGenericSelectionListener;

  public ChangesTreeList(final Project project, Collection<T> initiallyIncluded, final boolean showCheckboxes,
                         final boolean highlightProblems, @Nullable final Runnable inclusionListener, @Nullable final ChangeNodeDecorator decorator) {
    myProject = project;
    myShowCheckboxes = showCheckboxes;
    myHighlightProblems = highlightProblems;
    myInclusionListener = inclusionListener;
    myChangeDecorator = decorator;
    myIncludedChanges = new HashSet<T>(initiallyIncluded);
    myAlwaysExpandList = true;

    myCards = new CardLayout();

    setLayout(myCards);

    final int checkboxWidth = new JCheckBox().getPreferredSize().width;
    myTree = new Tree(ChangesBrowserNode.create(myProject, ROOT)) {
      public Dimension getPreferredScrollableViewportSize() {
        Dimension size = super.getPreferredScrollableViewportSize();
        size = new Dimension(size.width + 10, size.height);
        return size;
      }

      protected void processMouseEvent(MouseEvent e) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
          if (! myTree.isEnabled()) return;
          int row = myTree.getRowForLocation(e.getX(), e.getY());
          if (row >= 0) {
            final Rectangle baseRect = myTree.getRowBounds(row);
            baseRect.setSize(checkboxWidth, baseRect.height);
            if (baseRect.contains(e.getPoint())) {
              myTree.setSelectionRow(row);
              toggleSelection();
            }
          }
        }
        super.processMouseEvent(e);
      }

      public int getToggleClickCount() {
        return -1;
      }
    };

    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);

    myTree.setCellRenderer(new MyTreeCellRenderer());
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(TreePath o) {
        ChangesBrowserNode node = (ChangesBrowserNode) o.getLastPathComponent();
        return node.getTextPresentation();
      }
    });

    myList = new JBList(new DefaultListModel());
    myList.setVisibleRowCount(10);

    add(myListScrollPane = ScrollPaneFactory.createScrollPane(myList), LIST_CARD);
    add(myTreeScrollPane = ScrollPaneFactory.createScrollPane(myTree), TREE_CARD);

    new ListSpeedSearch(myList) {
      protected String getElementText(Object element) {
        if (element instanceof Change) {
          return ChangesUtil.getFilePath((Change)element).getName();
        }
        return super.getElementText(element);
      }
    };

    myList.setCellRenderer(new MyListCellRenderer());

    new MyToggleSelectionAction().registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), this);
    if (myShowCheckboxes) {
      registerKeyboardAction(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          includeSelection();
        }

      }, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

      registerKeyboardAction(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          excludeSelection();
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        final int idx = myList.locationToIndex(e.getPoint());
        if (idx >= 0) {
          final Rectangle baseRect = myList.getCellBounds(idx, idx);
          baseRect.setSize(checkboxWidth, baseRect.height);
          if (baseRect.contains(e.getPoint())) {
            toggleSelection();
            e.consume();
          }
          else if (e.getClickCount() == 2) {
            myDoubleClickHandler.run();
            e.consume();
          }
        }
      }
    });

    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        final int row = myTree.getRowForLocation(e.getPoint().x, e.getPoint().y);
        if (row >= 0) {
          final Rectangle baseRect = myTree.getRowBounds(row);
          baseRect.setSize(checkboxWidth, baseRect.height);
          if (!baseRect.contains(e.getPoint()) && e.getClickCount() == 2) {
            myDoubleClickHandler.run();
            e.consume();
          }
        }
      }
    });

    setShowFlatten(PropertiesComponent.getInstance(myProject).isTrueValue(FLATTEN_OPTION_KEY));

    String emptyText = StringUtil.capitalize(DiffBundle.message("diff.count.differences.status.text", 0));
    setEmptyText(emptyText);
  }

  public void setEmptyText(@NotNull String emptyText) {
    myTree.getEmptyText().setText(emptyText);
    myList.getEmptyText().setText(emptyText);
  }

  // generic, both for tree and list
  public void addSelectionListener(final Runnable runnable) {
    myGenericSelectionListener = runnable;
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        myGenericSelectionListener.run();
      }
    });
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        myGenericSelectionListener.run();
      }
    });
  }

  public void setChangeDecorator(@Nullable ChangeNodeDecorator changeDecorator) {
    myChangeDecorator = changeDecorator;
  }

  public void setDoubleClickHandler(final Runnable doubleClickHandler) {
    myDoubleClickHandler = doubleClickHandler;
  }

  public void installPopupHandler(ActionGroup group) {
    PopupHandler.installUnknownPopupHandler(myList, group, ActionManager.getInstance());
    PopupHandler.installUnknownPopupHandler(myTree, group, ActionManager.getInstance());
  }
  
  public JComponent getPreferredFocusedComponent() {
    return myShowFlatten ? myList : myTree;
  }

  public Dimension getPreferredSize() {
    return new Dimension(400, 400);
  }

  public boolean isShowFlatten() {
    return myShowFlatten;
  }

  public void setScrollPaneBorder(Border border) {
    myListScrollPane.setBorder(border);
    myTreeScrollPane.setBorder(border);
  }

  public void setShowFlatten(final boolean showFlatten) {
    final List<T> wasSelected = getSelectedChanges();
    myShowFlatten = showFlatten;
    myCards.show(this, myShowFlatten ? LIST_CARD : TREE_CARD);
    select(wasSelected);
    if (myList.hasFocus() || myTree.hasFocus()) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          requestFocus();
        }
      });
    }
  }


  public void requestFocus() {
    if (myShowFlatten) {
      myList.requestFocus();
    }
    else {
      myTree.requestFocus();
    }
  }

  public void setChangesToDisplay(final List<T> changes) {
    setChangesToDisplay(changes, null);
  }
  
  public void setChangesToDisplay(final List<T> changes, @Nullable final VirtualFile toSelect) {
    final boolean wasEmpty = myList.isEmpty();
    final List<T> sortedChanges = new ArrayList<T>(changes);
    Collections.sort(sortedChanges, new Comparator<T>() {
      public int compare(final T o1, final T o2) {
        return TreeModelBuilder.getPathForObject(o1).getName().compareToIgnoreCase(TreeModelBuilder.getPathForObject(o2).getName());
      }
    });

    final Set<Object> wasSelected = new HashSet<Object>(Arrays.asList(myList.getSelectedValues()));
    myList.setModel(new AbstractListModel() {
      @Override
      public int getSize() {
        return sortedChanges.size();
      }

      @Override
      public Object getElementAt(int index) {
        return sortedChanges.get(index);
      }
    });

    final DefaultTreeModel model = buildTreeModel(changes, myChangeDecorator);
    TreeState state = null;
    if (! myAlwaysExpandList && ! wasEmpty) {
      state = TreeState.createOn(myTree, (DefaultMutableTreeNode) myTree.getModel().getRoot());
    }
    myTree.setModel(model);
    if (! myAlwaysExpandList && ! wasEmpty) {
      state.applyTo(myTree, (DefaultMutableTreeNode) myTree.getModel().getRoot());

      final TIntArrayList indices = new TIntArrayList();
      for (int i = 0; i < sortedChanges.size(); i++) {
        T t = sortedChanges.get(i);
        if (wasSelected.contains(t)) {
          indices.add(i);
        }
      }
      myList.setSelectedIndices(indices.toNativeArray());
      return;
    }

    final Runnable runnable = new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        TreeUtil.expandAll(myTree);

        int listSelection = 0;
        int scrollRow = 0;

        if (myShowCheckboxes) {
          if (myIncludedChanges.size() > 0) {
            int count = 0;
            for (T change : changes) {
              if (myIncludedChanges.contains(change)) {
                listSelection = count;
                break;
              }
              count++;
            }

            ChangesBrowserNode root = (ChangesBrowserNode)model.getRoot();
            Enumeration enumeration = root.depthFirstEnumeration();

            while (enumeration.hasMoreElements()) {
              ChangesBrowserNode node = (ChangesBrowserNode)enumeration.nextElement();
              final CheckboxTree.NodeState state = getNodeStatus(node);
              if (node != root && state == CheckboxTree.NodeState.CLEAR) {
                myTree.collapsePath(new TreePath(node.getPath()));
              }
            }

            enumeration = root.depthFirstEnumeration();
            while (enumeration.hasMoreElements()) {
              ChangesBrowserNode node = (ChangesBrowserNode)enumeration.nextElement();
              final CheckboxTree.NodeState state = getNodeStatus(node);
              if (state == CheckboxTree.NodeState.FULL && node.isLeaf()) {
                scrollRow = myTree.getRowForPath(new TreePath(node.getPath()));
                break;
              }
            }
          }
        } else {
          if (toSelect != null) {
            ChangesBrowserNode root = (ChangesBrowserNode)model.getRoot();
            final int[] rowToSelect = new int[] {-1}; 
            TreeUtil.traverse(root, new TreeUtil.Traverse() {
              @Override
              public boolean accept(Object node) {
                if (node instanceof DefaultMutableTreeNode) {
                  Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
                  if (userObject instanceof Change) {
                    Change change = (Change)userObject;
                    VirtualFile virtualFile = change.getVirtualFile();
                    if ((virtualFile != null && virtualFile.equals(toSelect)) || seemsToBeMoved(change, toSelect)) {
                      TreeNode[] path = ((DefaultMutableTreeNode)node).getPath();
                      rowToSelect[0] = myTree.getRowForPath(new TreePath(path));
                    }
                  }
                }

                return rowToSelect[0] == -1;
              }
            });
            
            scrollRow = rowToSelect[0] == -1 ? scrollRow : rowToSelect[0];
          }
        }
        
        if (changes.size() > 0) {
          myList.setSelectedIndex(listSelection);
          myList.ensureIndexIsVisible(listSelection);

          myTree.setSelectionRow(scrollRow);
          TreeUtil.showRowCentered(myTree, scrollRow, false);
        }
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    } else {
      SwingUtilities.invokeLater(runnable);
    }
  }

  private static boolean seemsToBeMoved(Change change, VirtualFile toSelect) {
    ContentRevision afterRevision = change.getAfterRevision();
    if (afterRevision == null) return false;
    FilePath file = afterRevision.getFile();
    return file.getName().equals(toSelect.getName());
  }

  protected abstract DefaultTreeModel buildTreeModel(final List<T> changes, final ChangeNodeDecorator changeNodeDecorator);

  @SuppressWarnings({"SuspiciousMethodCalls"})
  private void toggleSelection() {
    boolean hasExcluded = false;
    for (T value : getSelectedChanges()) {
      if (!myIncludedChanges.contains(value)) {
        hasExcluded = true;
      }
    }

    if (hasExcluded) {
      includeSelection();
    }
    else {
      excludeSelection();
    }

    repaint();
  }

  private void includeSelection() {
    for (T change : getSelectedChanges()) {
      myIncludedChanges.add(change);
    }
    notifyInclusionListener();
    repaint();
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  private void excludeSelection() {
    for (T change : getSelectedChanges()) {
      myIncludedChanges.remove(change);
    }
    notifyInclusionListener();
    repaint();
  }

  public List<T> getChanges() {
    if (myShowFlatten) {
      ListModel m = myList.getModel();
      int size = m.getSize();
      List result = new ArrayList(size);
      for (int i = 0; i < size; i++) {
        result.add(m.getElementAt(i));
      }
      return result;
    }
    else {
      final LinkedHashSet result = new LinkedHashSet();
      TreeUtil.traverseDepth((ChangesBrowserNode)myTree.getModel().getRoot(), new TreeUtil.Traverse() {
        public boolean accept(Object node) {
          ChangesBrowserNode changeNode = (ChangesBrowserNode)node;
          if (changeNode.isLeaf()) result.addAll(changeNode.getAllChangesUnder());
          return true;
        }
      });
      return new ArrayList<T>(result);
    }
  }

  public int getSelectionCount() {
    if (myShowFlatten) {
      return myList.getSelectedIndices().length;
    } else {
      return myTree.getSelectionCount();
    }
  }

  @NotNull
  public List<T> getSelectedChanges() {
    if (myShowFlatten) {
      final Object[] o = myList.getSelectedValues();
      final List<T> changes = new ArrayList<T>();
      for (Object anO : o) {
        changes.add((T)anO);
      }

      return changes;
    }
    else {
      final List<T> changes = new ArrayList<T>();
      final Set<Integer> checkSet = new HashSet<Integer>();
      final TreePath[] paths = myTree.getSelectionPaths();
      if (paths != null) {
        for (TreePath path : paths) {
          final ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
          final List<T> objects = getSelectedObjects(node);
          for (T object : objects) {
            final int hash = object.hashCode();
            if (! checkSet.contains(hash)) {
              changes.add(object);
              checkSet.add(hash);
            } else {
              if (! changes.contains(object)) {
                changes.add(object);
              }
            }
          }
        }
      }

      return changes;
    }
  }

  protected abstract List<T> getSelectedObjects(final ChangesBrowserNode<T> node);

  @Nullable
  protected abstract T getLeadSelectedObject(final ChangesBrowserNode node);

  @Nullable
  public T getHighestLeadSelection() {
    if (myShowFlatten) {
      final int index = myList.getLeadSelectionIndex();
      ListModel listModel = myList.getModel();
      if (index < 0 || index >= listModel.getSize()) return null;
      //noinspection unchecked
      return (T)listModel.getElementAt(index);
    }
    else {
      final TreePath path = myTree.getSelectionPath();
      if (path == null) return null;
      return getLeadSelectedObject((ChangesBrowserNode<T>)path.getLastPathComponent());
    }
  }

  @Nullable
  public T getLeadSelection() {
    if (myShowFlatten) {
      final int index = myList.getLeadSelectionIndex();
      ListModel listModel = myList.getModel();
      if (index < 0 || index >= listModel.getSize()) return null;
      //noinspection unchecked
      return (T)listModel.getElementAt(index);
    }
    else {
      final TreePath path = myTree.getSelectionPath();
      if (path == null) return null;
      final List<T> changes = getSelectedObjects(((ChangesBrowserNode<T>)path.getLastPathComponent()));
      return changes.size() > 0 ? changes.get(0) : null;
    }
  }

  private void notifyInclusionListener() {
    if (myInclusionListener != null) {
      myInclusionListener.run();
    }
  }

  // no listener supposed to be called
  public void setIncludedChanges(final Collection<T> changes) {
    myIncludedChanges.clear();
    myIncludedChanges.addAll(changes);
    myTree.repaint();
    myList.repaint();
  }

  public void includeChange(final T change) {
    myIncludedChanges.add(change);
    notifyInclusionListener();
    myTree.repaint();
    myList.repaint();
  }

  public void includeChanges(final Collection<T> changes) {
    myIncludedChanges.addAll(changes);
    notifyInclusionListener();
    myTree.repaint();
    myList.repaint();
  }

  public void excludeChange(final T change) {
    myIncludedChanges.remove(change);
    notifyInclusionListener();
    myTree.repaint();
    myList.repaint();
  }

  public void excludeChanges(final Collection<T> changes) {
    myIncludedChanges.removeAll(changes);
    notifyInclusionListener();
    myTree.repaint();
    myList.repaint();
  }

  public boolean isIncluded(final T change) {
    return myIncludedChanges.contains(change);
  }

  public Collection<T> getIncludedChanges() {
    return myIncludedChanges;
  }

  public void expandAll() {
    TreeUtil.expandAll(myTree);
  }

  public AnAction[] getTreeActions() {
    final ToggleShowDirectoriesAction directoriesAction = new ToggleShowDirectoriesAction();
    final ExpandAllAction expandAllAction = new ExpandAllAction(myTree) {
      public void update(AnActionEvent e) {
        e.getPresentation().setVisible(!myShowFlatten);
      }
    };
    final CollapseAllAction collapseAllAction = new CollapseAllAction(myTree) {
      public void update(AnActionEvent e) {
        e.getPresentation().setVisible(!myShowFlatten);
      }
    };
    final SelectAllAction selectAllAction = new SelectAllAction();
    final AnAction[] actions = new AnAction[]{directoriesAction, expandAllAction, collapseAllAction, selectAllAction};
    directoriesAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK)),
      this);
    expandAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_EXPAND_ALL)),
      myTree);
    collapseAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_COLLAPSE_ALL)),
      myTree);
    selectAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_A, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK)),
      this);
    return actions;
  }

  private class MyTreeCellRenderer extends JPanel implements TreeCellRenderer {
    private final ChangesBrowserNodeRenderer myTextRenderer;
    private final JCheckBox myCheckBox;


    public MyTreeCellRenderer() {
      super(new BorderLayout());
      myCheckBox = new JCheckBox();
      myTextRenderer = new ChangesBrowserNodeRenderer(myProject, false, myHighlightProblems);

      if (myShowCheckboxes) {
        add(myCheckBox, BorderLayout.WEST);
      }

      add(myTextRenderer, BorderLayout.CENTER);
    }

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {

      if (UIUtil.isUnderGTKLookAndFeel() || UIUtil.isUnderNimbusLookAndFeel()) {
        NonOpaquePanel.setTransparent(this);
        NonOpaquePanel.setTransparent(myCheckBox);
      } else {
        setBackground(null);
        myCheckBox.setBackground(null);
      }


      myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      if (myShowCheckboxes) {
        ChangesBrowserNode node = (ChangesBrowserNode)value;

        CheckboxTree.NodeState state = getNodeStatus(node);
        myCheckBox.setSelected(state != CheckboxTree.NodeState.CLEAR);
        myCheckBox.setEnabled(state != CheckboxTree.NodeState.PARTIAL);
        revalidate();

        return this;
      }
      else {
        return myTextRenderer;
      }
    }
  }


  private CheckboxTree.NodeState getNodeStatus(ChangesBrowserNode node) {
    boolean hasIncluded = false;
    boolean hasExcluded = false;

    for (T change : getSelectedObjects(node)) {
      if (myIncludedChanges.contains(change)) {
        hasIncluded = true;
      }
      else {
        hasExcluded = true;
      }
    }

    if (hasIncluded && hasExcluded) return CheckboxTree.NodeState.PARTIAL;
    if (hasIncluded) return CheckboxTree.NodeState.FULL;
    return CheckboxTree.NodeState.CLEAR;
  }

  private class MyListCellRenderer extends JPanel implements ListCellRenderer {
    private final ColoredListCellRenderer myTextRenderer;
    public final JCheckBox myCheckbox;

    public MyListCellRenderer() {
      super(new BorderLayout());
      myCheckbox = new JCheckBox();
      myTextRenderer = new VirtualFileListCellRenderer(myProject) {
        @Override
        protected void putParentPath(Object value, FilePath path, FilePath self) {
          super.putParentPath(value, path, self);
          final boolean applyChangeDecorator = (value instanceof Change) && myChangeDecorator != null;
          if (applyChangeDecorator) {
            myChangeDecorator.decorate((Change) value, this, isShowFlatten());
          }
        }

        @Override
        protected void putParentPathImpl(Object value, String parentPath, FilePath self) {
          final boolean applyChangeDecorator = (value instanceof Change) && myChangeDecorator != null;
          List<Pair<String,ChangeNodeDecorator.Stress>> parts = null;
          if (applyChangeDecorator) {
            parts = myChangeDecorator.stressPartsOfFileName((Change)value, parentPath);
          }
          if (parts == null) {
            super.putParentPathImpl(value, parentPath, self);
            return;
          }

          for (Pair<String, ChangeNodeDecorator.Stress> part : parts) {
            append(part.getFirst(), part.getSecond().derive(SimpleTextAttributes.GRAYED_ATTRIBUTES));
          }
        }
      };

      myCheckbox.setBackground(null);
      setBackground(null);

      if (myShowCheckboxes) {
        add(myCheckbox, BorderLayout.WEST);
      }
      add(myTextRenderer, BorderLayout.CENTER);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      myTextRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (myShowCheckboxes) {
        myCheckbox.setSelected(myIncludedChanges.contains(value));
        return this;
      }
      else {
        return myTextRenderer;
      }
    }
  }

  private class MyToggleSelectionAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
      toggleSelection();
    }
  }

  public class ToggleShowDirectoriesAction extends ToggleAction {
    public ToggleShowDirectoriesAction() {
      super(VcsBundle.message("changes.action.show.directories.text"),
            VcsBundle.message("changes.action.show.directories.description"),
            PlatformIcons.DIRECTORY_CLOSED_ICON);
    }

    public boolean isSelected(AnActionEvent e) {
      return (! myProject.isDisposed()) && !PropertiesComponent.getInstance(myProject).isTrueValue(FLATTEN_OPTION_KEY);
    }

    public void setSelected(AnActionEvent e, boolean state) {
      PropertiesComponent.getInstance(myProject).setValue(FLATTEN_OPTION_KEY, String.valueOf(!state));
      setShowFlatten(!state);
    }
  }

  private class SelectAllAction extends AnAction {
    private SelectAllAction() {
      super("Select All", "Select all items", IconLoader.getIcon("/actions/selectall.png"));
    }

    public void actionPerformed(final AnActionEvent e) {
      if (myShowFlatten) {
        final int count = myList.getModel().getSize();
        if (count > 0) {
          myList.setSelectionInterval(0, count-1);
        }
      }
      else {
        final int countTree = myTree.getRowCount();
        if (countTree > 0) {
          myTree.setSelectionInterval(0, countTree-1);
        }
      }
    }
  }

  public void select(final List<T> changes) {
    final DefaultTreeModel treeModel = (DefaultTreeModel) myTree.getModel();
    final TreeNode root = (TreeNode) treeModel.getRoot();
    final List<TreePath> treeSelection = new ArrayList<TreePath>(changes.size());
    TreeUtil.traverse(root, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        final T change = (T) ((DefaultMutableTreeNode) node).getUserObject();
        if (changes.contains(change)) {
          treeSelection.add(new TreePath(((DefaultMutableTreeNode) node).getPath()));
        }
        return true;
      }
    });
    myTree.setSelectionPaths(treeSelection.toArray(new TreePath[treeSelection.size()]));

    // list
    final ListModel model = myList.getModel();
    final int size = model.getSize();
    final List<Integer> listSelection = new ArrayList<Integer>(changes.size());
    for (int i = 0; i < size; i++) {
      final T el = (T) model.getElementAt(i);
      if (changes.contains(el)) {
        listSelection.add(i);
      }
    }
    myList.setSelectedIndices(int2int(listSelection));
  }

  private static int[] int2int(List<Integer> treeSelection) {
    final int[] toPass = new int[treeSelection.size()];
    int i = 0;
    for (Integer integer : treeSelection) {
      toPass[i] = integer;
      ++ i;
    }
    return toPass;
  }

  public void enableSelection(final boolean value) {
    myTree.setEnabled(value);
  }

  public void setAlwaysExpandList(boolean alwaysExpandList) {
    myAlwaysExpandList = alwaysExpandList;
  }
}
