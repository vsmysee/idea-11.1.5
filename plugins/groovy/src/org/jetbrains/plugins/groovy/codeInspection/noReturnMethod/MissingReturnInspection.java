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
package org.jetbrains.plugins.groovy.codeInspection.noReturnMethod;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.MaybeReturnInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ThrowingInstruction;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;

/**
 * @author ven
 */
public class MissingReturnInspection extends GroovySuppressableInspectionTool {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GroovyInspectionBundle.message("groovy.dfa.issues");
  }

  @NotNull
  @Override
  public String[] getGroupPath() {
    return new String[]{"Groovy", getGroupDisplayName()};
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return GroovyInspectionBundle.message("no.return.display.name");
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder problemsHolder, boolean onTheFly) {
    return new GroovyPsiElementVisitor(new GroovyElementVisitor() {
      public void visitClosure(GrClosableBlock closure) {
        super.visitClosure(closure);

        final PsiType expectedClosureType = GroovyExpectedTypesProvider.getExpectedClosureReturnType(closure);
        check(closure, problemsHolder, expectedClosureType != null && expectedClosureType != PsiType.VOID);
      }

      public void visitMethod(GrMethod method) {
        super.visitMethod(method);

        final GrOpenBlock block = method.getBlock();
        if (block != null) {
          final boolean mustReturnValue = method.getReturnTypeElementGroovy() != null && method.getReturnType() != PsiType.VOID;
          check(block, problemsHolder, mustReturnValue);
        }
      }
    });

  }

  private static void check(GrCodeBlock block, ProblemsHolder holder, boolean mustReturnValue) {
    if (methodMissesSomeReturns(block, mustReturnValue)) {
      addNoReturnMessage(block, holder);
    }
  }

  public static boolean methodMissesSomeReturns(GrControlFlowOwner block, boolean mustReturnValue) {
    if (!mustReturnValue) {
      return false;
    }

    final Ref<Boolean> always = new Ref<Boolean>(true);
    final Ref<Boolean> hasExplicitReturn = new Ref<Boolean>(false);
    final Ref<Boolean> sometimes = new Ref<Boolean>(false);
    ControlFlowUtils.visitAllExitPoints(block, new ControlFlowUtils.ExitPointVisitor() {
      @Override
      public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
        if (instruction instanceof MaybeReturnInstruction) {
          if (((MaybeReturnInstruction)instruction).mayReturnValue()) {
            sometimes.set(true);
          }
          else {
            always.set(false);
          }
          return true;
        }
        final PsiElement element = instruction.getElement();
        if (element instanceof GrReturnStatement) {
          sometimes.set(true);
          if (returnValue != null) {
            hasExplicitReturn.set(true);
          }
        }
        else if (instruction instanceof ThrowingInstruction) {
          sometimes.set(true);
        }
        else if (element instanceof GrAssertStatement) {
          sometimes.set(true);
          int count = 0;
          for (Instruction _i : instruction.allSuccessors()) {
            count++;
          }
          if (count <= 1) {
            always.set(false);
          }
        }
        else {
          always.set(false);
        }
        return true;
      }
    });

    if (!sometimes.get()) {
      return true;
    }

    return sometimes.get() && !always.get();
  }

  private static void addNoReturnMessage(GrCodeBlock block, ProblemsHolder holder) {
    final PsiElement lastChild = block.getLastChild();
    if (lastChild == null) return;
    TextRange range = lastChild.getTextRange();
    if (!lastChild.isValid() || !lastChild.isPhysical() || range.getStartOffset() >= range.getEndOffset()) {
      return;
    }
    holder.registerProblem(lastChild, GroovyInspectionBundle.message("no.return.message"));
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "GroovyMissingReturnStatement";
  }

  public boolean isEnabledByDefault() {
    return true;
  }
}
