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
package com.intellij.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class RawCommandLineEditor extends JPanel {
  private final TextFieldWithBrowseButton myTextField;
  private String myDialogCaption = "";

  public RawCommandLineEditor() {
    super(new BorderLayout());
    myTextField = new TextFieldWithBrowseButton(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Messages.showTextAreaDialog(myTextField.getTextField(), myDialogCaption, "EditParametersPopupWindow");
      }
    });
    myTextField.setButtonIcon(IconLoader.getIcon("/actions/showViewer.png"));
    add(myTextField, BorderLayout.CENTER);
    setDescriptor(null);
  }

  public void setDescriptor(FileChooserDescriptor descriptor) {
    InsertPathAction.addTo(myTextField.getTextField(), descriptor);
  }

  public String getDialogCaption() {
    return myDialogCaption;
  }

  public void setDialogCaption(String dialogCaption) {
    myDialogCaption = dialogCaption != null ? dialogCaption : "";
  }

  public void setText(String text) {
    myTextField.setText(text);
  }

  public String getText() {
    return myTextField.getText();
  }

  public JTextField getTextField() {
    return myTextField.getTextField();
  }

  public Document getDocument() {
    return myTextField.getTextField().getDocument();
  }

  public void attachLabel(JLabel label) {
    label.setLabelFor(myTextField.getTextField());
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myTextField.setEnabled(enabled);
  }
}
