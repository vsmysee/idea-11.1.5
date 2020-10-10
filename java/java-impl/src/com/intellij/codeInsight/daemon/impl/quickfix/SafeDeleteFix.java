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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class SafeDeleteFix implements IntentionAction {
  private final PsiElement myElement;

  public SafeDeleteFix(@NotNull PsiElement element) {
    myElement = element;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("safe.delete.text",
                                  HighlightMessageUtil.getSymbolName(myElement, PsiSubstitutor.EMPTY));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("safe.delete.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myElement.isValid() && myElement.getManager().isInProject(myElement);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(myElement.getContainingFile())) return;
    SafeDeleteHandler.invoke(project, new PsiElement[]{myElement}, false);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

}
