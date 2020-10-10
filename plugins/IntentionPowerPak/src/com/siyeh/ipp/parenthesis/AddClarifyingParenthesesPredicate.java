/*
 * Copyright 2006-2011 Bas Leijdekkers
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
package com.siyeh.ipp.parenthesis;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

class AddClarifyingParenthesesPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    if (element instanceof PsiParenthesizedExpression) {
      return false;
    }
    if (element instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)element;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (parent instanceof PsiExpression) {
        final PsiExpression expression = (PsiExpression)parent;
        if (needsParentheses(expression, tokenType)) {
          return true;
        }
      }
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (needsParentheses(operand, tokenType)) {
          return true;
        }
      }
      return false;
    }
    if (parent instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)parent;
      final PsiExpression condition = conditionalExpression.getCondition();
      return element == condition;
    }
    return element instanceof PsiInstanceOfExpression && parent instanceof PsiPolyadicExpression;
  }

  private static boolean needsParentheses(PsiExpression expression,
                                          IElementType tokenType) {
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression)expression;
      final IElementType expressionTokenType = binaryExpression.getOperationTokenType();
      if (!tokenType.equals(expressionTokenType)) {
        return true;
      }
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      return true;
    }
    return false;
  }
}