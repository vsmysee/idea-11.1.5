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

package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrInterfaceDefinition;

/**
 * @author ilyas
 */
public abstract class CreateClassActionBase implements IntentionAction {
  protected final GrReferenceElement myRefElement;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.annotator.intentions.CreateClassActionBase");

  public CreateClassActionBase(GrReferenceElement refElement) {
    myRefElement = refElement;
  }

  @NotNull
  public String getText() {
    String referenceName = myRefElement.getReferenceName();
    return shouldCreateInterface() ? GroovyBundle.message("create.interface.text", referenceName) : GroovyBundle.message("create.class.text", referenceName);
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("create.class.family.name");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myRefElement.isValid();
  }

  public boolean startInWriteAction() {
    return true;
  }

  protected boolean shouldCreateInterface() {
    PsiElement parent = myRefElement.getParent();
    return parent instanceof GrImplementsClause || parent instanceof GrExtendsClause && parent.getParent() instanceof GrInterfaceDefinition;
  }

  @Nullable
  public static PsiClass createClassByType(@NotNull  final PsiDirectory directory,
                                           @NotNull  final String name,
                                           @NotNull  final PsiManager manager,
                                           @Nullable final PsiElement contextElement,
                                           @NotNull  final String templateName) {
    AccessToken accessToken = WriteAction.start();

    try {
      PsiClass targetClass = null;
      try {
        PsiFile file = GroovyTemplatesFactory.createFromTemplate(directory, name, name + ".groovy", templateName);
        for (PsiElement element : file.getChildren()) {
          if (element instanceof PsiClass) {
            targetClass = ((PsiClass)element);
            break;
          }
        }
        if (targetClass == null) {
          throw new IncorrectOperationException(GroovyBundle.message("no.class.in.file.template"));
        }
      }
      catch (final IncorrectOperationException e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showErrorDialog(
              GroovyBundle.message("cannot.create.class.error.text", name, e.getLocalizedMessage()),
              GroovyBundle.message("cannot.create.class.error.title"));
          }
        });
        return null;
      }
      PsiModifierList modifiers = targetClass.getModifierList();
      if (contextElement != null &&
          !JavaPsiFacade.getInstance(manager.getProject()).getResolveHelper().isAccessible(targetClass, contextElement, null) &&
          modifiers != null) {
        modifiers.setModifierProperty(PsiModifier.PUBLIC, true);
      }
      return targetClass;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
    finally {
      accessToken.finish();
    }
  }

  @Nullable
  public static Editor putCursor(Project project, @NotNull PsiFile targetFile, PsiElement element) {
    int textOffset = element.getTextOffset();
    VirtualFile virtualFile = targetFile.getVirtualFile();
    if (virtualFile != null) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, textOffset);
      return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    } else {
      return null;
    }
  }
}
