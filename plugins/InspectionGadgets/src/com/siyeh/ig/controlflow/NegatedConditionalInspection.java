/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NegatedConditionalInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreNegatedNullComparison = true;

  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "negated.conditional.display.name");
  }

  @NotNull
  public String getID() {
    return "ConditionalExpressionWithNegatedCondition";
  }

  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "negated.conditional.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NegatedConditionalVisitor();
  }

  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "negated.conditional.ignore.option"), this,
                                          "m_ignoreNegatedNullComparison");
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new NegatedConditionalFix();
  }

  private static class NegatedConditionalFix extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "negated.conditional.invert.quickfix");
    }

    public void doFix(Project project,
                      ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiConditionalExpression exp =
        (PsiConditionalExpression)element.getParent();
      assert exp != null;
      final PsiExpression elseBranch = exp.getElseExpression();
      final PsiExpression thenBranch = exp.getThenExpression();
      final PsiExpression condition = exp.getCondition();
      final String negatedCondition =
        BoolUtils.getNegatedExpressionText(condition);
      assert elseBranch != null;
      assert thenBranch != null;
      final String newStatement =
        negatedCondition + '?' + elseBranch.getText() + ':' +
        thenBranch.getText();
      replaceExpression(exp, newStatement);
    }
  }

  private class NegatedConditionalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(
      PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression thenBranch = expression.getThenExpression();
      if (thenBranch == null) {
        return;
      }
      final PsiExpression elseBranch = expression.getElseExpression();
      if (elseBranch == null) {
        return;
      }
      final PsiExpression condition = expression.getCondition();
      if (!ExpressionUtils.isNegation(condition,
                                      m_ignoreNegatedNullComparison)) {
        return;
      }
      registerError(condition);
    }
  }
}