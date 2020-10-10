/*
 * Copyright 2007-2008 Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.VariableSearchUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CaughtExceptionImmediatelyRethrownInspection
  extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "caught.exception.immediately.rethrown.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "caught.exception.immediately.rethrown.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiTryStatement tryStatement = (PsiTryStatement)infos[0];
    final boolean removeTryCatch =
      tryStatement.getCatchSections().length == 1 &&
      tryStatement.getFinallyBlock() == null;
    return new DeleteCatchSectionFix(removeTryCatch);
  }

  private static class DeleteCatchSectionFix extends InspectionGadgetsFix {

    private final boolean removeTryCatch;

    DeleteCatchSectionFix(boolean removeTryCatch) {
      this.removeTryCatch = removeTryCatch;
    }

    @NotNull
    public String getName() {
      if (removeTryCatch) {
        return InspectionGadgetsBundle.message(
          "remove.try.catch.quickfix");
      }
      else {
        return InspectionGadgetsBundle.message(
          "delete.catch.section.quickfix");
      }
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiParameter)) {
        return;
      }
      final PsiParameter parameter = (PsiParameter)parent;
      final PsiElement grandParent = parameter.getParent();
      if (!(grandParent instanceof PsiCatchSection)) {
        return;
      }
      final PsiCatchSection catchSection = (PsiCatchSection)grandParent;
      final PsiTryStatement tryStatement = catchSection.getTryStatement();
      final boolean removeTryCatch =
        tryStatement.getCatchSections().length == 1 &&
        tryStatement.getFinallyBlock() == null;
      if (removeTryCatch) {
        final PsiCodeBlock codeBlock = tryStatement.getTryBlock();
        if (codeBlock == null) {
          return;
        }
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 0) {
          tryStatement.delete();
          return;
        }
        final PsiElement containingElement = tryStatement.getParent();
        final boolean keepBlock;
        if (containingElement instanceof PsiCodeBlock) {
          final PsiCodeBlock parentBlock =
            (PsiCodeBlock)containingElement;
          keepBlock =
            VariableSearchUtils.containsConflictingDeclarations(
              codeBlock, parentBlock);
        }
        else {
          keepBlock = true;
        }
        if (keepBlock) {
          final JavaPsiFacade psiFacade =
            JavaPsiFacade.getInstance(project);
          final PsiElementFactory factory =
            psiFacade.getElementFactory();
          final PsiBlockStatement resultStatement = (PsiBlockStatement)
            factory.createStatementFromText("{}", element);
          final PsiCodeBlock resultBlock =
            resultStatement.getCodeBlock();
          for (PsiStatement statement : statements) {
            resultBlock.add(statement);
          }
          tryStatement.replace(resultStatement);
        }
        else {
          for (PsiStatement statement : statements) {
            containingElement.addBefore(statement, tryStatement);
          }
          tryStatement.delete();
        }
      }
      else {
        catchSection.delete();
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CaughtExceptionImmediatelyRethrownVisitor();
  }

  private static class CaughtExceptionImmediatelyRethrownVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiExpression expression = statement.getException();
      if (!(expression instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiStatement previousStatement =
        PsiTreeUtil.getPrevSiblingOfType(statement,
                                         PsiStatement.class);
      if (previousStatement != null) {
        return;
      }
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiStatement) {
        // e.g. if (notsure) throw e;
        return;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)expression;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiParameter)) {
        return;
      }
      final PsiParameter parameter = (PsiParameter)target;
      final PsiElement declarationScope = parameter.getDeclarationScope();
      if (!(declarationScope instanceof PsiCatchSection)) {
        return;
      }
      final PsiCatchSection catchSection =
        (PsiCatchSection)declarationScope;
      final PsiCodeBlock block =
        PsiTreeUtil.getParentOfType(statement, PsiCodeBlock.class);
      if (block == null) {
        return;
      }
      final PsiElement blockParent = block.getParent();
      if (blockParent != catchSection) {
        // e.g. if (notsure) { throw e; }
        return;
      }
      if (isSuperClassExceptionCaughtLater(parameter, catchSection)) {
        return;
      }
      final Query<PsiReference> query =
        ReferencesSearch.search(parameter);
      for (PsiReference reference : query) {
        final PsiElement element = reference.getElement();
        if (element != expression) {
          return;
        }
      }
      final PsiTryStatement tryStatement = catchSection.getTryStatement();
      registerVariableError(parameter, tryStatement);
    }

    private static boolean isSuperClassExceptionCaughtLater(
      PsiVariable parameter, PsiCatchSection catchSection) {
      final PsiTryStatement tryStatement = catchSection.getTryStatement();
      final PsiCatchSection[] catchSections =
        tryStatement.getCatchSections();
      int index = 0;
      while (catchSections[index] != catchSection &&
             index < catchSections.length) {
        index++;
      }
      final PsiType type = parameter.getType();
      if (!(type instanceof PsiClassType)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass parameterClass = classType.resolve();
      if (parameterClass == null) {
        return false;
      }
      for (int i = index; i < catchSections.length; i++) {
        final PsiCatchSection nextCatchSection = catchSections[i];
        final PsiParameter nextParameter =
          nextCatchSection.getParameter();
        if (nextParameter == null) {
          continue;
        }
        final PsiType nextType = nextParameter.getType();
        if (!(nextType instanceof PsiClassType)) {
          continue;
        }
        final PsiClassType nextClassType = (PsiClassType)nextType;
        final PsiClass aClass = nextClassType.resolve();
        if (aClass == null) {
          continue;
        }
        if (parameterClass.isInheritor(aClass, true)) {
          return true;
        }
      }
      return false;
    }
  }
}