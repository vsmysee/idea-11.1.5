/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.ide.fileTemplates.ui;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.actions.AttributesDefaults;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.apache.velocity.runtime.parser.ParseException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Properties;

public class CreateFromTemplateDialog extends DialogWrapper {
  @NotNull private final PsiDirectory myDirectory;
  @NotNull private final Project myProject;
  private PsiElement myCreatedElement;
  private final CreateFromTemplatePanel myAttrPanel;
  private final JComponent myAttrComponent;
  @NotNull private final FileTemplate myTemplate;
  private final Properties myDefaultProperties;

  public CreateFromTemplateDialog(@NotNull Project project,
                                  @NotNull PsiDirectory directory,
                                  @NotNull FileTemplate template,
                                  @Nullable final AttributesDefaults attributesDefaults,
                                  @Nullable final Properties defaultProperties) {
    super(project, true);
    myDirectory = directory;
    myProject = project;
    myTemplate = template;
    setTitle(IdeBundle.message("title.new.from.template", template.getName()));

    myDefaultProperties = defaultProperties == null ? FileTemplateManager.getInstance().getDefaultProperties() : defaultProperties;
    FileTemplateUtil.fillDefaultProperties(myDefaultProperties, directory);
    boolean mustEnterName = FileTemplateUtil.findHandler(template).isNameRequired();
    if (attributesDefaults != null && attributesDefaults.isFixedName()) {
      myDefaultProperties.setProperty(FileTemplate.ATTRIBUTE_NAME, attributesDefaults.getDefaultFileName());
      mustEnterName = false;
    }

    String[] unsetAttributes = null;
    try {
      unsetAttributes = myTemplate.getUnsetAttributes(myDefaultProperties);
    }
    catch (ParseException e) {
      showErrorDialog(e);
    }

    if (unsetAttributes != null) {
      myAttrPanel = new CreateFromTemplatePanel(unsetAttributes, mustEnterName, attributesDefaults);
      myAttrComponent = myAttrPanel.getComponent();
      init();
    }
    else {
      myAttrPanel = null;
      myAttrComponent = null;
    }
  }

  public PsiElement create(){
    if (myAttrPanel != null) {
      if (myAttrPanel.hasSomethingToAsk()) {
        show();
        return myCreatedElement;
      }
      doCreate(null);
    }
    close(DialogWrapper.OK_EXIT_CODE);
    return myCreatedElement;
  }

  protected void doOKAction(){
    String fileName = myAttrPanel.getFileName();
    if (fileName != null && fileName.length() == 0) {
      Messages.showMessageDialog(myAttrComponent, IdeBundle.message("error.please.enter.a.file.name"), CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return;
    }
    doCreate(fileName);
    if ( myCreatedElement != null ) {
      super.doOKAction();
    }
  }

  private void doCreate(@Nullable final String fileName)  {
    try {
      myCreatedElement = FileTemplateUtil.createFromTemplate(myTemplate, fileName, myAttrPanel.getProperties(myDefaultProperties),
                                                             myDirectory);
    }
    catch (Exception e) {
      showErrorDialog(e);
    }
  }

  public Properties getEnteredProperties() {
    return myAttrPanel.getProperties(new Properties());
  }

  private void showErrorDialog(final Exception e) {
    Messages.showMessageDialog(myProject, filterMessage(e.getMessage()), getErrorMessage(), Messages.getErrorIcon());
  }

  private String getErrorMessage() {
    return FileTemplateUtil.findHandler(myTemplate).getErrorMessage();
  }

  @Nullable
  private String filterMessage(String message){
    if (message == null) return null;
    @NonNls String ioExceptionPrefix = "java.io.IOException:";
    if (message.startsWith(ioExceptionPrefix)){
      message = message.substring(ioExceptionPrefix.length());
    }
    return IdeBundle.message("error.unable.to.parse.template.message", myTemplate.getName(), message);
  }

  protected JComponent createCenterPanel(){
    myAttrPanel.ensureFitToScreen(200, 200);
    JPanel centerPanel = new JPanel(new GridBagLayout());
    centerPanel.add(myAttrComponent, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
    return centerPanel;
  }

  public JComponent getPreferredFocusedComponent(){
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myAttrComponent);
  }
}
