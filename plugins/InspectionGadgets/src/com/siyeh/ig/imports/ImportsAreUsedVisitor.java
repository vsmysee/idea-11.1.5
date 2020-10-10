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
package com.siyeh.ig.imports;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class ImportsAreUsedVisitor extends JavaRecursiveElementVisitor {

  private final List<PsiImportStatement> importStatements;

  ImportsAreUsedVisitor(PsiImportStatement[] importStatements) {
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
      // it's already fully qualified, so the import statement wasn't
      // responsible
      return;
    }
    // during typing there can be incomplete code
    final JavaResolveResult resolveResult = reference.advancedResolve(true);
    final PsiElement element = resolveResult.getElement();
    if (!(element instanceof PsiClass)) {
      return;
    }
    final PsiClass referencedClass = (PsiClass)element;
    final String qualifiedName = referencedClass.getQualifiedName();
    if (qualifiedName == null) {
      return;
    }
    final List<PsiImportStatement> importStatementsCopy =
      new ArrayList(importStatements);
    for (PsiImportStatement importStatement : importStatementsCopy) {
      final String importName = importStatement.getQualifiedName();
      if (importName == null) {
        return;
      }
      if (importStatement.isOnDemand()) {
        final int lastComponentIndex =
          qualifiedName.lastIndexOf((int)'.');
        if (lastComponentIndex > 0) {
          final String packageName = qualifiedName.substring(0,
                                                             lastComponentIndex);
          if (importName.equals(packageName)) {
            removeAll(importStatement);
            break;
          }
        }
      }
      else if (importName.equals(qualifiedName)) {
        removeAll(importStatement);
        break;
      }
    }
  }

  private void removeAll(@NotNull PsiImportStatement importStatement) {
    for (int i = importStatements.size() - 1; i >= 0; i--) {
      final PsiImportStatement statement = importStatements.get(i);
      final String statementText = statement.getText();
      final String importText = importStatement.getText();
      if (importText.equals(statementText)) {
        importStatements.remove(i);
      }
    }
  }

  public PsiImportStatement[] getUnusedImportStatements() {
    if (importStatements.isEmpty()) {
      return PsiImportStatement.EMPTY_ARRAY;
    }
    return importStatements.toArray(
      new PsiImportStatement[importStatements.size()]);
  }
}