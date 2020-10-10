/*
 * Copyright 2006-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class AccessToStaticFieldLockedOnInstanceInspection
  extends BaseInspection {

  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "access.to.static.field.locked.on.instance.display.name");
  }

  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "access.to.static.field.locked.on.instance.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new AccessToStaticFieldLockedOnInstanceVisitor();
  }

  private static class AccessToStaticFieldLockedOnInstanceVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(
      @NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      boolean isLockedOnInstance = false;
      boolean isLockedOnClass = false;
      final PsiMethod containingMethod =
        PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      if (containingMethod != null &&
          containingMethod.hasModifierProperty(
            PsiModifier.SYNCHRONIZED)) {
        if (containingMethod.hasModifierProperty(
          PsiModifier.STATIC)) {
          isLockedOnClass = true;
        }
        else {
          isLockedOnInstance = true;
        }
      }
      final PsiClass expressionClass =
        PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      if (expressionClass == null) {
        return;
      }
      PsiElement elementToCheck = expression;
      while (true) {
        final PsiSynchronizedStatement synchronizedStatement =
          PsiTreeUtil.getParentOfType(elementToCheck,
                                      PsiSynchronizedStatement.class);
        if (synchronizedStatement == null ||
            !PsiTreeUtil.isAncestor(expressionClass,
                                    synchronizedStatement, true)) {
          break;
        }
        final PsiExpression lockExpression =
          synchronizedStatement.getLockExpression();
        if (lockExpression instanceof PsiReferenceExpression) {
          final PsiReferenceExpression reference =
            (PsiReferenceExpression)lockExpression;
          final PsiElement referent = reference.resolve();
          if (referent instanceof PsiField) {
            final PsiField referentField = (PsiField)referent;
            if (referentField.hasModifierProperty(
              PsiModifier.STATIC)) {
              isLockedOnClass = true;
            }
            else {
              isLockedOnInstance = true;
            }
          }
        }
        else if (lockExpression instanceof PsiThisExpression) {
          isLockedOnInstance = true;
        }
        else if (lockExpression instanceof
          PsiClassObjectAccessExpression) {
          isLockedOnClass = true;
        }
        elementToCheck = synchronizedStatement;
      }
      if (!isLockedOnInstance || isLockedOnClass) {
        return;
      }
      final PsiElement referent = expression.resolve();
      if (!(referent instanceof PsiField)) {
        return;
      }
      final PsiField referredField = (PsiField)referent;
      if (!referredField.hasModifierProperty(PsiModifier.STATIC) ||
          ExpressionUtils.isConstant(referredField)) {
        return;
      }
      final PsiClass containingClass = referredField.getContainingClass();
      if (!PsiTreeUtil.isAncestor(containingClass, expression, false)) {
        return;
      }
      registerError(expression);
    }
  }
}