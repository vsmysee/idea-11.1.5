/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConcatenationUtils;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceConcatenationWithStringBufferIntention extends MutablyNamedIntention {

  @Override
  protected String getTextForElement(PsiElement element) {
    if (PsiUtil.isLanguageLevel5OrHigher(element)) {
      return IntentionPowerPackBundle.message(
        "replace.concatenation.with.string.builder.intention.name");
    }
    else {
      return IntentionPowerPackBundle.message(
        "replace.concatenation.with.string.buffer.intention.name");
    }
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new SimpleStringConcatenationPredicate(true);
  }

  @Override
  public void processIntention(@NotNull PsiElement element)
    throws IncorrectOperationException {
    PsiPolyadicExpression expression =
      (PsiPolyadicExpression)element;
    PsiElement parent = expression.getParent();
    if (parent == null) {
      return;
    }
    while (ConcatenationUtils.isConcatenation(parent)) {
      expression = (PsiPolyadicExpression)parent;
      parent = expression.getParent();
      if (parent == null) {
        return;
      }
    }
    @NonNls final StringBuilder newExpression = new StringBuilder();
    if (isPartOfStringBufferAppend(expression)) {
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)parent.getParent();
      assert methodCallExpression != null;
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();
      if (qualifierExpression != null) {
        final String qualifierText = qualifierExpression.getText();
        newExpression.append(qualifierText);
      }
      turnExpressionIntoChainedAppends(expression, newExpression);
      replaceExpression(newExpression.toString(), methodCallExpression);
    }
    else {
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) {
        newExpression.append("new StringBuffer()");
      }
      else {
        newExpression.append("new StringBuilder()");
      }
      turnExpressionIntoChainedAppends(expression, newExpression);
      newExpression.append(".toString()");
      replaceExpression(newExpression.toString(), expression);
    }
  }

  private static boolean isPartOfStringBufferAppend(
    PsiExpression expression) {
    PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    parent = parent.getParent();
    if (!(parent instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCall =
      (PsiMethodCallExpression)parent;
    final PsiReferenceExpression methodExpression =
      methodCall.getMethodExpression();
    final PsiType type = methodExpression.getType();
    if (type == null) {
      return false;
    }
    final String className = type.getCanonicalText();
    if (!CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(className) &&
        !CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(className)) {
      return false;
    }
    @NonNls final String methodName = methodExpression.getReferenceName();
    return "append".equals(methodName);
  }

  private static void turnExpressionIntoChainedAppends(
    PsiExpression expression, @NonNls StringBuilder result) {
    if (ConcatenationUtils.isConcatenation(expression)) {
      final PsiPolyadicExpression concatenation =
        (PsiPolyadicExpression)expression;
      for (PsiExpression op : concatenation.getOperands()) {
        turnExpressionIntoChainedAppends(op, result);
      }
    }
    else {
      final PsiExpression strippedExpression =
        ParenthesesUtils.stripParentheses(expression);
      result.append(".append(");
      if (strippedExpression != null) {
        result.append(strippedExpression.getText());
      }
      result.append(')');
    }
  }
}
