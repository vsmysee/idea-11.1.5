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
package com.intellij.refactoring.inline;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;

public class InlineMethodDialog extends InlineOptionsDialog {
  public static final String REFACTORING_NAME = RefactoringBundle.message("inline.method.title");
  private final PsiJavaCodeReferenceElement myReferenceElement;
  private final Editor myEditor;
  private final boolean myAllowInlineThisOnly;

  private final PsiMethod myMethod;

  private int myOccurrencesNumber = -1;

  public InlineMethodDialog(Project project, PsiMethod method, PsiJavaCodeReferenceElement ref, Editor editor,
                            final boolean allowInlineThisOnly) {
    super(project, true, method);
    myMethod = method;
    myReferenceElement = ref;
    myEditor = editor;
    myAllowInlineThisOnly = allowInlineThisOnly;
    myInvokedOnReference = ref != null;

    setTitle(REFACTORING_NAME);
    myOccurrencesNumber = initOccurrencesNumber(method);
    init();
  }

  protected String getNameLabelText() {
    String methodText = PsiFormatUtil.formatMethod(myMethod,
                                                   PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS, PsiFormatUtil.SHOW_TYPE);
    return RefactoringBundle.message("inline.method.method.label", methodText);
  }

  protected String getBorderTitle() {
    return RefactoringBundle.message("inline.method.border.title");
  }

  protected String getInlineThisText() {
    return RefactoringBundle.message("this.invocation.only.and.keep.the.method");
  }

  protected String getInlineAllText() {
    final String occurrencesString = myOccurrencesNumber > -1 ? " (" + myOccurrencesNumber + " occurrence" + (myOccurrencesNumber == 1 ? ")" : "s)") : "";
    return (myMethod.isWritable()
            ? RefactoringBundle.message("all.invocations.and.remove.the.method")
            : RefactoringBundle.message("all.invocations.in.project")) + occurrencesString;
  }

  protected void doAction() {
    invokeRefactoring(new InlineMethodProcessor(getProject(), myMethod, myReferenceElement, myEditor, isInlineThisOnly()));
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    if(myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_METHOD_THIS = isInlineThisOnly();
    }
  }

  protected void doHelpAction() {
    if (myMethod.isConstructor()) HelpManager.getInstance().invokeHelp(HelpID.INLINE_CONSTRUCTOR);
    else HelpManager.getInstance().invokeHelp(HelpID.INLINE_METHOD);
  }

  protected boolean canInlineThisOnly() {
    return InlineMethodHandler.checkRecursive(myMethod) || myAllowInlineThisOnly;
  }

  protected boolean isInlineThis() {
    return JavaRefactoringSettings.getInstance().INLINE_METHOD_THIS;
  }
}
