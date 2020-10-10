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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MisorderedAssertEqualsParametersInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "misordered.assert.equals.parameters.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "misordered.assert.equals.parameters.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new FlipParametersFix();
  }

  private static class FlipParametersFix extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "misordered.assert.equals.parameters.flip.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = methodNameIdentifier.getParent();
      assert parent != null;
      final PsiMethodCallExpression callExpression =
        (PsiMethodCallExpression)parent.getParent();
      assert callExpression != null;
      final PsiReferenceExpression methodExpression =
        callExpression.getMethodExpression();
      final PsiMethod method = (PsiMethod)methodExpression.resolve();
      assert method != null;
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiManager psiManager = callExpression.getManager();
      final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      final PsiType stringType =
        PsiType.getJavaLangString(psiManager, scope);
      final PsiType parameterType1 = parameters[0].getType();
      final int expectedPosition;
      final int actualPosition;
      if (parameterType1.equals(stringType) && parameters.length > 2) {
        expectedPosition = 1;
        actualPosition = 2;
      }
      else {
        expectedPosition = 0;
        actualPosition = 1;
      }
      final PsiExpressionList argumentList =
        callExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression expectedArgument = arguments[expectedPosition];
      final PsiExpression actualArgument = arguments[actualPosition];
      final String actualArgumentText = actualArgument.getText();
      final String expectedArgumentText = expectedArgument.getText();
      replaceExpression(expectedArgument, actualArgumentText);
      replaceExpression(actualArgument, expectedArgumentText);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MisorderedAssertEqualsParametersVisitor();
  }

  private static class MisorderedAssertEqualsParametersVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isAssertEquals(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final PsiMethod method = (PsiMethod)methodExpression.resolve();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() == 0) {
        return;
      }
      final PsiManager psiManager = expression.getManager();
      final Project project = psiManager.getProject();
      final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      final PsiType stringType =
        PsiType.getJavaLangString(psiManager, scope);
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiType parameterType1 = parameters[0].getType();
      final int expectedPosition;
      final int actualPosition;
      if (parameterType1.equals(stringType) && parameters.length > 2) {
        expectedPosition = 1;
        actualPosition = 2;
      }
      else {
        expectedPosition = 0;
        actualPosition = 1;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (actualPosition >= arguments.length) {
        return;
      }
      final PsiExpression expectedArgument = arguments[expectedPosition];
      final PsiExpression actualArgument = arguments[actualPosition];
      if (expectedArgument == null || actualArgument == null) {
        return;
      }
      if (isLiteralOrConstant(expectedArgument)) {
        return;
      }
      if (!isLiteralOrConstant(actualArgument)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean isLiteralOrConstant(PsiExpression expression) {
      if (expression instanceof PsiLiteralExpression) {
        return true;
      }
      if (!(expression instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)expression;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField)) {
        return false;
      }
      final PsiField field = (PsiField)target;
      return field.hasModifierProperty(PsiModifier.STATIC) &&
             field.hasModifierProperty(PsiModifier.FINAL);
    }

    private static boolean isAssertEquals(
      PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if (!"assertEquals".equals(methodName)) {
        return false;
      }
      final PsiMethod method = (PsiMethod)methodExpression.resolve();
      if (method == null) {
        return false;
      }
      final PsiClass targetClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(targetClass,
                                         "junit.framework.Assert") ||
             InheritanceUtil.isInheritor(targetClass,
                                         "org.junit.Assert");
    }
  }
}