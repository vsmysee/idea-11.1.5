/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.MembersGetter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
* @author peter
*/
class GroovyMembersGetter extends MembersGetter {
  private final PsiClassType myExpectedType;
  private final GroovyPsiElement myContext;
  private final CompletionParameters myParameters;

  GroovyMembersGetter(PsiClassType expectedType, PsiElement context, CompletionParameters parameters) {
    myParameters = parameters;
    myExpectedType = JavaCompletionUtil.originalize(expectedType);
    myContext = (GroovyPsiElement)context;
  }

  public void processMembers(boolean searchInheritors, final Consumer<LookupElement> results) {
    processMembers(myContext, results, myExpectedType.resolve(), PsiTreeUtil.getParentOfType(myContext, GrAnnotation.class) != null, searchInheritors,
                   GroovyCompletionContributor.completeStaticMembers(myParameters));
  }

  @Override
  protected LookupElement createFieldElement(PsiField field) {
    if (!TypesUtil.isAssignable(myExpectedType, field.getType(), myContext)) {
      return null;
    }

    return GroovyCompletionContributor.createGlobalMemberElement(field, field.getContainingClass(), false);
  }

  @Override
  protected LookupElement createMethodElement(PsiMethod method) {
    PsiType type = method.getReturnType();
    if (type == null || !TypesUtil.isAssignable(myExpectedType, type, myContext)) {
      return null;
    }

    return GroovyCompletionContributor.createGlobalMemberElement(method, method.getContainingClass(), false);
  }
}
