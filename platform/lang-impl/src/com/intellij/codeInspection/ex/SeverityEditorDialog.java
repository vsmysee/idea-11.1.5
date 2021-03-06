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

package com.intellij.codeInspection.ex;

import com.intellij.application.options.colors.ColorAndFontDescriptionPanel;
import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.InspectionColorSettingsPage;
import com.intellij.application.options.colors.TextAttributesDescription;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ide.DataManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.newEditor.OptionsEditor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

import static com.intellij.codeInsight.daemon.impl.SeverityRegistrar.SeverityBasedTextAttributes;

/**
 * User: anna
 * Date: 24-Feb-2006
 */
public class SeverityEditorDialog extends DialogWrapper {
  private final JPanel myPanel;

  private final JList myOptionsList = new JBList();
  private final ColorAndFontDescriptionPanel myOptionsPanel = new ColorAndFontDescriptionPanel();

  private SeverityBasedTextAttributes myCurrentSelection;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.SeverityEditorDialog");
  private final SeverityRegistrar mySeverityRegistrar;
  private final CardLayout myCard;
  private final JPanel myRightPanel;
  @NonNls private static final String DEFAULT = "DEFAULT";
  @NonNls private static final String EDITABLE = "EDITABLE";

  public SeverityEditorDialog(final JComponent parent, final HighlightSeverity severity, final SeverityRegistrar severityRegistrar) {
    super(parent, true);
    mySeverityRegistrar = severityRegistrar;
    myOptionsList.setCellRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof SeverityBasedTextAttributes) {
          setText(((SeverityBasedTextAttributes)value).getSeverity().toString());
        }
        return rendererComponent;
      }
    });
    myOptionsList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (myCurrentSelection != null) {
          apply(myCurrentSelection);
        }
        myCurrentSelection = (SeverityBasedTextAttributes)myOptionsList.getSelectedValue();
        if (myCurrentSelection != null) {
          reset(myCurrentSelection);
          myCard.show(myRightPanel, mySeverityRegistrar.isDefaultSeverity(myCurrentSelection.getSeverity()) ? DEFAULT : EDITABLE);
        }
      }
    });
    myOptionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    JPanel leftPanel = ToolbarDecorator.createDecorator(myOptionsList)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final String name = Messages.showInputDialog(myPanel, InspectionsBundle.message("highlight.severity.create.dialog.name.label"),
                                                       InspectionsBundle.message("highlight.severity.create.dialog.title"),
                                                       Messages.getQuestionIcon(),
                                                       "", new InputValidator() {
            public boolean checkInput(final String inputString) {
              final ListModel listModel = myOptionsList.getModel();
              for (int i = 0; i < listModel.getSize(); i++) {
                final String severityName = ((SeverityBasedTextAttributes)listModel.getElementAt(i)).getSeverity().myName;
                if (Comparing.strEqual(severityName, inputString)) return false;
              }
              return true;
            }

            public boolean canClose(final String inputString) {
              return checkInput(inputString);
            }
          });
          if (name == null) return;
          final TextAttributes textAttributes = CodeInsightColors.WARNINGS_ATTRIBUTES.getDefaultAttributes();
          HighlightInfoType.HighlightInfoTypeImpl info = new HighlightInfoType.HighlightInfoTypeImpl(new HighlightSeverity(name, 50),
                                                                                                     TextAttributesKey
                                                                                                       .createTextAttributesKey(name));

          SeverityBasedTextAttributes newSeverityBasedTextAttributes = new SeverityBasedTextAttributes(textAttributes.clone(), info);
          ((DefaultListModel)myOptionsList.getModel()).addElement(newSeverityBasedTextAttributes);

          myOptionsList.clearSelection();
          ListScrollingUtil.selectItem(myOptionsList, newSeverityBasedTextAttributes);
        }
      }).setUpAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          apply(myCurrentSelection);
          ListUtil.moveSelectedItemsUp(myOptionsList);
        }
      }).setDownAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          apply(myCurrentSelection);
          ListUtil.moveSelectedItemsDown(myOptionsList);
        }
      }).createPanel();
    ToolbarDecorator.findRemoveButton(leftPanel).addCustomUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        return !mySeverityRegistrar
          .isDefaultSeverity(((SeverityBasedTextAttributes)myOptionsList.getSelectedValue()).getSeverity());
      }
    });
    ToolbarDecorator.findUpButton(leftPanel).addCustomUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        boolean canMove = ListUtil.canMoveSelectedItemsUp(myOptionsList);
        if (canMove) {
          SeverityBasedTextAttributes pair =
            (SeverityBasedTextAttributes)myOptionsList.getSelectedValue();
          if (pair != null && mySeverityRegistrar.isDefaultSeverity(pair.getSeverity())) {
            final int newPosition = myOptionsList.getSelectedIndex() - 1;
            pair = (SeverityBasedTextAttributes)myOptionsList.getModel().getElementAt(newPosition);
            if (mySeverityRegistrar.isDefaultSeverity(pair.getSeverity())) {
              canMove = false;
            }
          }
        }

        return canMove;
      }
    });
    ToolbarDecorator.findDownButton(leftPanel).addCustomUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        boolean canMove = ListUtil.canMoveSelectedItemsDown(myOptionsList);
        if (canMove) {
          SeverityBasedTextAttributes pair =
            (SeverityBasedTextAttributes)myOptionsList.getSelectedValue();
          if (pair != null && mySeverityRegistrar.isDefaultSeverity(pair.getSeverity())) {
            final int newPosition = myOptionsList.getSelectedIndex() + 1;
            pair = (SeverityBasedTextAttributes)myOptionsList.getModel().getElementAt(newPosition);
            if (mySeverityRegistrar.isDefaultSeverity(pair.getSeverity())) {
              canMove = false;
            }
          }
        }

        return canMove;
      }
    });

    myPanel = new JPanel(new BorderLayout());
    myPanel.add(leftPanel, BorderLayout.CENTER);
    myCard = new CardLayout();
    myRightPanel = new JPanel(myCard);
    final JPanel disabled = new JPanel(new GridBagLayout());
    final JButton button = new JButton(InspectionsBundle.message("severities.default.settings.message"));
    button.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        editColorsAndFonts();
      }
    });
    disabled.add(button,
                 new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0,
                                        0));
    myRightPanel.add(DEFAULT, disabled);
    myRightPanel.add(EDITABLE, myOptionsPanel);
    myCard.show(myRightPanel, EDITABLE);
    myPanel.add(myRightPanel, BorderLayout.EAST);
    fillList(severity);
    init();
    setTitle(InspectionsBundle.message("severities.editor.dialog.title"));
    reset((SeverityBasedTextAttributes)myOptionsList.getSelectedValue());
  }

  private void editColorsAndFonts() {
    final String toConfigure = getSelectedType().getSeverity(null).myName;
    doOKAction();
    myOptionsList.clearSelection();
    final DataContext dataContext = DataManager.getInstance().getDataContext(myPanel);
    final OptionsEditor optionsEditor = OptionsEditor.KEY.getData(dataContext);
    if (optionsEditor != null) {
      final ColorAndFontOptions colorAndFontOptions = optionsEditor.findConfigurable(ColorAndFontOptions.class);
      assert colorAndFontOptions != null;
      final SearchableConfigurable javaPage = colorAndFontOptions.findSubConfigurable(InspectionColorSettingsPage.class);
      LOG.assertTrue(javaPage != null);
      optionsEditor.select(javaPage).doWhenDone(new Runnable() {
        public void run() {
          final Runnable runnable = javaPage.enableSearch(toConfigure);
          if (runnable != null) {
            SwingUtilities.invokeLater(runnable);
          }
        }
      });
    }
    else {
      ColorAndFontOptions colorAndFontOptions = new ColorAndFontOptions();
      final Configurable[] configurables = colorAndFontOptions.buildConfigurables();
      try {
        final SearchableConfigurable javaPage = colorAndFontOptions.findSubConfigurable(InspectionColorSettingsPage.class);
        LOG.assertTrue(javaPage != null);
        ShowSettingsUtil.getInstance().editConfigurable(PlatformDataKeys.PROJECT.getData(dataContext), javaPage);
      }
      finally {
        for (Configurable configurable : configurables) {
          configurable.disposeUIResources();
        }
        colorAndFontOptions.disposeUIResources();
      }
    }
  }

  private void fillList(final HighlightSeverity severity) {
    DefaultListModel model = new DefaultListModel();
    model.removeAllElements();
    final List<SeverityRegistrar.SeverityBasedTextAttributes> infoTypes = new ArrayList<SeverityRegistrar.SeverityBasedTextAttributes>();
    infoTypes.addAll(mySeverityRegistrar.getRegisteredHighlightingInfoTypes());
    Collections.sort(infoTypes, new Comparator<SeverityRegistrar.SeverityBasedTextAttributes>() {
      public int compare(SeverityBasedTextAttributes attributes1,
                         SeverityBasedTextAttributes attributes2) {
        return -mySeverityRegistrar.compare(attributes1.getSeverity(), attributes2.getSeverity());
      }
    });
    SeverityBasedTextAttributes preselection = null;
    for (SeverityBasedTextAttributes type : infoTypes) {
      model.addElement(type);
      if (type.getSeverity().equals(severity)) {
        preselection = type;
      }
    }
    myOptionsList.setModel(model);
    myOptionsList.setSelectedValue(preselection, true);
  }


  private void apply(SeverityBasedTextAttributes info) {
    final MyTextAttributesDescription description =
      new MyTextAttributesDescription(info.getType().toString(), null, new TextAttributes(), info.getType().getAttributesKey());
    myOptionsPanel.apply(description, null);
    @NonNls Element textAttributes = new Element("temp");
    try {
      description.getTextAttributes().writeExternal(textAttributes);
      info.getAttributes().readExternal(textAttributes);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private void reset(SeverityBasedTextAttributes info) {
    final MyTextAttributesDescription description =
      new MyTextAttributesDescription(info.getType().toString(), null, info.getAttributes(), info.getType().getAttributesKey());
    @NonNls Element textAttributes = new Element("temp");
    try {
      info.getAttributes().writeExternal(textAttributes);
      description.getTextAttributes().readExternal(textAttributes);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    myOptionsPanel.reset(description);
  }

  protected void doOKAction() {
    apply((SeverityBasedTextAttributes)myOptionsList.getSelectedValue());
    final Collection<SeverityRegistrar.SeverityBasedTextAttributes> infoTypes =
      new HashSet<SeverityRegistrar.SeverityBasedTextAttributes>(mySeverityRegistrar.getRegisteredHighlightingInfoTypes());
    final ListModel listModel = myOptionsList.getModel();
    final List<String> order = new ArrayList<String>();
    for (int i = listModel.getSize() - 1; i >= 0; i--) {
      final SeverityBasedTextAttributes info =
        (SeverityBasedTextAttributes)listModel.getElementAt(i);
      order.add(info.getSeverity().myName);
      if (!mySeverityRegistrar.isDefaultSeverity(info.getSeverity())) {
        infoTypes.remove(info);
        final Color stripeColor = info.getAttributes().getErrorStripeColor();
        mySeverityRegistrar.registerSeverity(info, stripeColor != null ? stripeColor : LightColors.YELLOW);
      }
    }
    for (SeverityBasedTextAttributes info : infoTypes) {
      mySeverityRegistrar.unregisterSeverity(info.getSeverity());
    }
    mySeverityRegistrar.setOrder(order);
    super.doOKAction();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  public HighlightInfoType getSelectedType() {
    final SeverityBasedTextAttributes selection =
      (SeverityBasedTextAttributes)myOptionsList.getSelectedValue();
    return selection != null ? selection.getType() : null;
  }

  private static class MyTextAttributesDescription extends TextAttributesDescription {
    public MyTextAttributesDescription(final String name,
                                       final String group,
                                       final TextAttributes attributes,
                                       final TextAttributesKey type) {
      super(name, group, attributes, type, null, null, null);
    }

    public void apply(EditorColorsScheme scheme) {

    }

    public boolean isErrorStripeEnabled() {
      return true;
    }


    public TextAttributes getTextAttributes() {
      return super.getTextAttributes();
    }
  }
}
