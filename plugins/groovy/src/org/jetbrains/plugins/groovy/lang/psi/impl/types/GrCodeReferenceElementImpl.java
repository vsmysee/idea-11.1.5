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

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.impl.types.GrCodeReferenceElementImpl.ReferenceKind.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrCodeReferenceElementImpl extends GrReferenceElementImpl<GrCodeReferenceElement> implements GrCodeReferenceElement {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.psi.impl.types.GrCodeReferenceElementImpl");

  public GrCodeReferenceElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected GrCodeReferenceElement bindWithQualifiedRef(@NotNull String qName) {
    final GrTypeArgumentList list = getTypeArgumentList();
    final String typeArgs = (list != null) ? list.getText() : "";
    final String text = qName + typeArgs;
    final GrCodeReferenceElement qualifiedRef = GroovyPsiElementFactory.getInstance(getProject()).createTypeOrPackageReference(text);
    getNode().getTreeParent().replaceChild(getNode(), qualifiedRef.getNode());
    return qualifiedRef;
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitCodeReferenceElement(this);
  }

  public String toString() {
    return "Reference element";
  }

  public GrCodeReferenceElement getQualifier() {
    return (GrCodeReferenceElement) findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  enum ReferenceKind {
    CLASS,
    CLASS_OR_PACKAGE,
    PACKAGE_FQ,
    CLASS_FQ,
    CLASS_OR_PACKAGE_FQ,
    STATIC_MEMBER_FQ,
    CLASS_IN_QUALIFIED_NEW
  }

  @Nullable
  public PsiElement resolve() {
    ResolveResult[] results = ResolveCache.getInstance(getProject()).resolveWithCaching(this, RESOLVER, true, false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  private ReferenceKind getKind(boolean forCompletion) {
    if (isClassReferenceForNew()) {
      return CLASS_OR_PACKAGE;
    }

    PsiElement parent = getParent();
    if (parent instanceof GrCodeReferenceElementImpl) {
      ReferenceKind parentKind = ((GrCodeReferenceElementImpl) parent).getKind(forCompletion);
      if (parentKind == CLASS) return CLASS_OR_PACKAGE;
      else if (parentKind == STATIC_MEMBER_FQ) return CLASS;
      else if (parentKind == CLASS_FQ) return CLASS_OR_PACKAGE_FQ;
      return parentKind;
    } else if (parent instanceof GrPackageDefinition) {
      return PACKAGE_FQ;
    } else if (parent instanceof GrDocReferenceElement) {
      return CLASS_OR_PACKAGE;
    } else if (parent instanceof GrImportStatement) {
      final GrImportStatement importStatement = (GrImportStatement) parent;
      if (importStatement.isStatic()) {
        return importStatement.isOnDemand() ? CLASS : STATIC_MEMBER_FQ;
      }
      else {
        return forCompletion || importStatement.isOnDemand() ? CLASS_OR_PACKAGE_FQ : CLASS_FQ;
      }
    }
    else if (parent instanceof GrNewExpression || parent instanceof GrAnonymousClassDefinition) {
      if (parent instanceof GrAnonymousClassDefinition) {
        parent = parent.getParent();
      }
      assert parent instanceof GrNewExpression;
      final GrNewExpression newExpression = (GrNewExpression)parent;
      if (newExpression.getQualifier() != null) return CLASS_IN_QUALIFIED_NEW;
    }

    return CLASS;
  }

  @NotNull
  public String getCanonicalText() {
    final ReferenceKind kind = getKind(false);
    switch (kind) {
      case CLASS:
      case CLASS_IN_QUALIFIED_NEW:
      case CLASS_OR_PACKAGE:
        final PsiElement target = resolve();
        if (target instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)target;
          String name = aClass.getQualifiedName();
          if (name == null) { //parameter types don't have qualified name
            name = aClass.getName();
          }
          final PsiType[] types = getTypeArguments();
          if (types.length == 0) return name;

          final StringBuilder buf = new StringBuilder();
          buf.append(name);
          buf.append('<');
          for (int i = 0; i < types.length; i++) {
            if (i > 0) buf.append(',');
            buf.append(types[i].getCanonicalText());
          }
          buf.append('>');

          return buf.toString();
        }
        else if (target instanceof PsiPackage) {
          return ((PsiPackage)target).getQualifiedName();
        }
        else {
          LOG.assertTrue(target == null);
          return getTextSkipWhiteSpaceAndComments();
        }

      case CLASS_FQ:
      case CLASS_OR_PACKAGE_FQ:
      case PACKAGE_FQ:
      case STATIC_MEMBER_FQ:
        return getTextSkipWhiteSpaceAndComments();
      default:
        LOG.assertTrue(false);
        return null;
    }
  }

  protected boolean bindsCorrectly(PsiElement element) {
    if (super.bindsCorrectly(element)) return true;
    if (element instanceof PsiClass) {
      final PsiElement resolved = resolve();
      if (resolved instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod) resolved;
        if (method.isConstructor() && getManager().areElementsEquivalent(element, method.getContainingClass())) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public boolean isFullyQualified() {
    switch (getKind(false)) {
      case PACKAGE_FQ:
      case CLASS_FQ:
      case CLASS_OR_PACKAGE_FQ:
      case STATIC_MEMBER_FQ:
      case CLASS_OR_PACKAGE:
        if (resolve() instanceof PsiPackage) return true;
    }
    final GrCodeReferenceElement qualifier = getQualifier();
    return qualifier != null && ((GrCodeReferenceElementImpl)qualifier).isFullyQualified();
  }

  public boolean isReferenceTo(PsiElement element) {
    final PsiManager manager = getManager();
    if (element instanceof PsiNamedElement && getParent() instanceof GrImportStatement) {
      final GroovyResolveResult[] results = multiResolve(false);
      for (GroovyResolveResult result : results) {
        if (manager.areElementsEquivalent(result.getElement(), element)) return true;
      }
    }
    return manager.areElementsEquivalent(element, resolve());
  }

  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private boolean isClassReferenceForNew() {
    PsiElement parent = getParent();
    while (parent instanceof GrCodeReferenceElement) parent = parent.getParent();
    return parent instanceof GrNewExpression;
  }

  private void processVariantsImpl(ReferenceKind kind, Consumer<Object> consumer) {
    switch (kind) {
      case STATIC_MEMBER_FQ: {
        final GrCodeReferenceElement qualifier = getQualifier();
        if (qualifier != null) {
          final PsiElement resolve = qualifier.resolve();
          if (resolve instanceof PsiClass) {
            final PsiClass clazz = (PsiClass) resolve;

            for (PsiField field : clazz.getFields()) {
              if (field.hasModifierProperty(PsiModifier.STATIC)) {
                consumer.consume(field);
              }
            }

            for (PsiMethod method : clazz.getMethods()) {
              if (method.hasModifierProperty(PsiModifier.STATIC)) {
                consumer.consume(method);
              }
            }

            for (PsiClass inner : clazz.getInnerClasses()) {
              if (inner.hasModifierProperty(PsiModifier.STATIC)) {
                consumer.consume(inner);
              }
            }
            return;
          }
        }
      }
      //fallthrough

      case PACKAGE_FQ:
      case CLASS_FQ:
      case CLASS_OR_PACKAGE_FQ: {
        final String refText = PsiUtil.getQualifiedReferenceText(this);
        LOG.assertTrue(refText != null, this.getText());

        final int lastDot = refText.lastIndexOf(".");
        String parentPackageFQName = lastDot > 0 ? refText.substring(0, lastDot) : "";
        final PsiPackage parentPackage = JavaPsiFacade.getInstance(getProject()).findPackage(parentPackageFQName);
        if (parentPackage != null) {
          final GlobalSearchScope scope = getResolveScope();
          if (kind == PACKAGE_FQ) {
            for (PsiPackage aPackage : parentPackage.getSubPackages(scope)) {
              consumer.consume(aPackage);
            }
            return;
          } else {
            if (kind == CLASS_FQ) {
              for (PsiClass aClass : parentPackage.getClasses(scope)) {
                consumer.consume(aClass);
              }
              return;
            } else {
              final PsiPackage[] subpackages = parentPackage.getSubPackages(scope);
              final PsiClass[] classes = parentPackage.getClasses(scope);
              for (PsiPackage aPackage : subpackages) {
                consumer.consume(aPackage);
              }
              for (PsiClass aClass : classes) {
                consumer.consume(aClass);
              }
              return;
            }
          }
        }
      }

      case CLASS_OR_PACKAGE:
      case CLASS_IN_QUALIFIED_NEW:
      case CLASS: {
        GrCodeReferenceElement qualifier = getQualifier();
        if (qualifier != null) {
          PsiElement qualifierResolved = qualifier.resolve();
          if (qualifierResolved instanceof PsiPackage) {
            PsiPackage aPackage = (PsiPackage) qualifierResolved;
            PsiClass[] classes = aPackage.getClasses(getResolveScope());

            for (PsiClass aClass : classes) {
              consumer.consume(aClass);
            }
            if (kind == CLASS) return;

            PsiPackage[] subpackages = aPackage.getSubPackages(getResolveScope());
            for (PsiPackage subpackage : subpackages) {
              consumer.consume(subpackage);
            }
          } else if (qualifierResolved instanceof PsiClass) {
            for (PsiClass aClass : ((PsiClass)qualifierResolved).getInnerClasses()) {
              consumer.consume(aClass);
            }
          }
        } else {
          ResolverProcessor classProcessor = CompletionProcessor.createClassCompletionProcessor(this);
          ResolveUtil.treeWalkUp(this, classProcessor, false);

          for (Object o : GroovyCompletionUtil.getCompletionVariants(classProcessor.getCandidates())) {
            consumer.consume(o);
          }
        }
      }
    }
  }

  public boolean isSoft() {
    return false;
  }

  private static class OurResolver implements ResolveCache.PolyVariantResolver<GrCodeReferenceElementImpl> {

    @Nullable
    public GroovyResolveResult[] resolve(GrCodeReferenceElementImpl reference, boolean incompleteCode) {
      if (reference.getReferenceName() == null) return GroovyResolveResult.EMPTY_ARRAY;
      final GroovyResolveResult[] results = _resolve(reference, reference.getManager(), reference.getKind(false));
      if (results == null) return results;
      List<GroovyResolveResult> imported = new ArrayList<GroovyResolveResult>();
      final PsiType[] args = reference.getTypeArguments();
      for (int i = 0; i < results.length; i++) {
        GroovyResolveResult result = results[i];
        final PsiElement element = result.getElement();
        if (element instanceof PsiClass) {
          final PsiSubstitutor substitutor = result.getSubstitutor();
          final PsiSubstitutor newSubstitutor = substitutor.putAll((PsiClass) element, args);
          GroovyPsiElement context = result.getCurrentFileResolveContext();
          GroovyResolveResultImpl newResult = new GroovyResolveResultImpl(element, context, newSubstitutor, result.isAccessible(), result.isStaticsOK());
          results[i] = newResult;
          if (context instanceof GrImportStatement) {
            imported.add(newResult);
          }
        }
      }
      if (!imported.isEmpty()) {
        return imported.toArray(new GroovyResolveResult[imported.size()]);
      }

      return results;
    }

    private static GroovyResolveResult[] _resolve(GrCodeReferenceElementImpl ref, PsiManager manager, ReferenceKind kind) {
      final String refName = ref.getReferenceName();
      if (refName == null) {
        return GroovyResolveResult.EMPTY_ARRAY;
      }

      switch (kind) {
        case CLASS_OR_PACKAGE_FQ:
        case CLASS_FQ:
        case PACKAGE_FQ:
          String qName = PsiUtil.getQualifiedReferenceText(ref);
          LOG.assertTrue(qName != null, ref.getText());

          JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
          if (kind == CLASS_OR_PACKAGE_FQ || kind == CLASS_FQ) {
            final PsiFile file = ref.getContainingFile();
            if (qName.indexOf('.') > 0 || file instanceof GroovyFile && ((GroovyFile)file).getPackageName().length() == 0) {
              PsiClass aClass = facade.findClass(qName, ref.getResolveScope());
              if (aClass != null) {
                boolean isAccessible = PsiUtil.isAccessible(ref, aClass);
                return new GroovyResolveResult[]{new GroovyResolveResultImpl(aClass, isAccessible)};
              }
            }
          }

          if (kind == CLASS_OR_PACKAGE_FQ || kind == PACKAGE_FQ) {
            PsiPackage aPackage = facade.findPackage(qName);
            if (aPackage != null) {
              return new GroovyResolveResult[]{new GroovyResolveResultImpl(aPackage, true)};
            }
          }

          break;

        case CLASS:
        case CLASS_OR_PACKAGE: {
          GrCodeReferenceElement qualifier = ref.getQualifier();
          if (qualifier != null) {
            PsiElement qualifierResolved = qualifier.resolve();
            if (qualifierResolved instanceof PsiPackage) {
              for (final PsiClass aClass : ((PsiPackage) qualifierResolved).getClasses(ref.getResolveScope())) {
                if (refName.equals(aClass.getName())) {
                  boolean isAccessible = PsiUtil.isAccessible(ref, aClass);
                  return new GroovyResolveResult[]{new GroovyResolveResultImpl(aClass, isAccessible)};
                }
              }

              if (kind == CLASS_OR_PACKAGE) {
                final String fqName = ((PsiPackage)qualifierResolved).getQualifiedName() + "." + refName;
                final PsiPackage aPackage = JavaPsiFacade.getInstance(ref.getProject()).findPackage(fqName);
                if (aPackage != null) return new GroovyResolveResult[]{new GroovyResolveResultImpl(aPackage, true)};
              }
            } else if ((kind == CLASS || kind == CLASS_OR_PACKAGE) && qualifierResolved instanceof PsiClass) {
              PsiClass[] classes = ((PsiClass) qualifierResolved).getAllInnerClasses();
              for (final PsiClass aClass : classes) {
                if (refName.equals(aClass.getName())) {
                  boolean isAccessible = PsiUtil.isAccessible(ref, aClass);
                  return new GroovyResolveResult[]{new GroovyResolveResultImpl(aClass, isAccessible)};
                }
              }
            }
          } else {
            EnumSet<ClassHint.ResolveKind> kinds = kind == CLASS ? ResolverProcessor.RESOLVE_KINDS_CLASS :
                                                   ResolverProcessor.RESOLVE_KINDS_CLASS_PACKAGE;
            ResolverProcessor processor = new ClassResolverProcessor(refName, ref, kinds);
            ResolveUtil.treeWalkUp(ref, processor, false);
            GroovyResolveResult[] candidates = processor.getCandidates();
            if (candidates.length > 0) return candidates;

            if (kind == CLASS_OR_PACKAGE) {
              PsiPackage defaultPackage = JavaPsiFacade.getInstance(ref.getProject()).findPackage("");
              if (defaultPackage != null) {
                for (final PsiPackage subpackage : defaultPackage.getSubPackages(ref.getResolveScope())) {
                  if (refName.equals(subpackage.getName()))
                    return new GroovyResolveResult[]{new GroovyResolveResultImpl(subpackage, true)};
                }
              }
            }
          }

          break;
        }

        case STATIC_MEMBER_FQ: {
          final GrCodeReferenceElement qualifier = ref.getQualifier();
          if (qualifier != null) {
            final PsiElement resolve = qualifier.resolve();
            if (resolve instanceof PsiClass) {
              final PsiClass clazz = (PsiClass) resolve;
              PsiResolveHelper helper = JavaPsiFacade.getInstance(clazz.getProject()).getResolveHelper();
              List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();

              final PsiField field = clazz.findFieldByName(refName, false);
              if (field != null && field.hasModifierProperty(PsiModifier.STATIC)) {
                result.add(new GroovyResolveResultImpl(field, helper.isAccessible(field, ref, null)));
              }

              final PsiMethod[] methods = clazz.findMethodsByName(refName, false);
              for (PsiMethod method : methods) {
                result.add(new GroovyResolveResultImpl(method, helper.isAccessible(method, ref, null)));
              }

              final PsiClass innerClass = clazz.findInnerClassByName(refName, false);
              if (innerClass != null && innerClass.hasModifierProperty(PsiModifier.STATIC)) {
                result.add(new GroovyResolveResultImpl(innerClass, helper.isAccessible(innerClass, ref, null)));
              }


              return result.toArray(new GroovyResolveResult[result.size()]);
            }
          }
          break;
        }
        case CLASS_IN_QUALIFIED_NEW: {
          if (ref.getParent() instanceof GrCodeReferenceElement) return GroovyResolveResult.EMPTY_ARRAY;
          final GrNewExpression newExpression = PsiTreeUtil.getParentOfType(ref, GrNewExpression.class);
          assert newExpression != null;
          final GrExpression qualifier = newExpression.getQualifier();
          assert qualifier != null;

          final PsiType type = qualifier.getType();
          if (!(type instanceof PsiClassType)) break;

          final PsiClassType classType = (PsiClassType)type;
          final PsiClass psiClass = classType.resolve();
          if (psiClass == null) break;

          final PsiClass[] allInnerClasses = psiClass.getAllInnerClasses();
          ArrayList<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();
          PsiResolveHelper helper = JavaPsiFacade.getInstance(ref.getProject()).getResolveHelper();

          for (final PsiClass innerClass : allInnerClasses) {
            if (refName.equals(innerClass.getName())) {
              result.add(new GroovyResolveResultImpl(innerClass, helper.isAccessible(innerClass, ref, null)));
            }
          }
          return result.toArray(new GroovyResolveResult[result.size()]);
        }
      }

      return GroovyResolveResult.EMPTY_ARRAY;
    }
  }

  private static final OurResolver RESOLVER = new OurResolver();

  public GroovyResolveResult advancedResolve() {
    ResolveResult[] results = ResolveCache.getInstance(getProject()).resolveWithCaching(this, RESOLVER, true, false);
    return results.length == 1 ? (GroovyResolveResult) results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @NotNull
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    final ResolveResult[] results = ResolveCache.getInstance(getProject()).resolveWithCaching(this, RESOLVER, true, incompleteCode);
    if (results.length == 0) {
      return GroovyResolveResult.EMPTY_ARRAY;
    }

    return (GroovyResolveResult[])results;
  }

  @Override
  public void processVariants(PrefixMatcher matcher, CompletionParameters parameters, Consumer<Object> consumer) {
    processVariantsImpl(getKind(true), consumer);
  }

  @NotNull
  @Override
  public PsiType[] getTypeArguments() {
    GrTypeArgumentList typeArgumentList = getTypeArgumentList();
    if (typeArgumentList != null && typeArgumentList.isDiamond()) {
      return inferDiamondTypeArguments();
    }
    else {
      return super.getTypeArguments();
    }
  }

  private PsiType[] inferDiamondTypeArguments() {
    PsiElement parent = getParent();
    if (!(parent instanceof GrNewExpression)) return PsiType.EMPTY_ARRAY;

    PsiType ltype = PsiImplUtil.inferExpectedTypeForDiamond((GrNewExpression)parent);

    if (ltype instanceof PsiClassType) {
      return ((PsiClassType)ltype).getParameters();
    }

    return PsiType.EMPTY_ARRAY;
  }
}
