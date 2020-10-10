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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.*;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class MixinMemberContributor extends NonCodeMembersContributor {
  @Override
  public void processDynamicElements(@NotNull final PsiType qualifierType,
                                     PsiScopeProcessor processor,
                                     GroovyPsiElement place,
                                     ResolveState state) {
    if (!(qualifierType instanceof PsiClassType)) return;
    if (isInAnnotation(place)) return;
    final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)qualifierType).resolveGenerics();
    final PsiClass aClass = resolveResult.getElement();
    if (aClass == null) return;

    final PsiModifierList modifierList = aClass.getModifierList();
    if (modifierList == null) return;

    List<PsiClass> mixins = new ArrayList<PsiClass>();
    for (PsiAnnotation annotation : getAllMixins(modifierList)) {
      final PsiAnnotationMemberValue value = annotation.findAttributeValue("value");

      if (value instanceof GrAnnotationArrayInitializer) {
        final GrAnnotationMemberValue[] initializers = ((GrAnnotationArrayInitializer)value).getInitializers();
        for (GrAnnotationMemberValue initializer : initializers) {
          addMixin(initializer, mixins);
        }
      }
      else if (value instanceof GrExpression) {
        addMixin((GrExpression)value, mixins);
      }
    }

    for (PsiClass mixin : mixins) {
      if (!mixin.processDeclarations(new DelegatingScopeProcessor(processor) {
        @Override
        public boolean execute(PsiElement element, ResolveState state) {
          if (isCategoryMethod(element, qualifierType)) {
            return super.execute(GrGdkMethodImpl.createGdkMethod((PsiMethod)element, false), state);
          }
          else {
            return super.execute(element, state);
          }
        }
      }, state, null, place)) {
        return;
      }
    }
  }

  private static List<PsiAnnotation> getAllMixins(PsiModifierList modifierList) {
    final ArrayList<PsiAnnotation> result = new ArrayList<PsiAnnotation>();
    for (PsiAnnotation annotation : modifierList.getApplicableAnnotations()) {
      if (GroovyCommonClassNames.GROOVY_LANG_MIXIN.equals(annotation.getQualifiedName())) {
        result.add(annotation);
      }
    }
    return result;
  }

  private static boolean isCategoryMethod(PsiElement element, PsiType qualifierType) {
    if (!(element instanceof PsiMethod)) return false;
    if (!((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC)) return false;

    final PsiParameter[] parameters = ((PsiMethod)element).getParameterList().getParameters();
    if (parameters.length == 0) return false;

    final PsiParameter selfParam = parameters[0];
    final PsiType selfType = selfParam.getType();

    return TypesUtil.isAssignable(selfType, qualifierType, element.getManager(), element.getResolveScope());
  }

  private static boolean isInAnnotation(GroovyPsiElement place) {
    return place.getParent() instanceof GrAnnotation || place.getParent() instanceof GrAnnotationArrayInitializer;
  }

  private static void addMixin(GrAnnotationMemberValue value, List<PsiClass> mixins) {
    if (value instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression)value).resolve();
      if (resolved instanceof PsiClass) {
        mixins.add((PsiClass)resolved);
      }
    }
  }
}
