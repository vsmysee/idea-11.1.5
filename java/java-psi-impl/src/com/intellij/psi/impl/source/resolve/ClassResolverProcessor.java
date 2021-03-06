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
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.SmartList;

import java.util.Iterator;
import java.util.List;

public class ClassResolverProcessor extends BaseScopeProcessor implements NameHint, ElementClassHint {
  private static final String[] DEFAULT_PACKAGES = new String[]{CommonClassNames.DEFAULT_PACKAGE};
  private final String myClassName;
  private final PsiElement myPlace;
  private PsiClass myAccessClass = null;
  private List<ClassCandidateInfo> myCandidates = null;
  private boolean myHasAccessibleCandidate;
  private boolean myHasInaccessibleCandidate;
  private JavaResolveResult[] myResult = JavaResolveResult.EMPTY_ARRAY;
  private PsiElement myCurrentFileContext;

  public ClassResolverProcessor(String className, PsiElement place) {
    myClassName = className;
    final PsiFile file = place.getContainingFile();
    if (file instanceof JavaCodeFragment) {
      if (((JavaCodeFragment)file).getVisibilityChecker() != null) place = null;
    }
    myPlace = place;
    if (place instanceof PsiReferenceExpression) {
      final PsiReferenceExpression expression = (PsiReferenceExpression)place;
      final PsiExpression qualifierExpression = expression.getQualifierExpression();
      if (qualifierExpression != null) {
        final PsiType type = qualifierExpression.getType();
        if (type instanceof PsiClassType) {
          myAccessClass = ((PsiClassType)type).resolve();
        }
      }
    }
  }

  public JavaResolveResult[] getResult() {
    if (myResult != null) return myResult;
    if (myCandidates == null) return myResult = JavaResolveResult.EMPTY_ARRAY;
    if (myHasAccessibleCandidate && myHasInaccessibleCandidate) {
      for (Iterator<ClassCandidateInfo> iterator = myCandidates.iterator(); iterator.hasNext();) {
        CandidateInfo info = iterator.next();
        if (!info.isAccessible()) iterator.remove();
      }
      myHasInaccessibleCandidate = false;
    }

    myResult = myCandidates.toArray(new JavaResolveResult[myCandidates.size()]);
    return myResult;
  }

  @Override
  public String getName(ResolveState state) {
    return myClassName;
  }

  @Override
  public boolean shouldProcess(DeclarationKind kind) {
    return kind == DeclarationKind.CLASS;
  }

  private boolean myStaticContext = false;

  @Override
  public void handleEvent(PsiScopeProcessor.Event event, Object associated) {
    if (event == JavaScopeProcessorEvent.START_STATIC) {
      myStaticContext = true;
    }
    else if (event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT) {
      myCurrentFileContext = (PsiElement)associated;
    }
  }

  private static boolean isImported(PsiElement fileContext) {
    return fileContext instanceof PsiImportStatementBase;
  }

  private boolean isOnDemand(PsiElement fileContext, PsiClass psiClass) {
    if (isImported(fileContext)) {
      return ((PsiImportStatementBase)fileContext).isOnDemand();
    }

    String fqn = psiClass.getQualifiedName();
    if (fqn == null) return false;

    PsiFile file = myPlace == null ? null : FileContextUtil.getContextFile(myPlace);

    String[] defaultPackages = file instanceof PsiJavaFile ? ((PsiJavaFile)file).getImplicitlyImportedPackages() : DEFAULT_PACKAGES;
    String packageName = StringUtil.getPackageName(fqn);
    for (String defaultPackage : defaultPackages) {
      if (defaultPackage.equals(packageName)) return true;
    }

    // class from my package imported implicitly
    return file instanceof PsiJavaFile && ((PsiJavaFile)file).getPackageName().equals(packageName);
  }

  private Domination dominates(PsiClass aClass, boolean accessible, String fqName, ClassCandidateInfo info) {
    final PsiClass otherClass = info.getElement();
    assert otherClass != null;
    String otherQName = otherClass.getQualifiedName();
    if (fqName.equals(otherQName)) {
      return Domination.DOMINATED_BY;
    }

    final PsiClass containingClass1 = aClass.getContainingClass();
    final PsiClass containingClass2 = otherClass.getContainingClass();
    if (containingClass1 != null && containingClass2 != null && containingClass2.isInheritor(containingClass1, true) &&
        !isImported(myCurrentFileContext)) {
      // shadowing
      return Domination.DOMINATED_BY;
    }

    boolean infoAccessible = info.isAccessible();
    if (infoAccessible && !accessible) {
      return Domination.DOMINATED_BY;
    }
    if (!infoAccessible && accessible) {
      return Domination.DOMINATES;
    }

    // everything wins over class from default package
    boolean isDefault = StringUtil.getPackageName(fqName).length() == 0;
    boolean otherDefault = otherQName != null && StringUtil.getPackageName(otherQName).length() == 0;
    if (isDefault && !otherDefault) {
      return Domination.DOMINATED_BY;
    }
    if (!isDefault && otherDefault) {
      return Domination.DOMINATES;
    }

    // single import wins over on-demand
    boolean myOnDemand = isOnDemand(myCurrentFileContext, aClass);
    boolean otherOnDemand = isOnDemand(info.getCurrentFileResolveScope(), otherClass);
    if (myOnDemand && !otherOnDemand) {
      return Domination.DOMINATED_BY;
    }
    if (!myOnDemand && otherOnDemand) {
      return Domination.DOMINATES;
    }

    return Domination.EQUAL;
  }

  @Override
  public boolean execute(PsiElement element, ResolveState state) {
    if (!(element instanceof PsiClass)) return true;
    final PsiClass aClass = (PsiClass)element;
    final String name = aClass.getName();
    if (!myClassName.equals(name)) {
      return true;
    }
    boolean accessible = myPlace == null || checkAccessibility(aClass);
    if (myCandidates == null) {
      myCandidates = new SmartList<ClassCandidateInfo>();
    }
    else {
      String fqName = aClass.getQualifiedName();
      if (fqName != null) {
        for (int i = myCandidates.size()-1; i>=0; i--) {
          ClassCandidateInfo info = myCandidates.get(i);

          Domination domination = dominates(aClass, accessible, fqName, info);
          if (domination == Domination.DOMINATED_BY) {
            return true;
          }
          else if (domination == Domination.DOMINATES) {
            myCandidates.remove(i);
          }
        }
      }
    }

    myHasAccessibleCandidate |= accessible;
    myHasInaccessibleCandidate |= !accessible;
    myCandidates.add(new ClassCandidateInfo(aClass, state.get(PsiSubstitutor.KEY), !accessible, myCurrentFileContext));
    myResult = null;
    if (!accessible) return true;
    return myCurrentFileContext instanceof PsiImportStatementBase;
  }

  private boolean checkAccessibility(final PsiClass aClass) {
    //We don't care about accessibility in javadoc
    if (JavaResolveUtil.isInJavaDoc(myPlace)) {
      return true;
    }

    if (PsiImplUtil.isInServerPage(aClass.getContainingFile())) {
      PsiFile file = FileContextUtil.getContextFile(myPlace);
      if (PsiImplUtil.isInServerPage(file)) {
        return true;
      }
    }

    boolean accessible = true;
    if (aClass instanceof PsiTypeParameter) {
      accessible = !myStaticContext;
    }

    PsiManager manager = aClass.getManager();
    if (aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      PsiElement parent = aClass.getParent();
      while (true) {
        PsiElement parentScope = parent.getParent();
        if (parentScope instanceof PsiJavaFile) break;
        parent = parentScope;
        if (!(parentScope instanceof PsiClass)) break;
      }
      if (parent instanceof PsiDeclarationStatement) {
        parent = parent.getParent();
      }
      accessible = false;
      for (PsiElement placeParent = myPlace; placeParent != null; placeParent = placeParent.getContext()) {
        if (manager.areElementsEquivalent(placeParent, parent)) accessible = true;
      }
    }
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    if (aClass.hasModifierProperty(PsiModifier.PROTECTED)) {
      accessible = false;
      if (myPlace != null && facade.arePackagesTheSame(aClass, myPlace)) {
        accessible = true;
      }
      else {
        if (aClass.getContainingClass() != null) {
          accessible = myAccessClass == null || myPlace != null && facade.getResolveHelper().isAccessible(aClass, myPlace, myAccessClass);
        }
      }
    }
    if (aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      if (myPlace == null || !facade.arePackagesTheSame(aClass, myPlace)) {
        accessible = false;
      }
    }
    return accessible;
  }

  @Override
  public <T> T getHint(Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY || hintKey == NameHint.KEY) {
      //noinspection unchecked
      return (T)this;
    }
    return super.getHint(hintKey);
  }
}
