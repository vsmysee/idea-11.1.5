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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ReturnStatementsVisitor;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class ConvertReturnStatementsVisitor implements ReturnStatementsVisitor {
  private final PsiElementFactory myFactory;
  private final PsiMethod myMethod;
  private final DeclarationSearcher mySearcher;
  private PsiReturnStatement myLatestReturn;
  private final String myDefaultValue;

  public ConvertReturnStatementsVisitor(final PsiElementFactory factory, final PsiMethod method, final PsiType targetType) {
    myFactory = factory;
    myMethod = method;
    mySearcher = new DeclarationSearcher(myMethod, targetType);
    myDefaultValue = PsiTypesUtil.getDefaultValueOfType(targetType);
  }

  @Override
  public void visit(final List<PsiReturnStatement> returnStatements) throws IncorrectOperationException {
    final PsiReturnStatement statement = ApplicationManager.getApplication().runWriteAction(new Computable<PsiReturnStatement>() {
      public PsiReturnStatement compute() {
        return replaceReturnStatements(returnStatements);
      }
    });
    if (statement != null) {
      myLatestReturn = statement;
    }
  }

  public PsiReturnStatement getLatestReturn() {
    return myLatestReturn;
  }

  private String generateValue(final PsiElement stopElement) {
    final PsiVariable variable = mySearcher.getDeclaration(stopElement);
    return variable != null ? variable.getName() : myDefaultValue;
  }

  public PsiReturnStatement createReturnInLastStatement() throws IncorrectOperationException {
    return ApplicationManager.getApplication().runWriteAction(new Computable<PsiReturnStatement>() {
      public PsiReturnStatement compute() {
        PsiCodeBlock body = myMethod.getBody();
        final String value = generateValue(body.getRBrace());
        PsiReturnStatement returnStatement = (PsiReturnStatement) myFactory.createStatementFromText("return " + value+";", myMethod);
        return (PsiReturnStatement) body.addBefore(returnStatement, body.getRBrace());
      }
    });
  }

  @Nullable
  public PsiReturnStatement replaceReturnStatements(final List<PsiReturnStatement> currentStatements) throws IncorrectOperationException {
    PsiReturnStatement latestReplaced = null;

    for (PsiReturnStatement returnStatement : currentStatements) {
      if (returnStatement.getReturnValue() != null) {
        continue;
      }
      final String value = generateValue(returnStatement);

      latestReplaced = (PsiReturnStatement) myFactory.createStatementFromText("return " + value+";", returnStatement.getParent());
      latestReplaced = (PsiReturnStatement)returnStatement.replace(latestReplaced);
    }

    return latestReplaced;
  }
}
