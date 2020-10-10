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
package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.impl.compiled.ClsElementImpl;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.*;
import com.intellij.ui.IconDeferrer;
import com.intellij.ui.RowIcon;
import com.intellij.util.*;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author ik
 * Date: 24.10.2003
 */
public class PsiClassImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiClassImplUtil");

  private static final Key<CachedValue<Map>> MAP_IN_CLASS_KEY = Key.create("MAP_KEY");

  private PsiClassImplUtil() { }

  @NotNull public static PsiField[] getAllFields(final PsiClass aClass) {
    List<PsiField> map = getAllByMap(aClass, PsiField.class);
    return map.toArray(new PsiField[map.size()]);
  }

  @NotNull public static PsiMethod[] getAllMethods(final PsiClass aClass) {
    List<PsiMethod> methods = getAllByMap(aClass, PsiMethod.class);
    return methods.toArray(new PsiMethod[methods.size()]);
  }

  @NotNull public static PsiClass[] getAllInnerClasses(PsiClass aClass) {
    List<PsiClass> classes = getAllByMap(aClass, PsiClass.class);
    return classes.toArray(new PsiClass[classes.size()]);
  }

  @Nullable public static PsiField findFieldByName(PsiClass aClass, String name, boolean checkBases) {
    final List<PsiField> byMap = findByMap(aClass, name, checkBases, PsiField.class);
    return byMap.isEmpty() ? null : byMap.get(0);
  }

  @NotNull public static PsiMethod[] findMethodsByName(PsiClass aClass, String name, boolean checkBases) {
    List<PsiMethod> methods = findByMap(aClass, name, checkBases, PsiMethod.class);
    return methods.toArray(new PsiMethod[methods.size()]);
  }

  @Nullable public static PsiMethod findMethodBySignature(final PsiClass aClass, final PsiMethod patternMethod, final boolean checkBases) {
    final List<PsiMethod> result = findMethodsBySignature(aClass, patternMethod, checkBases, true);
    return result.isEmpty() ? null : result.get(0);
  }

  // ----------------------------- findMethodsBySignature -----------------------------------

  @NotNull public static PsiMethod[] findMethodsBySignature(final PsiClass aClass, final PsiMethod patternMethod, final boolean checkBases) {
    List<PsiMethod> methods = findMethodsBySignature(aClass, patternMethod, checkBases, false);
    return methods.toArray(new PsiMethod[methods.size()]);
  }

  @NotNull private static List<PsiMethod> findMethodsBySignature(final PsiClass aClass,
                                                    final PsiMethod patternMethod,
                                                    final boolean checkBases,
                                                    final boolean stopOnFirst) {
    final PsiMethod[] methodsByName = aClass.findMethodsByName(patternMethod.getName(), checkBases);
    if (methodsByName.length == 0) return Collections.emptyList();
    final List<PsiMethod> methods = new SmartList<PsiMethod>();
    final MethodSignature patternSignature = patternMethod.getSignature(PsiSubstitutor.EMPTY);
    for (final PsiMethod method : methodsByName) {
      final PsiClass superClass = method.getContainingClass();
      final PsiSubstitutor substitutor;
      if (checkBases && !aClass.equals(superClass)) {
        substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
      }
      else {
        substitutor = PsiSubstitutor.EMPTY;
      }
      final MethodSignature signature = method.getSignature(substitutor);
      if (signature.equals(patternSignature)) {
        methods.add(method);
        if (stopOnFirst) {
          break;
        }
      }
    }
    return methods;
  }

  // ----------------------------------------------------------------------------------------

  @Nullable public static PsiClass findInnerByName(PsiClass aClass, String name, boolean checkBases) {
    final List<PsiClass> byMap = findByMap(aClass, name, checkBases, PsiClass.class);
    return byMap.isEmpty() ? null : byMap.get(0);
  }

  @SuppressWarnings({"unchecked"})
  @NotNull private static <T extends PsiMember> List<T> findByMap(PsiClass aClass, String name, boolean checkBases, Class<T> type) {
    if (name == null) return Collections.emptyList();

    if (!checkBases) {
      T[] members = null;
      if (ReflectionCache.isAssignable(type,PsiMethod.class)) {
        members = (T[])aClass.getMethods();
      }
      else if (ReflectionCache.isAssignable(type,PsiClass.class)) {
        members = (T[])aClass.getInnerClasses();
      }
      else if (ReflectionCache.isAssignable(type,PsiField.class)) {
        members = (T[])aClass.getFields();
      }
      if (members == null) return Collections.emptyList();

      List<T> list = new ArrayList<T>();
      for (T member : members) {
        if (name.equals(member.getName())) list.add(member);
      }
      return list;
    }
    else {
      final Map<String, List<Pair<T, PsiSubstitutor>>> allMethodsMap = getMap(aClass, type);
      final List<Pair<T, PsiSubstitutor>> list = allMethodsMap.get(name);
      if (list == null) return Collections.emptyList();
      final List<T> ret = new ArrayList<T>();
      for (final Pair<T, PsiSubstitutor> info : list) {
        ret.add(info.getFirst());
      }

      return ret;
    }
  }

  public static <T extends PsiMember> List<Pair<T, PsiSubstitutor>> getAllWithSubstitutorsByMap(PsiClass aClass, Class<T> type) {
    final Map<String, List<Pair<T, PsiSubstitutor>>> allMap = getMap(aClass, type);
    return allMap.get(ALL);
  }

  @NotNull private static <T extends PsiMember> List<T> getAllByMap(PsiClass aClass, Class<T> type) {
    List<Pair<T, PsiSubstitutor>> pairs = getAllWithSubstitutorsByMap(aClass, type);

    assert pairs != null : "pairs should be already computed. Wrong allMap: " + getMap(aClass, type);

    final List<T> ret = new ArrayList<T>(pairs.size());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < pairs.size(); i++) {
      Pair<T, PsiSubstitutor> pair = pairs.get(i);
      T t = pair.getFirst();
      LOG.assertTrue(t != null, aClass);
      ret.add(t);
    }
    return ret;
  }

  @NonNls private static final String ALL = "Intellij-IDEA-ALL";

  private static Map<Class<? extends PsiMember>, Map<String, List<Pair<PsiMember, PsiSubstitutor>>>> buildAllMaps(final PsiClass psiClass) {
    final List<Pair<PsiMember, PsiSubstitutor>> classes = new ArrayList<Pair<PsiMember, PsiSubstitutor>>();
    final List<Pair<PsiMember, PsiSubstitutor>> fields = new ArrayList<Pair<PsiMember, PsiSubstitutor>>();
    final List<Pair<PsiMember, PsiSubstitutor>> methods = new ArrayList<Pair<PsiMember, PsiSubstitutor>>();

    FilterScopeProcessor<MethodCandidateInfo> processor = new FilterScopeProcessor<MethodCandidateInfo>(
      new OrFilter(ElementClassFilter.METHOD, ElementClassFilter.FIELD, ElementClassFilter.CLASS)) {
      @Override
      protected void add(PsiElement element, PsiSubstitutor substitutor) {
        if (element instanceof PsiMethod) {
          methods.add(new Pair<PsiMember, PsiSubstitutor>((PsiMethod)element, substitutor));
        }
        else if (element instanceof PsiField) {
          fields.add(new Pair<PsiMember, PsiSubstitutor>((PsiField)element, substitutor));
        }
        else if (element instanceof PsiClass) {
          classes.add(new Pair<PsiMember, PsiSubstitutor>((PsiClass)element, substitutor));
        }
      }
    };
    processDeclarationsInClassNotCached(psiClass, processor, ResolveState.initial(),  new THashSet<PsiClass>(), null, psiClass, false);

    Map<Class<? extends PsiMember>, Map<String, List<Pair<PsiMember, PsiSubstitutor>>>> result = new HashMap<Class<? extends PsiMember>, Map<String, List<Pair<PsiMember, PsiSubstitutor>>>>(3);
    result.put(PsiClass.class, generateMapByList(classes));
    result.put(PsiMethod.class, generateMapByList(methods));
    result.put(PsiField.class, generateMapByList(fields));
    return result;
  }

  private static Map<String, List<Pair<PsiMember, PsiSubstitutor>>> generateMapByList(@NotNull final List<Pair<PsiMember, PsiSubstitutor>> list) {
    Map<String, List<Pair<PsiMember, PsiSubstitutor>>> map = new HashMap<String, List<Pair<PsiMember, PsiSubstitutor>>>();
    map.put(ALL, list);
    for (final Pair<PsiMember, PsiSubstitutor> info : list) {
      final PsiMember element = info.getFirst();
      final String currentName = element.getName();
      List<Pair<PsiMember, PsiSubstitutor>> listByName = map.get(currentName);
      if (listByName == null) {
        listByName = new ArrayList<Pair<PsiMember, PsiSubstitutor>>(1);
        map.put(currentName, listByName);
      }
      listByName.add(info);
    }
    return map;
  }

  private static <T extends PsiMember> Map<String, List<Pair<T, PsiSubstitutor>>> getMap(final PsiClass aClass, Class<T> memberClazz) {
    CachedValue<Map> value = aClass.getUserData(MAP_IN_CLASS_KEY);
    if (value == null) {
      final CachedValueProvider<Map> provider = new ByNameCachedValueProvider(aClass);
      value = CachedValuesManager.getManager(aClass.getProject()).createCachedValue(provider, false);
      //Do not cache for nonphysical elements
      if (aClass.isPhysical()) {
        value = ((UserDataHolderEx)aClass).putUserDataIfAbsent(MAP_IN_CLASS_KEY, value);
      }
    }
    return getCachedMembers(value, memberClazz);
  }

  private static <T extends PsiMember> Map<String, List<Pair<T, PsiSubstitutor>>> getCachedMembers(CachedValue<Map> value,
                                                                                                   Class<T> memberClazz) {
    //noinspection unchecked
    return (Map<String, List<Pair<T, PsiSubstitutor>>>)value.getValue().get(memberClazz);
  }

  private static class ClassIconRequest {
    public final PsiClass psiClass;
    public final int flags;
    public final Icon symbolIcon;

    private ClassIconRequest(PsiClass psiClass, int flags, Icon symbolIcon) {
      this.psiClass = psiClass;
      this.flags = flags;
      this.symbolIcon = symbolIcon;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ClassIconRequest)) return false;

      ClassIconRequest that = (ClassIconRequest)o;

      if (flags != that.flags) return false;
      if (psiClass != null ? !psiClass.equals(that.psiClass) : that.psiClass != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = psiClass != null ? psiClass.hashCode() : 0;
      result = 31 * result + flags;
      return result;
    }
  }

  private static final Function<ClassIconRequest, Icon> FULL_ICON_EVALUATOR = new NullableFunction<ClassIconRequest, Icon>() {
    @Override
    public Icon fun(ClassIconRequest r) {
      if (!r.psiClass.isValid() || r.psiClass.getProject().isDisposed()) return null;

      final boolean isLocked = (r.flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !r.psiClass.isWritable();
      Icon symbolIcon = r.symbolIcon != null
                        ? r.symbolIcon
                        : ElementPresentationUtil.getClassIconOfKind(r.psiClass, ElementPresentationUtil.getClassKind(r.psiClass));
      RowIcon baseIcon = ElementPresentationUtil.createLayeredIcon(symbolIcon, r.psiClass, isLocked);
      return ElementPresentationUtil.addVisibilityIcon(r.psiClass, r.flags, baseIcon);
    }
  };

  public static Icon getClassIcon(final int flags, final PsiClass aClass) {
    return getClassIcon(flags, aClass, null);
  }

  public static Icon getClassIcon(int flags, PsiClass aClass, @Nullable Icon symbolIcon) {
    Icon base = Iconable.LastComputedIcon.get(aClass, flags);
    if (base == null) {
      if (symbolIcon == null) {
        symbolIcon = ElementPresentationUtil.getClassIconOfKind(aClass, ElementPresentationUtil.getBasicClassKind(aClass));
      }
      RowIcon baseIcon = ElementBase.createLayeredIcon(aClass, symbolIcon, 0);
      base = ElementPresentationUtil.addVisibilityIcon(aClass, flags, baseIcon);
    }

    return IconDeferrer.getInstance().defer(base, new ClassIconRequest(aClass, flags, symbolIcon), FULL_ICON_EVALUATOR);
  }

  public static SearchScope getClassUseScope(final PsiClass aClass) {
    final GlobalSearchScope maximalUseScope = ResolveScopeManager.getElementUseScope(aClass);
    if (aClass instanceof PsiAnonymousClass) {
      return new LocalSearchScope(aClass);
    }
    PsiFile file = aClass.getContainingFile();
    if (PsiImplUtil.isInServerPage(file)) return maximalUseScope;
    final PsiClass containingClass = aClass.getContainingClass();
    if (aClass.hasModifierProperty(PsiModifier.PUBLIC) ||
        aClass.hasModifierProperty(PsiModifier.PROTECTED)) {
      return containingClass != null ? containingClass.getUseScope() : maximalUseScope;
    }
    else if (aClass.hasModifierProperty(PsiModifier.PRIVATE) || aClass instanceof PsiTypeParameter) {
      PsiClass topClass = PsiUtil.getTopLevelClass(aClass);
      return new LocalSearchScope(topClass == null ? aClass.getContainingFile() : topClass);
    }
    else {
      PsiPackage aPackage = null;
      if (file instanceof PsiJavaFile) {
        aPackage = JavaPsiFacade.getInstance(aClass.getProject()).findPackage(((PsiJavaFile)file).getPackageName());
      }

      if (aPackage == null) {
        PsiDirectory dir = file.getContainingDirectory();
        if (dir != null) {
          aPackage = JavaDirectoryService.getInstance().getPackage(dir);
        }
      }

      if (aPackage != null) {
        SearchScope scope = PackageScope.packageScope(aPackage, false);
        scope = scope.intersectWith(maximalUseScope);
        return scope;
      }

      return new LocalSearchScope(file);
    }
  }

  public static boolean isMainMethod(PsiMethod method) {
    if (!PsiType.VOID.equals(method.getReturnType())) return false;
    String name = method.getName();
    if (!("main".equals(name) || "premain".equals(name))) return false;

    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
    MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
    try {
      MethodSignature main = createSignatureFromText(factory, "void main(String[] args);");
      if (MethodSignatureUtil.areSignaturesEqual(signature, main)) return true;
      MethodSignature premain = createSignatureFromText(factory, "void premain(String args, java.lang.instrument.Instrumentation i);");
      if (MethodSignatureUtil.areSignaturesEqual(signature, premain)) return true;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    return false;
  }

  private static MethodSignature createSignatureFromText(PsiElementFactory factory, String text) {
    return factory.createMethodFromText(text, null).getSignature(PsiSubstitutor.EMPTY);
  }

  private static class ByNameCachedValueProvider implements CachedValueProvider<Map> {
    private final PsiClass myClass;

    private ByNameCachedValueProvider(final PsiClass aClass) {
      myClass = aClass;
    }

    @Override
    public Result<Map> compute() {
      final Map<Class<? extends PsiMember>, Map<String, List<Pair<PsiMember, PsiSubstitutor>>>> map = buildAllMaps(myClass);
      return new Result<Map>(map, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }
  }

  public static boolean processDeclarationsInClass(PsiClass aClass,
                                                   PsiScopeProcessor processor,
                                                   ResolveState state,
                                                   @Nullable Set<PsiClass> visited,
                                                   PsiElement last,
                                                   PsiElement place,
                                                   boolean isRaw) {
    if (last instanceof PsiTypeParameterList || last instanceof PsiModifierList) return true; //TypeParameterList and ModifierList do not see our declarations
    if (visited != null && visited.contains(aClass)) return true;

    PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    isRaw = isRaw || PsiUtil.isRawSubstitutor(aClass, substitutor);

    CachedValue<Map> cache = aClass.getUserData(MAP_IN_CLASS_KEY);
    if (cache != null && cache.hasUpToDateValue()) {
      final NameHint nameHint = processor.getHint(NameHint.KEY);
      if (nameHint != null) {
        return processCachedMembersByName(aClass, processor, state, visited, last, place, isRaw, substitutor, cache, nameHint);
      }
    }
    return processDeclarationsInClassNotCached(aClass, processor, state, visited, last, place, isRaw);
  }

  private static boolean processCachedMembersByName(PsiClass aClass,
                                                    PsiScopeProcessor processor,
                                                    ResolveState state,
                                                    @Nullable Set<PsiClass> visited,
                                                    PsiElement last,
                                                    PsiElement place,
                                                    boolean isRaw,
                                                    PsiSubstitutor substitutor,
                                                    CachedValue<Map> cache, NameHint nameHint) {
    final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);

    PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.FIELD)) {
      final PsiField fieldByName = aClass.findFieldByName(nameHint.getName(state), false);
      if (fieldByName != null) {
        processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
        if (!processor.execute(fieldByName, state)) return false;
      }
      else {
        final Map<String, List<Pair<PsiField, PsiSubstitutor>>> allFieldsMap = getCachedMembers(cache, PsiField.class);

        final List<Pair<PsiField, PsiSubstitutor>> list = allFieldsMap.get(nameHint.getName(state));
        if (list != null) {
          for (final Pair<PsiField, PsiSubstitutor> candidate : list) {
            PsiField candidateField = candidate.getFirst();
            PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(candidateField.getContainingClass(), candidate.getSecond(), aClass,
                                                                     substitutor, place, factory);

            processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, candidateField.getContainingClass());
            if (!processor.execute(candidateField, state.put(PsiSubstitutor.KEY, finalSubstitutor))) return false;
          }
        }
      }
    }
    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      if (last != null && last.getParent() == aClass) {
        if (last instanceof PsiClass) {
          if (!processor.execute(last, state)) return false;
        }
        // Parameters
        final PsiTypeParameterList list = aClass.getTypeParameterList();
        if (list != null && !list.processDeclarations(processor, state, last, place)) return false;
      }
      if (!(last instanceof PsiReferenceList)) {
        final PsiClass classByName = aClass.findInnerClassByName(nameHint.getName(state), false);
        if (classByName != null) {
          processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
          if (!processor.execute(classByName, state)) return false;
        }
        else {
          final Map<String, List<Pair<PsiClass, PsiSubstitutor>>> allClassesMap = getCachedMembers(cache, PsiClass.class);

          final List<Pair<PsiClass, PsiSubstitutor>> list = allClassesMap.get(nameHint.getName(state));
          if (list != null) {
            for (final Pair<PsiClass, PsiSubstitutor> candidate : list) {
              final PsiClass inner = candidate.getFirst();
              final PsiClass containingClass = inner.getContainingClass();
              if (containingClass != null) {
                PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(containingClass, candidate.getSecond(), aClass,
                                                                         substitutor, place, factory);
                processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
                if (!processor.execute(inner, state.put(PsiSubstitutor.KEY, finalSubstitutor))) return false;
              }
            }
          }
        }
      }
    }
    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)) {
      if (processor instanceof MethodResolverProcessor) {
        final MethodResolverProcessor methodResolverProcessor = (MethodResolverProcessor)processor;
        if (methodResolverProcessor.isConstructor()) {
          final PsiMethod[] constructors = aClass.getConstructors();
          methodResolverProcessor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
          for (PsiMethod constructor : constructors) {
            if (!methodResolverProcessor.execute(constructor, state)) return false;
          }
          return true;
        }
      }
      final Map<String, List<Pair<PsiMethod, PsiSubstitutor>>> allMethodsMap = getCachedMembers(cache, PsiMethod.class);
      final List<Pair<PsiMethod, PsiSubstitutor>> list = allMethodsMap.get(nameHint.getName(state));
      if (list != null) {
        for (final Pair<PsiMethod, PsiSubstitutor> candidate : list) {
          ProgressIndicatorProvider.checkCanceled();
          PsiMethod candidateMethod = candidate.getFirst();
          if (processor instanceof MethodResolverProcessor) {
            if (candidateMethod.isConstructor() != ((MethodResolverProcessor)processor).isConstructor()) continue;
          }
          final PsiClass containingClass = candidateMethod.getContainingClass();
          if (visited != null && visited.contains(candidateMethod.getContainingClass())) {
            continue;
          }

          PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(containingClass, candidate.getSecond(), aClass,
                                                                   substitutor, place, factory);
          finalSubstitutor = checkRaw(isRaw, factory, candidateMethod, finalSubstitutor);
          processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
          if (!processor.execute(candidateMethod, state.put(PsiSubstitutor.KEY, finalSubstitutor))) return false;
        }

        if (visited != null) {
          for (Pair<PsiMethod, PsiSubstitutor> aList : list) {
            visited.add(aList.getFirst().getContainingClass());
          }
        }

      }
    }
    return true;
    }

  private static PsiSubstitutor checkRaw(boolean isRaw, PsiElementFactory factory, PsiMethod candidateMethod, PsiSubstitutor substitutor) {
    if (isRaw && !candidateMethod.hasModifierProperty(PsiModifier.STATIC)) { //static methods are not erased due to raw overriding
      PsiTypeParameter[] methodTypeParameters = candidateMethod.getTypeParameters();
      substitutor = factory.createRawSubstitutor(substitutor, methodTypeParameters);
    }
    return substitutor;
  }

  public static PsiSubstitutor obtainFinalSubstitutor(@NotNull PsiClass candidateClass, PsiSubstitutor candidateSubstitutor, PsiClass aClass,
                                                       PsiSubstitutor substitutor,
                                                       final PsiElement place,
                                                       PsiElementFactory elementFactory) {
    if (PsiUtil.isRawSubstitutor(aClass, substitutor)) {
      return elementFactory.createRawSubstitutor(candidateClass);
    }

    final PsiType containingType = elementFactory.createType(candidateClass, candidateSubstitutor, PsiUtil.getLanguageLevel(place));
    PsiType type = substitutor.substitute(containingType);
    if (!(type instanceof PsiClassType)) return candidateSubstitutor;
    return ((PsiClassType)type).resolveGenerics().getSubstitutor();
  }

  private static boolean processDeclarationsInClassNotCached(PsiClass aClass,
                                                             PsiScopeProcessor processor,
                                                             ResolveState state,
                                                             @Nullable Set<PsiClass> visited,
                                                             PsiElement last,
                                                             PsiElement place,
                                                             boolean isRaw) {
    if (visited == null) visited = new THashSet<PsiClass>();
    if (!visited.add(aClass)) return true;
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
    final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    final NameHint nameHint = processor.getHint(NameHint.KEY);


    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.FIELD)) {
      if (nameHint != null) {
        final PsiField fieldByName = aClass.findFieldByName(nameHint.getName(state), false);
        if (fieldByName != null) {
          if (!processor.execute(fieldByName, state)) return false;
        }
      }
      else {
        final PsiField[] fields = aClass.getFields();
        for (final PsiField field : fields) {
          if (!processor.execute(field, state)) return false;
        }
      }
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)) {
      PsiSubstitutor baseSubstitutor = state.get(PsiSubstitutor.KEY);
      final PsiMethod[] methods = nameHint != null ? aClass.findMethodsByName(nameHint.getName(state), false) : aClass.getMethods();
      for (final PsiMethod method : methods) {
        PsiSubstitutor finalSubstitutor = checkRaw(isRaw, factory, method, baseSubstitutor);
        ResolveState methodState = finalSubstitutor == baseSubstitutor ? state : state.put(PsiSubstitutor.KEY, finalSubstitutor);
        if (!processor.execute(method, methodState)) return false;
      }
    }

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      if (last != null && last.getParent() == aClass) {
        // Parameters
        final PsiTypeParameterList list = aClass.getTypeParameterList();
        if (list != null && !list.processDeclarations(processor, ResolveState.initial(), last, place)) return false;
      }

      if (!(last instanceof PsiReferenceList) && !(last instanceof PsiModifierList)) {
        // Inners
        if (nameHint != null) {
          final PsiClass inner = aClass.findInnerClassByName(nameHint.getName(state), false);
          if (inner != null) {
            if (!processor.execute(inner, state)) return false;
          }
        }
        else {
          final PsiClass[] inners = aClass.getInnerClasses();
          for (final PsiClass inner : inners) {
            if (!processor.execute(inner, state)) return false;
          }
        }
      }
    }

    return last instanceof PsiReferenceList || processSuperTypes(aClass, processor, visited, last, place, state, isRaw, factory);
  }

  private static boolean processSuperTypes(PsiClass aClass,
                                           PsiScopeProcessor processor,
                                           Set<PsiClass> visited,
                                           PsiElement last,
                                           PsiElement place,
                                           ResolveState state,
                                           boolean isRaw, PsiElementFactory factory) {
    for (final PsiClassType superType : aClass.getSuperTypes()) {
      final PsiClassType.ClassResolveResult superTypeResolveResult = superType.resolveGenerics();
      PsiClass superClass = superTypeResolveResult.getElement();
      if (superClass == null) continue;
      PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(superClass, superTypeResolveResult.getSubstitutor(), aClass, state.get(PsiSubstitutor.KEY),
                                                               place, factory);
      if (!processDeclarationsInClass(superClass, processor, state.put(PsiSubstitutor.KEY, finalSubstitutor), visited, last, place, isRaw)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public static PsiClass getSuperClass(PsiClass psiClass) {
    PsiManager manager = psiClass.getManager();
    GlobalSearchScope resolveScope = psiClass.getResolveScope();

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    if (psiClass.isInterface()) {
      return facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, resolveScope);
    }
    if (psiClass.isEnum()) {
      return facade.findClass(CommonClassNames.JAVA_LANG_ENUM, resolveScope);
    }

    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassReference = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassReference.resolve();
      if (baseClass == null || baseClass.isInterface()) return facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, resolveScope);
      return baseClass;
    }

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) return null;

    final PsiClassType[] referenceElements = psiClass.getExtendsListTypes();

    if (referenceElements.length == 0) return facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, resolveScope);

    PsiClass psiResoved = referenceElements[0].resolve();
    return psiResoved == null ? facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, resolveScope) : psiResoved;
  }

  @NotNull public static PsiClass[] getSupers(PsiClass psiClass) {
    final PsiClass[] supers = getSupersInner(psiClass);
    for (final PsiClass aSuper : supers) {
      LOG.assertTrue(aSuper != null);
    }
    return supers;
  }

  private static PsiClass[] getSupersInner(PsiClass psiClass) {
    PsiClassType[] extendsListTypes = psiClass.getExtendsListTypes();
    PsiClassType[] implementsListTypes = psiClass.getImplementsListTypes();

    if (psiClass.isInterface()) {
      return resolveClassReferenceList(extendsListTypes,
                                       psiClass.getManager(), psiClass.getResolveScope(), true);
    }

    if (psiClass instanceof PsiAnonymousClass) {
      PsiAnonymousClass psiAnonymousClass = (PsiAnonymousClass)psiClass;
      PsiClassType baseClassReference = psiAnonymousClass.getBaseClassType();
      PsiClass baseClass = baseClassReference.resolve();
      if (baseClass != null) {
        if (baseClass.isInterface()) {
          PsiClass objectClass = JavaPsiFacade.getInstance(psiClass.getProject()).findClass("java.lang.Object", psiClass.getResolveScope());
          return objectClass != null ? new PsiClass[]{objectClass, baseClass} : new PsiClass[]{baseClass};
        }
        return new PsiClass[]{baseClass};
      }

      PsiClass objectClass = JavaPsiFacade.getInstance(psiClass.getProject()).findClass("java.lang.Object", psiClass.getResolveScope());
      return objectClass != null ? new PsiClass[]{objectClass} : PsiClass.EMPTY_ARRAY;
    }
    else if (psiClass instanceof PsiTypeParameter) {
      if (extendsListTypes.length == 0) {
        final PsiClass objectClass =
          JavaPsiFacade.getInstance(psiClass.getProject()).findClass("java.lang.Object", psiClass.getResolveScope());
        return objectClass != null ? new PsiClass[]{objectClass} : PsiClass.EMPTY_ARRAY;
      }
      return resolveClassReferenceList(extendsListTypes, psiClass.getManager(),
                                       psiClass.getResolveScope(), false);
    }

    PsiClass[] interfaces = resolveClassReferenceList(implementsListTypes, psiClass.getManager(), psiClass.getResolveScope(), false);

    PsiClass superClass = getSuperClass(psiClass);
    if (superClass == null) return interfaces;
    PsiClass[] types = new PsiClass[interfaces.length + 1];
    types[0] = superClass;
    System.arraycopy(interfaces, 0, types, 1, interfaces.length);

    return types;
  }

  @NotNull public static PsiClassType[] getSuperTypes(PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassType = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassType.resolve();
      if (baseClass == null || !baseClass.isInterface()) {
        return new PsiClassType[]{baseClassType};
      }
      else {
        PsiClassType objectType = PsiType.getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope());
        return new PsiClassType[]{objectType, baseClassType};
      }
    }

    PsiClassType[] extendsTypes = psiClass.getExtendsListTypes();
    PsiClassType[] implementsTypes = psiClass.getImplementsListTypes();
    boolean hasExtends = extendsTypes.length != 0;
    int extendsListLength = extendsTypes.length + (hasExtends ? 0 : 1);
    PsiClassType[] result = new PsiClassType[extendsListLength + implementsTypes.length];

    System.arraycopy(extendsTypes, 0, result, 0, extendsTypes.length);
    if (!hasExtends) {
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) {
        return PsiClassType.EMPTY_ARRAY;
      }
      PsiManager manager = psiClass.getManager();
      PsiClassType objectType = PsiType.getJavaLangObject(manager, psiClass.getResolveScope());
      result[0] = objectType;
    }
    System.arraycopy(implementsTypes, 0, result, extendsListLength, implementsTypes.length);
    for (int i = 0; i < result.length; i++) {
      PsiClassType type = result[i];
      result[i] = (PsiClassType)PsiUtil.captureToplevelWildcards(type, psiClass);
    }
    return result;
  }

  private static PsiClassType getAnnotationSuperType(PsiClass psiClass, PsiElementFactory factory) {
    return factory.createTypeByFQClassName("java.lang.annotation.Annotation", psiClass.getResolveScope());
  }

  private static PsiClassType getEnumSuperType(PsiClass psiClass, PsiElementFactory factory) {
    PsiClassType superType;
    final PsiManager manager = psiClass.getManager();
    final PsiClass enumClass = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.lang.Enum", psiClass.getResolveScope());
    if (enumClass == null) {
      try {
        superType = (PsiClassType)factory.createTypeFromText("java.lang.Enum", null);
      }
      catch (IncorrectOperationException e) {
        superType = null;
      }
    }
    else {
      final PsiTypeParameter[] typeParameters = enumClass.getTypeParameters();
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      if (typeParameters.length == 1) {
        substitutor = substitutor.put(typeParameters[0], factory.createType(psiClass));
      }
      superType = new PsiImmediateClassType(enumClass, substitutor);
    }
    return superType;
  }

  public static PsiClass[] getInterfaces(PsiTypeParameter typeParameter) {
    final ArrayList<PsiClass> result = new ArrayList<PsiClass>();
    final PsiClassType[] referencedTypes = typeParameter.getExtendsListTypes();
    for (PsiClassType referencedType : referencedTypes) {
      final PsiClass psiClass = referencedType.resolve();
      if (psiClass != null && psiClass.isInterface()) {
        result.add(psiClass);
      }
    }
    return result.toArray(new PsiClass[result.size()]);
  }

  public static PsiClass[] getInterfaces(PsiClass psiClass) {
    if (psiClass.isInterface()) {
      final PsiClassType[] extendsListTypes = psiClass.getExtendsListTypes();
      return resolveClassReferenceList(extendsListTypes, psiClass.getManager(), psiClass.getResolveScope(), false);
    }

    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassReference = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassReference.resolve();
      return baseClass != null && baseClass.isInterface() ? new PsiClass[]{baseClass} : PsiClass.EMPTY_ARRAY;
    }

    final PsiClassType[] implementsListTypes = psiClass.getImplementsListTypes();
    return resolveClassReferenceList(implementsListTypes, psiClass.getManager(), psiClass.getResolveScope(), false);
  }

  private static PsiClass[] resolveClassReferenceList(final PsiClassType[] listOfTypes,
                                                      final PsiManager manager,
                                                      final GlobalSearchScope resolveScope,
                                                      boolean includeObject) {
    PsiClass objectClass = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.lang.Object", resolveScope);
    if (objectClass == null) includeObject = false;
    if (listOfTypes == null || listOfTypes.length == 0) {
      if (includeObject) return new PsiClass[]{objectClass};
      return PsiClass.EMPTY_ARRAY;
    }

    int referenceCount = listOfTypes.length;
    if (includeObject) referenceCount++;

    PsiClass[] resolved = new PsiClass[referenceCount];
    int resolvedCount = 0;

    if (includeObject) resolved[resolvedCount++] = objectClass;
    for (PsiClassType reference : listOfTypes) {
      PsiClass refResolved = reference.resolve();
      if (refResolved != null) resolved[resolvedCount++] = refResolved;
    }

    if (resolvedCount < referenceCount) {
      PsiClass[] shorter = new PsiClass[resolvedCount];
      System.arraycopy(resolved, 0, shorter, 0, resolvedCount);
      resolved = shorter;
    }

    return resolved;
  }

  public static List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(PsiClass psiClass, String name, boolean checkBases) {
    if (!checkBases) {
      final PsiMethod[] methodsByName = psiClass.findMethodsByName(name, false);
      final List<Pair<PsiMethod, PsiSubstitutor>> ret = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>(methodsByName.length);
      for (final PsiMethod method : methodsByName) {
        ret.add(new Pair<PsiMethod, PsiSubstitutor>(method, PsiSubstitutor.EMPTY));
      }
      return ret;
    }
    final Map<String, List<Pair<PsiMethod, PsiSubstitutor>>> map = getMap(psiClass, PsiMethod.class);
    final List<Pair<PsiMethod, PsiSubstitutor>> list = map.get(name);
    return list == null ?
           Collections.<Pair<PsiMethod, PsiSubstitutor>>emptyList() :
           Collections.unmodifiableList(list);
  }

  public static PsiClassType[] getExtendsListTypes(PsiClass psiClass) {
    if (psiClass.isEnum()) {
      return new PsiClassType[]{getEnumSuperType(psiClass, JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory())};
    }
    else if (psiClass.isAnnotationType()) {
      return new PsiClassType[]{getAnnotationSuperType(psiClass, JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory())};
    }
    final PsiReferenceList extendsList = psiClass.getExtendsList();
    if (extendsList != null) {
      return extendsList.getReferencedTypes();
    }
    return PsiClassType.EMPTY_ARRAY;
  }

  public static PsiClassType[] getImplementsListTypes(PsiClass psiClass) {
    final PsiReferenceList extendsList = psiClass.getImplementsList();
    if (extendsList != null) {
      return extendsList.getReferencedTypes();
    }
    return PsiClassType.EMPTY_ARRAY;
  }

  public static boolean isClassEquivalentTo(PsiClass aClass, PsiElement another) {
    if (aClass == another) return true;
    if (!(another instanceof PsiClass)) return false;
    String name1 = aClass.getName();
    if (name1 == null) return false;
    if (!another.isValid()) return false;
    String name2 = ((PsiClass)another).getName(); 
    if (name2 == null) return false;
    if (name1.hashCode() != name2.hashCode()) return false;
    if (!name1.equals(name2)) return false;
    String qName1 = aClass.getQualifiedName();
    String qName2 = ((PsiClass)another).getQualifiedName();
    if (qName1 == null || qName2 == null) {
      //noinspection StringEquality
      if (qName1 != qName2) return false;

      if (aClass instanceof PsiTypeParameter && another instanceof PsiTypeParameter) {
        PsiTypeParameter p1 = (PsiTypeParameter)aClass;
        PsiTypeParameter p2 = (PsiTypeParameter)another;

        return p1.getIndex() == p2.getIndex() &&
               aClass.getManager().areElementsEquivalent(p1.getOwner(), p2.getOwner());

      }
      else {
        return false;
      }
    }
    if (qName1.hashCode() != qName2.hashCode() || !qName1.equals(qName2)) {
      return false;
    }

    if (originalElement(aClass).equals(originalElement((PsiClass)another))) {
      return true;
    }

    final PsiFile file1 = aClass.getContainingFile().getOriginalFile();
    final PsiFile file2 = another.getContainingFile().getOriginalFile();

    //see com.intellij.openapi.vcs.changes.PsiChangeTracker
    //see com.intellij.psi.impl.PsiFileFactoryImpl#createFileFromText(CharSequence,PsiFile)
    final PsiFile original1 = file1.getUserData(PsiFileFactory.ORIGINAL_FILE);
    final PsiFile original2 = file2.getUserData(PsiFileFactory.ORIGINAL_FILE);
    if (original1 == original2 && original1 != null || original1 == file2 || original2 == file1 || file1 == file2) {
      return compareClassSeqNumber(aClass, (PsiClass)another);
    }    

    final FileIndexFacade fileIndex = ServiceManager.getService(file1.getProject(), FileIndexFacade.class);
    final VirtualFile vfile1 = file1.getViewProvider().getVirtualFile();
    final VirtualFile vfile2 = file2.getViewProvider().getVirtualFile();
    return (fileIndex.isInSource(vfile1) || fileIndex.isInLibraryClasses(vfile1)) &&
           (fileIndex.isInSource(vfile2) || fileIndex.isInLibraryClasses(vfile2));
  }

  private static boolean compareClassSeqNumber(PsiClass aClass, PsiClass another) {
    // there may be several classes in one file, they must not be equal
    int index1 = getSeqNumber(aClass);
    if (index1 == -1) return true;
    int index2 = getSeqNumber(another);
    return index1 == index2;
  }

  private static int getSeqNumber(PsiClass aClass) {
    // sequence number of this class among its parent' child classes named the same
    PsiElement parent = aClass.getParent();
    if (parent == null) return -1;
    int seqNo = 0;
    for (PsiElement child : parent.getChildren()) {
      if (child == aClass) return seqNo;
      if (child instanceof PsiClass && Comparing.strEqual(aClass.getName(), ((PsiClass)child).getName())) {
        seqNo++;
      }
    }
    return -1;
  }

  private static PsiElement originalElement(PsiClass aClass) {
    final PsiElement originalElement = aClass.getOriginalElement();
    ASTNode node = originalElement.getNode();
    if (node != null) {
      final PsiCompiledElement compiled = node.getUserData(ClsElementImpl.COMPILED_ELEMENT);
      if (compiled != null) {
        return compiled;
      }
    }
    return originalElement;
  }

  public static boolean isFieldEquivalentTo(PsiField field, PsiElement another) {
    if (!(another instanceof PsiField)) return false;
    String name1 = field.getName();
    if (name1 == null) return false;
    if (!another.isValid()) return false;

    String name2 = ((PsiField)another).getName();
    if (!name1.equals(name2)) return false;
    PsiClass aClass1 = field.getContainingClass();
    PsiClass aClass2 = ((PsiField)another).getContainingClass();
    return aClass1 != null && aClass2 != null && field.getManager().areElementsEquivalent(aClass1, aClass2);
  }

  public static boolean isMethodEquivalentTo(PsiMethod method1, PsiElement another) {
    if (method1 == another) return true;
    if (!(another instanceof PsiMethod)) return false;
    PsiMethod method2 = (PsiMethod)another;
    if (!another.isValid()) return false;
    if (!method1.getName().equals(method2.getName())) return false;
    PsiClass aClass1 = method1.getContainingClass();
    PsiClass aClass2 = method2.getContainingClass();
    PsiManager manager = method1.getManager();
    if (!(aClass1 != null && aClass2 != null && manager.areElementsEquivalent(aClass1, aClass2))) return false;

    PsiParameter[] parameters1 = method1.getParameterList().getParameters();
    PsiParameter[] parameters2 = method2.getParameterList().getParameters();
    if (parameters1.length != parameters2.length) return false;
    for (int i = 0; i < parameters1.length; i++) {
      PsiParameter parameter1 = parameters1[i];
      PsiParameter parameter2 = parameters2[i];
      PsiType type1 = parameter1.getType();
      PsiType type2 = parameter2.getType();
      if (!compareParamTypes(manager,type1, type2)) return false;
    }
    return true;
  }

  private static boolean compareParamTypes(@NotNull PsiManager manager, @NotNull PsiType type1, @NotNull PsiType type2) {
    if (type1 instanceof PsiArrayType) {
      if (!(type2 instanceof PsiArrayType)) return false;
      return compareParamTypes(manager, ((PsiArrayType)type1).getComponentType(), ((PsiArrayType)type2).getComponentType());
    }

    if (!(type1 instanceof PsiClassType) || !(type2 instanceof PsiClassType)) {
      return type1.equals(type2);
    }

    PsiClass class1 = ((PsiClassType)type1).resolve();
    PsiClass class2 = ((PsiClassType)type2).resolve();

    if (class1 instanceof PsiTypeParameter && class2 instanceof PsiTypeParameter) {
      return Comparing.equal(class1.getName(), class2.getName()) &&
             ((PsiTypeParameter)class1).getIndex() == ((PsiTypeParameter)class2).getIndex();
    }

    return manager.areElementsEquivalent(class1, class2);
  }
}
