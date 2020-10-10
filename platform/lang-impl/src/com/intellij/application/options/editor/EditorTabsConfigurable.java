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

package com.intellij.application.options.editor;

import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author yole
 */
public class EditorTabsConfigurable implements EditorOptionsProvider {
  private JPanel myRootPanel;
  private JCheckBox myHideKnownExtensions;
  private JCheckBox myScrollTabLayoutInEditorCheckBox;
  private JTextField myEditorTabLimitField;
  private JComboBox myEditorTabPlacement;
  private JRadioButton myCloseNonModifiedFilesFirstRadio;
  private JRadioButton myCloseLRUFilesRadio;
  private JRadioButton myActivateLeftEditorOnCloseRadio;
  private JRadioButton myActivateMRUEditorOnCloseRadio;
  private JCheckBox myCbModifiedTabsMarkedWithAsterisk;
  private JCheckBox myShowCloseButtonOnCheckBox;
  private JCheckBox myShowDirectoryInTabCheckBox;
  private JRadioButton myActivateRightNeighbouringTabRadioButton;
  private JCheckBox mySwitcherPolicy;

  public EditorTabsConfigurable() {
    myEditorTabPlacement.setModel(new DefaultComboBoxModel(new Object[]{
      SwingConstants.TOP,
      SwingConstants.LEFT,
      SwingConstants.BOTTOM,
      SwingConstants.RIGHT,
      UISettings.TABS_NONE,
    }));
    myEditorTabPlacement.setRenderer(new MyTabsPlacementComboBoxRenderer(myEditorTabPlacement.getRenderer()));
    myEditorTabPlacement.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        revalidateSingleRowCheckbox();
      }
    });

    revalidateSingleRowCheckbox();
  }

  private void revalidateSingleRowCheckbox() {
    final int i = ((Integer)myEditorTabPlacement.getSelectedItem()).intValue();

    if (i == UISettings.TABS_NONE) {
      myHideKnownExtensions.setEnabled(false);
      myScrollTabLayoutInEditorCheckBox.setEnabled(false);
      myCbModifiedTabsMarkedWithAsterisk.setEnabled(false);
      myShowCloseButtonOnCheckBox.setEnabled(false);
      myShowDirectoryInTabCheckBox.setEnabled(false);
    } else {
      myHideKnownExtensions.setEnabled(true);
      myScrollTabLayoutInEditorCheckBox.setEnabled(true);
      myCbModifiedTabsMarkedWithAsterisk.setEnabled(true);
      myShowCloseButtonOnCheckBox.setEnabled(true);
      myShowDirectoryInTabCheckBox.setEnabled(true);
    }

    if (SwingConstants.TOP == i) {
      myScrollTabLayoutInEditorCheckBox.setEnabled(true);
    } else {
      myScrollTabLayoutInEditorCheckBox.setSelected(true);
      myScrollTabLayoutInEditorCheckBox.setEnabled(false);
    }
  }

  @Nls
  public String getDisplayName() {
    return "Editor Tabs";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.editor.tabs";
  }

  public JComponent createComponent() {
    return myRootPanel;
  }

  public void reset() {
    UISettings uiSettings=UISettings.getInstance();

    myCbModifiedTabsMarkedWithAsterisk.setSelected(uiSettings.MARK_MODIFIED_TABS_WITH_ASTERISK);
    myScrollTabLayoutInEditorCheckBox.setSelected(uiSettings.SCROLL_TAB_LAYOUT_IN_EDITOR);
    myEditorTabPlacement.setSelectedItem(uiSettings.EDITOR_TAB_PLACEMENT);
    myHideKnownExtensions.setSelected(uiSettings.HIDE_KNOWN_EXTENSION_IN_TABS);
    myShowDirectoryInTabCheckBox.setSelected(uiSettings.SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES);
    myEditorTabLimitField.setText(Integer.toString(uiSettings.EDITOR_TAB_LIMIT));
    myShowCloseButtonOnCheckBox.setSelected(uiSettings.SHOW_CLOSE_BUTTON);
    mySwitcherPolicy.setSelected(uiSettings.HIDE_SWITCHER_ON_CONTROL_RELEASE);

    if (uiSettings.CLOSE_NON_MODIFIED_FILES_FIRST) {
      myCloseNonModifiedFilesFirstRadio.setSelected(true);
    }
    else {
      myCloseLRUFilesRadio.setSelected(true);
    }
    if (uiSettings.ACTIVATE_MRU_EDITOR_ON_CLOSE) {
      myActivateMRUEditorOnCloseRadio.setSelected(true);
    }
    else if (uiSettings.ACTIVATE_RIGHT_EDITOR_ON_CLOSE) {
      myActivateRightNeighbouringTabRadioButton.setSelected(true);
    }
    else {
      myActivateLeftEditorOnCloseRadio.setSelected(true);
    }
  }

  public void apply() throws ConfigurationException {
    UISettings uiSettings=UISettings.getInstance();

    boolean uiSettingsChanged = uiSettings.MARK_MODIFIED_TABS_WITH_ASTERISK != myCbModifiedTabsMarkedWithAsterisk.isSelected();
    uiSettings.MARK_MODIFIED_TABS_WITH_ASTERISK = myCbModifiedTabsMarkedWithAsterisk.isSelected();

    if (isModified(myScrollTabLayoutInEditorCheckBox, uiSettings.SCROLL_TAB_LAYOUT_IN_EDITOR)) uiSettingsChanged = true;
    uiSettings.SCROLL_TAB_LAYOUT_IN_EDITOR = myScrollTabLayoutInEditorCheckBox.isSelected();

    if (isModified(myShowCloseButtonOnCheckBox, uiSettings.SHOW_CLOSE_BUTTON)) uiSettingsChanged = true;
    uiSettings.SHOW_CLOSE_BUTTON = myShowCloseButtonOnCheckBox.isSelected();

    if (isModified(mySwitcherPolicy, uiSettings.HIDE_SWITCHER_ON_CONTROL_RELEASE)) uiSettingsChanged = true;
    uiSettings.HIDE_SWITCHER_ON_CONTROL_RELEASE = mySwitcherPolicy.isSelected();

    final int tabPlacement = ((Integer)myEditorTabPlacement.getSelectedItem()).intValue();
    if (uiSettings.EDITOR_TAB_PLACEMENT != tabPlacement) uiSettingsChanged = true;
    uiSettings.EDITOR_TAB_PLACEMENT = tabPlacement;

    boolean hide = myHideKnownExtensions.isSelected();
    if (uiSettings.HIDE_KNOWN_EXTENSION_IN_TABS != hide) uiSettingsChanged = true;
    uiSettings.HIDE_KNOWN_EXTENSION_IN_TABS = hide;

    boolean dir = myShowDirectoryInTabCheckBox.isSelected();
    if (uiSettings.SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES != hide) uiSettingsChanged = true;
    uiSettings.SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES = myShowDirectoryInTabCheckBox.isSelected();

    uiSettings.CLOSE_NON_MODIFIED_FILES_FIRST = myCloseNonModifiedFilesFirstRadio.isSelected();
    uiSettings.ACTIVATE_MRU_EDITOR_ON_CLOSE = myActivateMRUEditorOnCloseRadio.isSelected();
    uiSettings.ACTIVATE_RIGHT_EDITOR_ON_CLOSE = myActivateRightNeighbouringTabRadioButton.isSelected();

    String temp = myEditorTabLimitField.getText();
    if(temp.trim().length() > 0){
      try {
        int newEditorTabLimit = Integer.parseInt(temp);
        if(newEditorTabLimit>0&&newEditorTabLimit!=uiSettings.EDITOR_TAB_LIMIT){
          uiSettings.EDITOR_TAB_LIMIT=newEditorTabLimit;
          uiSettingsChanged = true;
        }
      }catch (NumberFormatException ignored){}
    }
    if(uiSettingsChanged){
      uiSettings.fireUISettingsChanged();
    }
  }

  public boolean isModified() {
    final UISettings uiSettings = UISettings.getInstance();
    boolean isModified = isModified(myCbModifiedTabsMarkedWithAsterisk, uiSettings.MARK_MODIFIED_TABS_WITH_ASTERISK);
    isModified |= isModified(myEditorTabLimitField, uiSettings.EDITOR_TAB_LIMIT);
    int tabPlacement = ((Integer)myEditorTabPlacement.getSelectedItem()).intValue();
    isModified |= tabPlacement != uiSettings.EDITOR_TAB_PLACEMENT;
    isModified |= myHideKnownExtensions.isSelected() != uiSettings.HIDE_KNOWN_EXTENSION_IN_TABS;
    isModified |= myShowDirectoryInTabCheckBox.isSelected() != uiSettings.SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES;

    isModified |= myScrollTabLayoutInEditorCheckBox.isSelected() != uiSettings.SCROLL_TAB_LAYOUT_IN_EDITOR;
    isModified |= myShowCloseButtonOnCheckBox.isSelected() != uiSettings.SHOW_CLOSE_BUTTON;
    isModified |= mySwitcherPolicy.isSelected() != uiSettings.HIDE_SWITCHER_ON_CONTROL_RELEASE;

    isModified |= isModified(myCloseNonModifiedFilesFirstRadio, uiSettings.CLOSE_NON_MODIFIED_FILES_FIRST);
    isModified |= isModified(myActivateMRUEditorOnCloseRadio, uiSettings.ACTIVATE_MRU_EDITOR_ON_CLOSE);
    isModified |= isModified(myActivateRightNeighbouringTabRadioButton, uiSettings.ACTIVATE_RIGHT_EDITOR_ON_CLOSE);

    return isModified;
  }

  public void disposeUIResources() {
  }


  private static boolean isModified(JToggleButton checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private static boolean isModified(JTextField textField, int value) {
    try {
      int fieldValue = Integer.parseInt(textField.getText().trim());
      return fieldValue != value;
    }
    catch(NumberFormatException e) {
      return false;
    }
  }

  private static final class MyTabsPlacementComboBoxRenderer extends ListCellRendererWrapper<Integer> {
    public MyTabsPlacementComboBoxRenderer(final ListCellRenderer listCellRenderer) {
      super(listCellRenderer);
    }

    @Override
    public void customize(JList list, Integer value, int index, boolean selected, boolean hasFocus) {
      int tabPlacement = value.intValue();
      String text;
      if (UISettings.TABS_NONE == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.none");
      }
      else if (SwingConstants.TOP == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.top");
      }
      else if (SwingConstants.LEFT == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.left");
      }
      else if (SwingConstants.BOTTOM == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.bottom");
      }
      else if (SwingConstants.RIGHT == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.right");
      }
      else {
        throw new IllegalArgumentException("unknown tabPlacement: " + tabPlacement);
      }
      setText(text);
    }
  }

  @NotNull
  public String getId() {
    return "editor.preferences.tabs";
  }

  public Runnable enableSearch(final String option) {
    return null;
  }
}
