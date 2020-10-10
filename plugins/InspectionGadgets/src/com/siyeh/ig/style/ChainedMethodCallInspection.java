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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ChainedMethodCallInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_ignoreFieldInitializations = true;

  @SuppressWarnings("PublicField")
  public boolean m_ignoreThisSuperCalls = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "chained.method.call.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "chained.method.call.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel =
      new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message(
      "chained.method.call.ignore.option"),
                      "m_ignoreFieldInitializations");
    panel.addCheckbox(InspectionGadgetsBundle.message(
      "chained.method.call.ignore.this.super.option"),
                      "m_ignoreThisSuperCalls");
    return panel;
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ChainedMethodCallVisitor();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ChainedMethodCallFix();
  }

  private static class ChainedMethodCallFix extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "introduce.variable.quickfix");
    }

    @Override
    public void doFix(final Project project, ProblemDescriptor descriptor) {
      final JavaRefactoringActionHandlerFactory factory =
        JavaRefactoringActionHandlerFactory.getInstance();
      final RefactoringActionHandler introduceHandler =
        factory.createIntroduceVariableHandler();
      final PsiElement methodNameElement = descriptor.getPsiElement();
      final PsiReferenceExpression methodCallExpression =
        (PsiReferenceExpression)methodNameElement.getParent();
      assert methodCallExpression != null;
      final PsiExpression qualifier =
        methodCallExpression.getQualifierExpression();
      final DataManager dataManager = DataManager.getInstance();
      final DataContext dataContext = dataManager.getDataContext();
      final Runnable runnable = new Runnable() {
        public void run() {
          introduceHandler.invoke(project,
                                  new PsiElement[]{qualifier}, dataContext);
        }
      };
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        runnable.run();
      }
      else {
        ApplicationManager.getApplication().invokeLater(runnable,
                                                        project.getDisposed());
      }
    }
  }

  private class ChainedMethodCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression reference =
        expression.getMethodExpression();
      final PsiExpression qualifier = reference.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (!isCallExpression(qualifier)) {
        return;
      }
      if (m_ignoreFieldInitializations) {
        final PsiElement field =
          PsiTreeUtil.getParentOfType(expression, PsiField.class);
        if (field != null) {
          return;
        }
      }
      if (m_ignoreThisSuperCalls) {
        final PsiExpressionList expressionList =
          PsiTreeUtil.getParentOfType(expression,
                                      PsiExpressionList.class);
        if (expressionList != null) {
          final PsiElement parent = expressionList.getParent();
          if (ExpressionUtils.isConstructorInvocation(parent)) {
            return;
          }
        }
      }
      registerMethodCallError(expression);
    }

    private boolean isCallExpression(PsiExpression expression) {
      expression = ParenthesesUtils.stripParentheses(expression);
      return expression instanceof PsiMethodCallExpression ||
             expression instanceof PsiNewExpression;
    }
  }
}