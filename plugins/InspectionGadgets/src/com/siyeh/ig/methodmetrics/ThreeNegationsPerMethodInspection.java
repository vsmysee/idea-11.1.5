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
package com.siyeh.ig.methodmetrics;

import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ThreeNegationsPerMethodInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreInEquals = true;

  @NotNull
  public String getID() {
    return "MethodWithMoreThanThreeNegations";
  }

  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "three.negations.per.method.display.name");
  }

  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message(
        "three.negations.per.method.ignore.option"),
      this, "m_ignoreInEquals");
  }

  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer negationCount = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "three.negations.per.method.problem.descriptor", negationCount);
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ThreeNegationsPerMethodVisitor();
  }

  private class ThreeNegationsPerMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      final NegationCountVisitor visitor = new NegationCountVisitor();
      method.accept(visitor);
      final int negationCount = visitor.getCount();
      if (negationCount <= 3) {
        return;
      }
      if (m_ignoreInEquals && MethodUtils.isEquals(method)) {
        return;
      }
      registerMethodError(method, Integer.valueOf(negationCount));
    }
  }
}