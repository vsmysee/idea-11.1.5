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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ComparatorUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.*;

import static com.intellij.psi.CommonClassNames.*;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_LSHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_RSHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.*;

/**
 * @author ven
 */
public class TypesUtil {
  
  private static final LightCacheKey<Map<String, PsiClass>> PARENT_CACHE_KEY = LightCacheKey.create();
  
  @NonNls
  public static final Map<String, PsiType> ourQNameToUnboxed = new HashMap<String, PsiType>();
  public static final PsiPrimitiveType[] PRIMITIVES =
    new PsiPrimitiveType[]{PsiType.BYTE, PsiType.CHAR, PsiType.DOUBLE, PsiType.FLOAT, PsiType.INT, PsiType.SHORT, PsiType.LONG,
      PsiType.BOOLEAN, PsiType.VOID};

  private TypesUtil() {
  }

  @Nullable
  public static PsiType getNumericResultType(GrBinaryExpression binaryExpression) {
    PsiType lType = binaryExpression.getLeftOperand().getType();
    final GrExpression rop = binaryExpression.getRightOperand();
    PsiType rType = rop == null ? null : rop.getType();
    if (lType == null || rType == null) return null;
    return getLeastUpperBoundForNumericType(lType, rType);
  }

  @Nullable
  private static PsiType getLeastUpperBoundForNumericType(@NotNull PsiType lType, @NotNull PsiType rType) {
    String lCanonical = lType.getCanonicalText();
    String rCanonical = rType.getCanonicalText();
    if (JAVA_LANG_FLOAT.equals(lCanonical)) lCanonical = JAVA_LANG_DOUBLE;
    if (JAVA_LANG_FLOAT.equals(rCanonical)) rCanonical = JAVA_LANG_DOUBLE;
    if (TYPE_TO_RANK.containsKey(lCanonical) && TYPE_TO_RANK.containsKey(rCanonical)) {
      return TYPE_TO_RANK.get(lCanonical) > TYPE_TO_RANK.get(rCanonical) ? lType : rType;
    }
    return null;
  }

  @NotNull
  public static GroovyResolveResult[] getOverloadedOperatorCandidates(@NotNull PsiType thisType,
                                                                      IElementType tokenType,
                                                                      @NotNull GroovyPsiElement place,
                                                                      PsiType[] argumentTypes) {
    return ResolveUtil.getMethodCandidates(thisType, ourOperationsToOperatorNames.get(tokenType), place, argumentTypes);
  }

  public static GroovyResolveResult[] getOverloadedUnaryOperatorCandidates(@NotNull PsiType thisType,
                                                                      IElementType tokenType,
                                                                      @NotNull GroovyPsiElement place,
                                                                      PsiType[] argumentTypes) {
    return ResolveUtil.getMethodCandidates(thisType, ourUnaryOperationsToOperatorNames.get(tokenType), place, argumentTypes);
  }

  private static final Map<IElementType, String> ourPrimitiveTypesToClassNames = new HashMap<IElementType, String>();
  private static final String NULL = "null";

  static {
    ourPrimitiveTypesToClassNames.put(mSTRING_LITERAL, JAVA_LANG_STRING);
    ourPrimitiveTypesToClassNames.put(mGSTRING_LITERAL, JAVA_LANG_STRING);
    ourPrimitiveTypesToClassNames.put(mREGEX_LITERAL, JAVA_LANG_STRING);
    ourPrimitiveTypesToClassNames.put(mDOLLAR_SLASH_REGEX_LITERAL, JAVA_LANG_STRING);
    ourPrimitiveTypesToClassNames.put(mNUM_INT, JAVA_LANG_INTEGER);
    ourPrimitiveTypesToClassNames.put(mNUM_LONG, JAVA_LANG_LONG);
    ourPrimitiveTypesToClassNames.put(mNUM_FLOAT, JAVA_LANG_FLOAT);
    ourPrimitiveTypesToClassNames.put(mNUM_DOUBLE, JAVA_LANG_DOUBLE);
    ourPrimitiveTypesToClassNames.put(mNUM_BIG_INT, JAVA_MATH_BIG_INTEGER);
    ourPrimitiveTypesToClassNames.put(mNUM_BIG_DECIMAL, JAVA_MATH_BIG_DECIMAL);
    ourPrimitiveTypesToClassNames.put(kFALSE, JAVA_LANG_BOOLEAN);
    ourPrimitiveTypesToClassNames.put(kTRUE, JAVA_LANG_BOOLEAN);
    ourPrimitiveTypesToClassNames.put(kNULL, NULL);

    ourPrimitiveTypesToClassNames.put(kINT, JAVA_LANG_INTEGER);
    ourPrimitiveTypesToClassNames.put(kLONG, JAVA_LANG_LONG);
    ourPrimitiveTypesToClassNames.put(kFLOAT, JAVA_LANG_FLOAT);
    ourPrimitiveTypesToClassNames.put(kDOUBLE, JAVA_LANG_DOUBLE);
    ourPrimitiveTypesToClassNames.put(kBOOLEAN, JAVA_LANG_BOOLEAN);
    ourPrimitiveTypesToClassNames.put(kCHAR, JAVA_LANG_CHARACTER);
    ourPrimitiveTypesToClassNames.put(kBYTE, JAVA_LANG_BYTE);
  }

  private static final Map<IElementType, String> ourOperationsToOperatorNames = new HashMap<IElementType, String>();
  private static final Map<IElementType, String> ourUnaryOperationsToOperatorNames = new HashMap<IElementType, String>();

  static {
    ourOperationsToOperatorNames.put(mPLUS, "plus");
    ourOperationsToOperatorNames.put(mMINUS, "minus");
    ourOperationsToOperatorNames.put(mBAND, "and");
    ourOperationsToOperatorNames.put(mBOR, "or");
    ourOperationsToOperatorNames.put(mBXOR, "xor");
    ourOperationsToOperatorNames.put(mDIV, "div");
    ourOperationsToOperatorNames.put(mMOD, "mod");
    ourOperationsToOperatorNames.put(mSTAR, "multiply");
    ourOperationsToOperatorNames.put(kAS, "asType");
    ourOperationsToOperatorNames.put(mCOMPARE_TO, "compareTo");
    ourOperationsToOperatorNames.put(mGT, "compareTo");
    ourOperationsToOperatorNames.put(mGE, "compareTo");
    ourOperationsToOperatorNames.put(mLT, "compareTo");
    ourOperationsToOperatorNames.put(mLE, "compareTo");
    ourOperationsToOperatorNames.put(mSTAR_STAR, "power");
    ourOperationsToOperatorNames.put(COMPOSITE_LSHIFT_SIGN, "leftShift");
    ourOperationsToOperatorNames.put(COMPOSITE_RSHIFT_SIGN, "rightShift");

    ourUnaryOperationsToOperatorNames.put(mLNOT, "asBoolean");
    ourUnaryOperationsToOperatorNames.put(mPLUS, "positive");
    ourUnaryOperationsToOperatorNames.put(mMINUS, "negative");
    ourUnaryOperationsToOperatorNames.put(mDEC, "previous");
    ourUnaryOperationsToOperatorNames.put(mINC, "next");
    ourUnaryOperationsToOperatorNames.put(mBNOT, "bitwiseNegate");
  }

  private static final TObjectIntHashMap<String> TYPE_TO_RANK = new TObjectIntHashMap<String>();

  static {
    TYPE_TO_RANK.put(JAVA_LANG_BYTE, 1);
    TYPE_TO_RANK.put(JAVA_LANG_SHORT, 2);
    TYPE_TO_RANK.put(JAVA_LANG_CHARACTER, 2);
    TYPE_TO_RANK.put(JAVA_LANG_INTEGER, 3);
    TYPE_TO_RANK.put(JAVA_LANG_LONG, 4);
    TYPE_TO_RANK.put(JAVA_MATH_BIG_INTEGER, 5);
    TYPE_TO_RANK.put(JAVA_MATH_BIG_DECIMAL, 6);
    TYPE_TO_RANK.put(JAVA_LANG_FLOAT, 7);
    TYPE_TO_RANK.put(JAVA_LANG_DOUBLE, 8);
    TYPE_TO_RANK.put(JAVA_LANG_NUMBER, 9);
  }

  static {
    ourQNameToUnboxed.put(JAVA_LANG_BOOLEAN, PsiType.BOOLEAN);
    ourQNameToUnboxed.put(JAVA_LANG_BYTE, PsiType.BYTE);
    ourQNameToUnboxed.put(JAVA_LANG_CHARACTER, PsiType.CHAR);
    ourQNameToUnboxed.put(JAVA_LANG_SHORT, PsiType.SHORT);
    ourQNameToUnboxed.put(JAVA_LANG_INTEGER, PsiType.INT);
    ourQNameToUnboxed.put(JAVA_LANG_LONG, PsiType.LONG);
    ourQNameToUnboxed.put(JAVA_LANG_FLOAT, PsiType.FLOAT);
    ourQNameToUnboxed.put(JAVA_LANG_DOUBLE, PsiType.DOUBLE);
  }


  private static final TIntObjectHashMap<String> RANK_TO_TYPE = new TIntObjectHashMap<String>();

  static {
    RANK_TO_TYPE.put(1, JAVA_LANG_INTEGER);
    RANK_TO_TYPE.put(2, JAVA_LANG_INTEGER);
    RANK_TO_TYPE.put(3, JAVA_LANG_INTEGER);
    RANK_TO_TYPE.put(4, JAVA_LANG_LONG);
    RANK_TO_TYPE.put(5, JAVA_MATH_BIG_INTEGER);
    RANK_TO_TYPE.put(6, JAVA_MATH_BIG_DECIMAL);
    RANK_TO_TYPE.put(7, JAVA_LANG_DOUBLE);
    RANK_TO_TYPE.put(8, JAVA_LANG_DOUBLE);
    RANK_TO_TYPE.put(9, JAVA_LANG_NUMBER);
  }

  public static boolean isAssignable(PsiType lType, PsiType rType, PsiManager manager, GlobalSearchScope scope) {
    return isAssignable(lType, rType, manager, scope, true);
  }

  public static boolean isAssignable(PsiType lType, PsiType rType, PsiManager manager, GlobalSearchScope scope, boolean allowConversion) {
    if (allowConversion && isAssignableByMethodCallConversion(lType, rType, manager, scope)) {
      return true;
    }

    return _isAssignable(lType, rType, manager, scope, allowConversion);
  }

  public static boolean isAssignable(@NotNull PsiType lType, @NotNull PsiType rType, GroovyPsiElement context) {
    return isAssignable(lType, rType, context, true);
  }

  public static boolean isAssignable(@NotNull PsiType lType, @NotNull PsiType rType, GroovyPsiElement context, boolean allowConversion) {
    if (rType instanceof PsiIntersectionType) {
      for (PsiType child : ((PsiIntersectionType)rType).getConjuncts()) {
        if (isAssignable(lType, child, context, allowConversion)) {
          return true;
        }
      }
      return false;
    }
    if (lType instanceof PsiIntersectionType) {
      for (PsiType child : ((PsiIntersectionType)lType).getConjuncts()) {
        if (!isAssignable(child, rType, context, allowConversion)) {
          return false;
        }
      }
      return true;
    }

    if (allowConversion) {
      for (GrTypeConverter converter : GrTypeConverter.EP_NAME.getExtensions()) {
        final Boolean result = converter.isConvertible(lType, rType, context);
        if (result != null) {
          return result;
        }
      }
    }
    return (allowConversion && isAssignableByMethodCallConversion(lType, rType, context)) ||
           _isAssignable(lType, rType, context.getManager(), context.getResolveScope(), true);
  }

  private static boolean _isAssignable(PsiType lType, PsiType rType, PsiManager manager, GlobalSearchScope scope, boolean allowConversion) {
    if (lType == null || rType == null) {
      return false;
    }

    if (rType == PsiType.NULL && lType instanceof PsiPrimitiveType) return false;

    if (allowConversion) {
      //all numeric types are assignable
      if (isNumericType(lType)) {
        return isNumericType(rType) || rType == PsiType.NULL;
      }
      else if (isClassType(lType, JAVA_LANG_STRING)) {
        return true;
      }
    }

    rType = boxPrimitiveType(rType, manager, scope);
    lType = boxPrimitiveType(lType, manager, scope);

    return lType.isAssignableFrom(rType);
  }

  public static boolean isAssignableByMethodCallConversion(PsiType lType, PsiType rType, GroovyPsiElement context) {
    if (lType == null || rType == null) return false;

    if (isAssignableByMethodCallConversion(lType, rType, context.getManager(), context.getResolveScope())) {
      return true;
    }

    for (GrTypeConverter converter : GrTypeConverter.EP_NAME.getExtensions()) {
      final Boolean result = converter.isConvertible(lType, rType, context);
      if (result != null) {
        return result;
      }
    }

    return false;
  }

  public static boolean isAssignableByMethodCallConversion(PsiType lType, PsiType rType, PsiManager manager, GlobalSearchScope scope) {
    if (lType == null || rType == null) return false;

    if (rType instanceof GrTupleType && ((GrTupleType)rType).getComponentTypes().length == 0) {
      if (lType instanceof PsiArrayType ||
          InheritanceUtil.isInheritor(lType, JAVA_UTIL_LIST) ||
          InheritanceUtil.isInheritor(lType, JAVA_UTIL_SET)) {
        return true;
      }
    }

    if (isClassType(rType, GroovyCommonClassNames.GROOVY_LANG_GSTRING)) {
      if (isAssignable(lType, GroovyPsiManager.getInstance(manager.getProject()).createTypeByFQClassName(JAVA_LANG_STRING, scope), manager, scope)) {
        return true;
      }
    }

    if (isNumericType(lType) && isNumericType(rType)) {
      lType = unboxPrimitiveTypeWrapper(lType);
      if (isClassType(lType, JAVA_MATH_BIG_DECIMAL)) lType = PsiType.DOUBLE;
      rType = unboxPrimitiveTypeWrapper(rType);
      if (isClassType(rType, JAVA_MATH_BIG_DECIMAL)) rType = PsiType.DOUBLE;
    }
    else {
      if (rType == PsiType.NULL && lType instanceof PsiPrimitiveType) return false;

      rType = boxPrimitiveType(rType, manager, scope);
      lType = boxPrimitiveType(lType, manager, scope);
    }

    if (unboxPrimitiveTypeWrapper(lType) == PsiType.CHAR &&
        (isClassType(rType, GROOVY_LANG_GSTRING) || isClassType(rType, JAVA_LANG_STRING))) {
      return true;
    }

    if (rType instanceof GrMapType || rType instanceof GrTupleType) {
      Boolean result = isAssignableForNativeTypes(lType, (PsiClassType)rType, manager, scope);
      if (result != null) return result.booleanValue();
    }

    return TypeConversionUtil.isAssignable(lType, rType);

  }

  @Nullable
  private static Boolean isAssignableForNativeTypes(PsiType lType, PsiClassType rType, PsiManager manager, GlobalSearchScope scope) {
    if (!(lType instanceof PsiClassType)) return null;
    final PsiClassType.ClassResolveResult leftResult = ((PsiClassType)lType).resolveGenerics();
    final PsiClassType.ClassResolveResult rightResult = rType.resolveGenerics();
    final PsiClass leftClass = leftResult.getElement();
    PsiClass rightClass = rightResult.getElement();
    if (rightClass == null || leftClass == null) return null;

    if (!InheritanceUtil.isInheritorOrSelf(rightClass, leftClass, true)) return Boolean.FALSE;

    PsiSubstitutor rightSubstitutor = rightResult.getSubstitutor();

    if (!leftClass.hasTypeParameters()) return Boolean.TRUE;
    PsiSubstitutor leftSubstitutor = leftResult.getSubstitutor();

    if (!leftClass.getManager().areElementsEquivalent(leftClass, rightClass)) {
      rightSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(leftClass, rightClass, rightSubstitutor);
      rightClass = leftClass;
    }
    else if (!rightClass.hasTypeParameters()) return Boolean.TRUE;

    Iterator<PsiTypeParameter> li = PsiUtil.typeParametersIterator(leftClass);
    Iterator<PsiTypeParameter> ri = PsiUtil.typeParametersIterator(rightClass);
    while (li.hasNext()) {
      if (!ri.hasNext()) return Boolean.FALSE;
      PsiTypeParameter lp = li.next();
      PsiTypeParameter rp = ri.next();
      final PsiType typeLeft = leftSubstitutor.substitute(lp);
      if (typeLeft == null) continue;
      final PsiType typeRight = rightSubstitutor.substituteWithBoundsPromotion(rp);
      if (typeRight == null) {
        return Boolean.TRUE;
      }
      if (!isAssignableByMethodCallConversion(typeLeft, typeRight, manager, scope)) return Boolean.FALSE;
    }
    return Boolean.TRUE;
  }

  public static boolean isNumericType(PsiType type) {
    if (type instanceof PsiClassType) {
      return TYPE_TO_RANK.contains(type.getCanonicalText());
    }

    return type instanceof PsiPrimitiveType && TypeConversionUtil.isNumericType(type);
  }

  public static PsiType unboxPrimitiveTypeWrapperAndEraseGenerics(PsiType result) {
    return TypeConversionUtil.erasure(unboxPrimitiveTypeWrapper(result));
  }

  @Nullable
  public static PsiType unboxPrimitiveTypeWrapper(@Nullable PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass != null) {
        PsiType unboxed = ourQNameToUnboxed.get(psiClass.getQualifiedName());
        if (unboxed != null) type = unboxed;
      }
    }
    return type;
  }

  public static PsiType boxPrimitiveType(PsiType result, PsiManager manager, GlobalSearchScope resolveScope, boolean boxVoid) {
    if (result instanceof PsiPrimitiveType && (boxVoid || result != PsiType.VOID)) {
      PsiPrimitiveType primitive = (PsiPrimitiveType)result;
      String boxedTypeName = primitive.getBoxedTypeName();
      if (boxedTypeName != null) {
        return GroovyPsiManager.getInstance(manager.getProject()).createTypeByFQClassName(boxedTypeName, resolveScope);
      }
    }

    return result;
  }

  public static PsiType boxPrimitiveType(PsiType result, PsiManager manager, GlobalSearchScope resolveScope) {
    return boxPrimitiveType(result, manager, resolveScope, false);
  }
  @NotNull
  public static PsiClassType createType(String fqName, @NotNull PsiElement context) {
    return createTypeByFQClassName(fqName, context);
  }

  @NotNull
  public static PsiClassType getJavaLangObject(PsiElement context) {
    return PsiType.getJavaLangObject(context.getManager(), context.getResolveScope());
  }

  @Nullable
  public static PsiType getLeastUpperBoundNullable(@Nullable PsiType type1, @Nullable PsiType type2, PsiManager manager) {
    if (type1 == null) return type2;
    if (type2 == null) return type1;
    return getLeastUpperBound(type1, type2, manager);
  }

  @Nullable
  public static PsiType getLeastUpperBoundNullable(@NotNull Iterable<PsiType> collection, @NotNull PsiManager manager) {
    Iterator<PsiType> iterator = collection.iterator();
    if (!iterator.hasNext()) return null;
    PsiType result = iterator.next();
    while (iterator.hasNext()) {
      result = getLeastUpperBoundNullable(result, iterator.next(), manager);
    }
    return result;
  }

  @Nullable
  public static PsiType getLeastUpperBound(@NotNull PsiType type1, @NotNull PsiType type2, PsiManager manager) {
    if (type1 instanceof GrTupleType && type2 instanceof GrTupleType) {
      GrTupleType tuple1 = (GrTupleType)type1;
      GrTupleType tuple2 = (GrTupleType)type2;
      PsiType[] components1 = tuple1.getComponentTypes();
      PsiType[] components2 = tuple2.getComponentTypes();
      PsiType[] components3 = new PsiType[Math.min(components1.length, components2.length)];
      for (int i = 0; i < components3.length; i++) {
        PsiType c1 = components1[i];
        PsiType c2 = components2[i];
        if (c1 == null || c2 == null) {
          components3[i] = null;
        }
        else {
          components3[i] = getLeastUpperBound(c1, c2, manager);
        }
      }
      return new GrTupleType(components3, JavaPsiFacade.getInstance(manager.getProject()), tuple1.getScope().intersectWith(tuple2.getResolveScope()));
    }
    else if (type1 instanceof GrMapType && type2 instanceof GrMapType) {
      return GrMapType.merge(((GrMapType)type1), ((GrMapType)type2));
    }
    else if (type1 instanceof GrClosureType && type2 instanceof GrClosureType) {
      GrClosureType clType1 = (GrClosureType)type1;
      GrClosureType clType2 = (GrClosureType)type2;
      GrClosureSignature signature1 = clType1.getSignature();
      GrClosureSignature signature2 = clType2.getSignature();

      if (signature1.getParameterCount() == signature2.getParameterCount()) {
        final GrClosureSignature signature = GrClosureSignatureImpl.getLeastUpperBound(signature1, signature2, manager);
        if (signature != null) {
          GlobalSearchScope scope = clType1.getResolveScope().intersectWith(clType2.getResolveScope());
          final LanguageLevel languageLevel = ComparatorUtil.max(clType1.getLanguageLevel(), clType2.getLanguageLevel());
          return GrClosureType.create(signature, scope, JavaPsiFacade.getInstance(manager.getProject()), languageLevel, true);
        }
      }
    }
    else if (GroovyCommonClassNames.GROOVY_LANG_GSTRING.equals(type1.getCanonicalText()) &&
             CommonClassNames.JAVA_LANG_STRING.equals(type2.getInternalCanonicalText())) {
      return type2;
    }
    else if (GroovyCommonClassNames.GROOVY_LANG_GSTRING.equals(type2.getCanonicalText()) &&
             CommonClassNames.JAVA_LANG_STRING.equals(type1.getInternalCanonicalText())) {
      return type1;
    }
    final PsiType result = getLeastUpperBoundForNumericType(type1, type2);
    if (result != null) return result;
    return GenericsUtil.getLeastUpperBound(type1, type2, manager);
  }

  @Nullable
  public static PsiType getPsiType(PsiElement context, IElementType elemType) {
    if (elemType == kNULL) {
      return PsiType.NULL;
    }
    final String typeName = getBoxedTypeName(elemType);
    if (typeName != null) {
      return createTypeByFQClassName(typeName, context);
    }
    return null;
  }

  @Nullable
  public static String getBoxedTypeName(IElementType elemType) {
    return ourPrimitiveTypesToClassNames.get(elemType);
  }

  @NotNull
  public static PsiType getLeastUpperBound(PsiClass[] classes, PsiManager manager) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());

    if (classes.length == 0) return factory.createTypeByFQClassName(JAVA_LANG_OBJECT);

    PsiType type = factory.createType(classes[0]);

    for (int i = 1; i < classes.length; i++) {
      PsiType t = getLeastUpperBound(type, factory.createType(classes[i]), manager);
      if (t != null) {
        type = t;
      }
    }

    return type;
  }

  public static boolean isClassType(@Nullable PsiType type, @NotNull String qName) {
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      return psiClass != null && qName.equals(psiClass.getQualifiedName());
    }
    return false;
  }

  public static PsiSubstitutor composeSubstitutors(PsiSubstitutor s1, PsiSubstitutor s2) {
    final Map<PsiTypeParameter, PsiType> map = s1.getSubstitutionMap();
    Map<PsiTypeParameter, PsiType> result = new THashMap<PsiTypeParameter, PsiType>(map.size());
    for (PsiTypeParameter parameter : map.keySet()) {
      result.put(parameter, s2.substitute(map.get(parameter)));
    }
    return PsiSubstitutorImpl.createSubstitutor(result);
  }

  @NotNull
  public static PsiClassType createTypeByFQClassName(@NotNull String fqName, @NotNull PsiElement context) {
    return GroovyPsiManager.getInstance(context.getProject()).createTypeByFQClassName(fqName, context.getResolveScope());
  }

  @Nullable
  public static PsiType createJavaLangClassType(@Nullable PsiType type,
                                         Project project,
                                         GlobalSearchScope resolveScope) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiType result = null;
    PsiClass javaLangClass = facade.findClass(JAVA_LANG_CLASS, resolveScope);
    if (javaLangClass != null) {
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      final PsiTypeParameter[] typeParameters = javaLangClass.getTypeParameters();
      if (typeParameters.length == 1) {
        substitutor = substitutor.put(typeParameters[0], type);
      }
      result = facade.getElementFactory().createType(javaLangClass, substitutor);
    }
    return result;
  }

  @NotNull
  public static PsiPrimitiveType getPrimitiveTypeByText(String typeText) {
    for (final PsiPrimitiveType primitive : PRIMITIVES) {
      if (PsiType.VOID.equals(primitive)) {
        return primitive;
      }
      if (primitive.getCanonicalText().equals(typeText)) {
        return primitive;
      }

    }

    assert false: "Unknown primitive type";
    return null;
  }

  @NotNull
  public static PsiClassType createListType(@NotNull PsiClass elements) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(elements.getProject());
    GlobalSearchScope resolveScope = elements.getResolveScope();
    PsiClass listClass = facade.findClass(CommonClassNames.JAVA_UTIL_LIST, resolveScope);
    if (listClass == null) {
      return facade.getElementFactory().createTypeByFQClassName(CommonClassNames.JAVA_UTIL_LIST, resolveScope);
    }
    return facade.getElementFactory().createType(listClass, facade.getElementFactory().createType(elements));
  }

  @NotNull
  public static PsiType createSetType(@NotNull PsiElement context, @NotNull PsiType type) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
    GlobalSearchScope resolveScope = context.getResolveScope();

    PsiClass setClass = facade.findClass(CommonClassNames.JAVA_UTIL_SET, resolveScope);
    if (setClass != null) {
      return facade.getElementFactory().createType(setClass, type);
    }

    return facade.getElementFactory().createTypeByFQClassName(CommonClassNames.JAVA_UTIL_SET, resolveScope);
  }

  public static Map<String, PsiClass> getSuperClassesWithCache(@NotNull PsiClass aClass) {
    Map<String, PsiClass> superClassNames = PARENT_CACHE_KEY.getCachedValue(aClass);
    if (superClassNames == null) {
      Set<PsiClass> superClasses = new HashSet<PsiClass>();
      superClasses.add(aClass);
      InheritanceUtil.getSuperClasses(aClass, superClasses, true);

      superClassNames = new LinkedHashMap<String, PsiClass>();
      for (PsiClass superClass : superClasses) {
        superClassNames.put(superClass.getQualifiedName(), superClass);
      }

      superClassNames = PARENT_CACHE_KEY.putCachedValue(aClass, superClassNames);
    }

    return superClassNames;
  }

  @Nullable
  public static PsiType substituteBoxAndNormalizeType(@Nullable PsiType type,
                                                      @NotNull PsiSubstitutor substitutor,
                                                      @NotNull GrExpression expression) {
    if (type == null) return null;
    GlobalSearchScope resolveScope = expression.getResolveScope();
    PsiManager manager = expression.getManager();
    type = substitutor.substitute(type);
    type = boxPrimitiveType(type, manager, resolveScope);
    if (type == null) return null;
    return PsiImplUtil.normalizeWildcardTypeByPosition(type, expression);
  }

  @Nullable
  public static PsiType getItemType(@Nullable PsiType containerType) {
    if (containerType == null) return null;

    if (containerType instanceof PsiArrayType) return ((PsiArrayType)containerType).getComponentType();
    return PsiUtil.extractIterableTypeParameter(containerType, false);
  }
}
