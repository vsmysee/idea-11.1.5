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

package com.intellij.application.options.codeStyle;

import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class CodeStyleSchemesPanel{
  private JComboBox myCombo;

  private final CodeStyleSchemesModel myModel;
  private JPanel myPanel;
  private JBScrollPane myJBScrollPane;
  private JButton myManageButton;

  private boolean myIsReset = false;
  private NewCodeStyleSettingsPanel mySettingsPanel;
  private final Font myDefaultComboFont;
  private final Font myBoldComboFont;

  public CodeStyleSchemesPanel(CodeStyleSchemesModel model) {
    myModel = model;

    myDefaultComboFont = myCombo.getFont();
    myBoldComboFont = myDefaultComboFont.deriveFont(Font.BOLD);
    myCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (!myIsReset) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  onCombo();
                }
              });
        }
      }
    });
    myCombo.setRenderer(new ListCellRendererWrapper(myCombo.getRenderer()) {
      @Override
      public void customize(final JList list, final Object value, final int index, final boolean selected, final boolean hasFocus) {
        Font font = myDefaultComboFont;
        if (value instanceof CodeStyleScheme) {
          CodeStyleScheme scheme = (CodeStyleScheme)value;
          if (scheme.isDefault() || myModel.isProjectScheme(scheme)) {
            font = myBoldComboFont;
          }
        }
        setFont(font);
      }
    });
    
    myManageButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showManageSchemesDialog();
      }
    });
    
    myJBScrollPane.setBorder(null);
  }

  private void onCombo() {
    CodeStyleScheme selected = getSelectedScheme();
    if (selected != null) {
      if (myModel.isProjectScheme(selected)) {
        myModel.setUsePerProjectSettings(true);
      }
      else {
        myModel.setUsePerProjectSettings(false);
        myModel.selectScheme(selected, this);
      }
    }
  }

  @Nullable
  private CodeStyleScheme getSelectedScheme() {
    Object selected = myCombo.getSelectedItem();
    if (selected instanceof CodeStyleScheme) {
      return (CodeStyleScheme)selected;
    }
    return null;
  }

  public void disposeUIResources() {
    myPanel.removeAll();
  }

  public void resetSchemesCombo() {
    myIsReset = true;
    try {
      List<CodeStyleScheme> schemes = new ArrayList<CodeStyleScheme>();
      schemes.addAll(myModel.getAllSortedSchemes());
      DefaultComboBoxModel model = new DefaultComboBoxModel(schemes.toArray());
      myCombo.setModel(model);
      if (myModel.isUsePerProjectSettings()) {
        myCombo.setSelectedItem(myModel.getProjectScheme());
      }
      else {
        myCombo.setSelectedItem(myModel.getSelectedGlobalScheme());
      }
    }
    finally {
      myIsReset = false;
    }


  }


  public void onSelectedSchemeChanged() {
    myIsReset = true;
    try {
      if (myModel.isUsePerProjectSettings()) {
        myCombo.setSelectedItem(myModel.getProjectScheme());
      }
      else {
        myCombo.setSelectedItem(myModel.getSelectedGlobalScheme());
      }
    }
    finally {
      myIsReset = false;
    }
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public void setCodeStyleSettingsPanel(NewCodeStyleSettingsPanel settingsPanel) {
    mySettingsPanel = settingsPanel;
  }

  private void showManageSchemesDialog() {
    ManageCodeStyleSchemesDialog manageSchemesDialog = new ManageCodeStyleSchemesDialog(myPanel, myModel);
    manageSchemesDialog.show();
  }

  public void usePerProjectSettingsOptionChanged() {
    if (myModel.isProjectScheme(myModel.getSelectedScheme())) {
      myCombo.setSelectedItem(myModel.getProjectScheme());
    }
    else {
      myCombo.setSelectedItem(myModel.getSelectedScheme());
    }
  }
}
