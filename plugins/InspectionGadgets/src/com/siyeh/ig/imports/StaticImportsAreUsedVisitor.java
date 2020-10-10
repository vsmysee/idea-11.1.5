/*
 * Copyright 2005-2008 Bas Leijdekkers
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
package com.siyeh.ig.imports;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class StaticImportsAreUsedVisitor extends JavaRecursiveElementVisitor {

  private final List<PsiImportStaticStatement> importStatements;

  StaticImportsAreUsedVisitor(PsiImportStaticStatement[] importStatements) {
    this.importStatements = new ArrayList(Arrays.asList(importStatements));
    Collections.reverse(this.importStatements);
  }

  @Override
  public void visitElement(PsiElement element) {
    if (importStatements.isEmpty()) {
      return;
    }
    super.visitElement(element);
  }

  @Override
  public void visitReferenceElement(
    @NotNull PsiJavaCodeReferenceElement reference) {
    followReferenceToImport(reference);
    super.visitReferenceElement(reference);
  }

  private void followReferenceToImport(
    PsiJavaCodeReferenceElement reference) {
    if (reference.getQualifier() != null) {
      //it's already qualified, so the import statement wasn't
      // responsible
      return;
    }
    final String referenceName = reference.getReferenceName();
    if (referenceName == null) {
      return;
    }
    final PsiElement element = reference.resolve();
    if (!(element instanceof PsiMember)) {
      return;
    }
    final PsiMember member = (PsiMember)element;
    final PsiClass containingClass = member.getContainingClass();
    if (containingClass == null) {
      return;
    }
    for (PsiImportStaticStatement importStatement : importStatements) {
      if (importStatement.isOnDemand()) {
        final PsiClass targetClass =
          importStatement.resolveTargetClass();
        if (InheritanceUtil.isInheritorOrSelf(targetClass,
                                              containingClass, true)) {
          removeAll(importStatement);
          break;
        }
      }
      else {
        final String importReferenceName =
          importStatement.getReferenceName();
        if (importReferenceName == null) {
          continue;
        }
        if (importReferenceName.equals(referenceName)) {
          removeAll(importStatement);
          break;
        }
      }
    }
  }

  private void removeAll(
    @NotNull PsiImportStaticStatement importStaticStatement) {
    for (int i = importStatements.size() - 1; i >= 0; i--) {
      final PsiImportStaticStatement statement = importStatements.get(i);
      final String text = statement.getText();
      if (importStaticStatement.getText().equals(text)) {
        importStatements.remove(i);
      }
    }
  }

  public PsiImportStaticStatement[] getUnusedImportStaticStatements() {
    if (importStatements.isEmpty()) {
      return PsiImportStaticStatement.EMPTY_ARRAY;
    }
    else {
      return importStatements.toArray(
        new PsiImportStaticStatement[importStatements.size()]);
    }
  }
}
