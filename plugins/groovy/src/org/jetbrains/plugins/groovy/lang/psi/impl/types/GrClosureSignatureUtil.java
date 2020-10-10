/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCurriedClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class GrClosureSignatureUtil {
  private GrClosureSignatureUtil() {
  }


  @Nullable
  public static GrClosureSignature createSignature(GrCall call) {
    if (call instanceof GrMethodCall) {
      final GrExpression invokedExpression = ((GrMethodCall)call).getInvokedExpression();
      final PsiType type = invokedExpression.getType();
      if (type instanceof GrClosureType) return ((GrClosureType)type).getSignature();
    }

    final GroovyResolveResult resolveResult = call.advancedResolve();
    final PsiElement element = resolveResult.getElement();
    if (element instanceof PsiMethod) {
      return createSignature((PsiMethod)element, resolveResult.getSubstitutor());
    }

    return null;
  }

  public static GrClosureSignature createSignature(final GrClosableBlock block) {
    return new GrClosureSignatureImpl(block.getAllParameters(), null) {
      @Override
      public PsiType getReturnType() {
        return block.getReturnType();
      }

      @Override
      public boolean isValid() {
        return block.isValid();
      }
    };
  }

  public static GrClosureSignature createSignature(final PsiMethod method, PsiSubstitutor substitutor) {
    return new GrClosureSignatureImpl(method.getParameterList().getParameters(), null, substitutor) {
      @Override
      public PsiType getReturnType() {
        return getSubstitutor().substitute(PsiUtil.getSmartReturnType(method));
      }

      @Override
      public boolean isValid() {
        return method.isValid();
      }
    };
  }

  public static GrClosureSignature removeParam(final GrClosureSignature signature, int i) {
    final GrClosureParameter[] newParams = ArrayUtil.remove(signature.getParameters(), i);
    return new GrClosureSignatureImpl(newParams, null, newParams.length > 0 && signature.isVarargs()) {
      @Override
      public PsiType getReturnType() {
        return signature.getReturnType();
      }

      @Override
      public boolean isValid() {
        return signature.isValid();
      }
    };
  }

  public static GrClosureSignature createSignatureWithErasedParameterTypes(final PsiMethod method) {
    final PsiParameter[] params = method.getParameterList().getParameters();
    final GrClosureParameter[] closureParams = new GrClosureParameter[params.length];
    for (int i = 0; i < params.length; i++) {
      PsiParameter param = params[i];
      PsiType type = TypeConversionUtil.erasure(param.getType());
      closureParams[i] = new GrClosureParameterImpl(type, GrClosureParameterImpl.isParameterOptional(param),
                                                    GrClosureParameterImpl.getDefaultInitializer(param));
    }
    return new GrClosureSignatureImpl(closureParams, null, GrClosureParameterImpl.isVararg(closureParams)) {
      @Override
      public PsiType getReturnType() {
        return PsiUtil.getSmartReturnType(method);
      }

      @Override
      public boolean isValid() {
        return method.isValid();
      }
    };
  }

  public static GrClosureSignature createSignature(PsiParameter[] parameters, @Nullable PsiType returnType) {
    return new GrClosureSignatureImpl(parameters, returnType);
  }

  public static boolean isSignatureApplicable(GrClosureSignature signature, PsiType[] args, GroovyPsiElement context) {
    if (mapArgTypesToParameters(signature, args, context, false) != null) return true;

    // check for the case foo([1, 2, 3]) if foo(int, int, int)
    if (args.length == 1 && PsiUtil.isInMethodCallContext(context)) {
      final GrClosureParameter[] parameters = signature.getParameters();
      if (parameters.length == 1 && parameters[0].getType() instanceof PsiArrayType) return false;
      PsiType arg = args[0];
      if (arg instanceof GrTupleType) {
        args = ((GrTupleType)arg).getComponentTypes();
        if (mapArgTypesToParameters(signature, args, context, false) != null) return true;
      }
    }
    return false;
  }

  @Nullable
  public static ArgInfo<PsiType>[] mapArgTypesToParameters(@NotNull GrClosureSignature signature,
                                                           PsiType[] args,
                                                           GroovyPsiElement context,
                                                           boolean partial) {
    return mapParametersToArguments(signature, args, FunctionUtil.<PsiType>id(), context, partial);
  }

  private static class ArgWrapper<Arg> {
    PsiType type;
    @Nullable Arg arg;

    private ArgWrapper(PsiType type, Arg arg) {
      this.type = type;
      this.arg = arg;
    }
  }

  private static <Arg> Function<ArgWrapper<Arg>, PsiType> ARG_WRAPPER_COMPUTER() {
    return new Function<ArgWrapper<Arg>, PsiType>() {
      @Override
      public PsiType fun(ArgWrapper<Arg> argWrapper) {
        return argWrapper.type;
      }
    };
  }

  @Nullable
  private static <Arg> ArgWrapper<Arg>[] getActualArgs(GrCurriedClosureSignature signature,
                                                       Arg[] args,
                                                       Function<Arg, PsiType> typeComputer) {
    List<ArgWrapper<Arg>> actual = new ArrayList<ArgWrapper<Arg>>(signature.getParameterCount());
    for (Arg arg : args) {
      actual.add(new ArgWrapper<Arg>(typeComputer.fun(arg), arg));
    }
    GrCurriedClosureSignature curried = signature;
    while (true) {
      final PsiType[] curriedArgs = curried.getCurriedArgs();
      int curriedPosition = curried.getCurriedPosition();
      if (curriedPosition == -1) curriedPosition = actual.size();
      if (curriedPosition > actual.size()) return null;

      for (int i = 0; i < curriedArgs.length; i++, curriedPosition++) {
        actual.add(curriedPosition, new ArgWrapper<Arg>(curriedArgs[i], null));
      }
      if (!(curried.getOriginalSignature() instanceof GrCurriedClosureSignature)) break;
      curried = (GrCurriedClosureSignature)curried.getOriginalSignature();
    }
    return actual.toArray(new ArgWrapper[args.length]);
  }

  public static GrClosureSignature getOriginalSignature(GrClosureSignature signature) {
    if (signature instanceof GrCurriedClosureSignature) {
      return getOriginalSignature(((GrCurriedClosureSignature)signature).getOriginalSignature());
    }
    return signature;
  }

  @Nullable
  private static <Arg> ArgInfo<Arg>[] mapParametersToArguments(@NotNull GrClosureSignature signature,
                                                               Arg[] args,
                                                               Function<Arg, PsiType> typeComputer,
                                                               @NotNull GroovyPsiElement context,
                                                               boolean partial) {
    if (signature instanceof GrCurriedClosureSignature) {
      final ArgWrapper<Arg>[] actualArgs = getActualArgs((GrCurriedClosureSignature)signature, args, typeComputer);
      if (actualArgs == null) return null;
      final ArgInfo<ArgWrapper<Arg>>[] argInfos =
        mapParametersToArguments(getOriginalSignature(signature), actualArgs, GrClosureSignatureUtil.<Arg>ARG_WRAPPER_COMPUTER(), context, partial);
      if (argInfos == null) return null;

      final ArgInfo<Arg>[] result = new ArgInfo[argInfos.length];
      for (int i = 0; i < argInfos.length; i++) {
        ArgInfo<ArgWrapper<Arg>> info = argInfos[i];
        List<Arg> list = new ArrayList<Arg>();
        for (ArgWrapper<Arg> arg : info.args) {
          if (arg.arg != null) list.add(arg.arg);
        }
        result[i] = new ArgInfo<Arg>(list, info.isMultiArg);
      }
      return result;
    }
    if (checkForOnlyMapParam(signature, args.length)) return ArgInfo.empty_array();
    GrClosureParameter[] params = signature.getParameters();
    if (args.length > params.length && !signature.isVarargs()) return null;
    int optional = getOptionalParamCount(signature, false);
    int notOptional = params.length - optional;
    if (signature.isVarargs()) notOptional--;
    if (notOptional > args.length && !partial) return null;

    final ArgInfo<Arg>[] map = mapSimple(params, args, typeComputer, context);
    if (map != null) return map;

    if (signature.isVarargs()) {
      return new ParameterMapperForVararg<Arg>(context, params, args, typeComputer).isApplicable();
    }
    return null;
  }

  private static boolean checkForOnlyMapParam(@NotNull GrClosureSignature signature, final int argCount) {
    if (argCount > 0) return false;
    final GrClosureParameter[] parameters = signature.getParameters();
    if (parameters.length != 1) return false;
    final PsiType type = parameters[0].getType();
    return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP);
  }

  @Nullable
  private static <Arg> ArgInfo<Arg>[] mapSimple(GrClosureParameter[] params,
                                                Arg[] args,
                                                Function<Arg, PsiType> typeComputer,
                                                GroovyPsiElement context) {
    ArgInfo<Arg>[] map = new ArgInfo[params.length];
    int optional = getOptionalParamCount(params, false);
    int notOptional = params.length - optional;
    int optionalArgs = args.length - notOptional;
    int cur = 0;
    for (int i = 0; i < args.length; i++, cur++) {
      while (optionalArgs == 0 && cur < params.length && params[cur].isOptional()) {
        cur++;
      }
      if (cur == params.length) return null;
      if (params[cur].isOptional()) optionalArgs--;
      if (!isAssignableByConversion(params[cur].getType(), typeComputer.fun(args[i]), context)) return null;
      map[cur] = new ArgInfo<Arg>(args[i]);
    }
    for (int i = 0; i < map.length; i++) {
      if (map[i] == null) map[i] = new ArgInfo<Arg>(Collections.<Arg>emptyList(), false);
    }
    return map;
  }

  private static boolean isAssignableByConversion(PsiType paramType, PsiType argType, GroovyPsiElement context) {
    if (argType == null) {
      return true;
    }
    return TypesUtil.isAssignableByMethodCallConversion(paramType, argType, context);
  }

  @Nullable
  public static GrClosureSignature createSignature(GroovyResolveResult resolveResult) {
    final PsiElement resolved = resolveResult.getElement();
    if (!(resolved instanceof PsiMethod)) return null;
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    return createSignature((PsiMethod)resolved, substitutor);
  }

  private static class ParameterMapperForVararg<Arg> {
    private final GroovyPsiElement context;
    private final GrClosureParameter[] params;
    private final Arg[] args;
    private final PsiType[] types;
    private final PsiType vararg;
    private final int paramLength;
    private final ArgInfo<Arg>[] map;

    private ParameterMapperForVararg(GroovyPsiElement context,
                                     GrClosureParameter[] params,
                                     Arg[] args,
                                     Function<Arg, PsiType> typeComputer) {
      this.context = context;
      this.params = params;
      this.args = args;
      this.types = new PsiType[args.length];
      for (int i = 0; i < args.length; i++) {
        types[i] = typeComputer.fun(args[i]);
      }
      paramLength = params.length - 1;
      vararg = ((PsiArrayType)params[paramLength].getType()).getComponentType();
      map = new ArgInfo[params.length];
    }

    @Nullable
    private ArgInfo<Arg>[] isApplicable() {
      int notOptionals = 0;
      for (int i = 0; i < paramLength; i++) {
        if (!params[i].isOptional()) notOptionals++;
      }
      if (isApplicableInternal(0, 0, false, notOptionals)) {
        for (int i = 0; i < map.length; i++) {
          if (map[i] == null) map[i] = new ArgInfo<Arg>(false);
        }
        return map;
      }
      else {
        return null;
      }
    }

    private boolean isApplicableInternal(int curParam, int curArg, boolean skipOptionals, int notOptional) {
      int startParam = curParam;
      if (notOptional > args.length - curArg) return false;
      if (notOptional == args.length - curArg) skipOptionals = true;

      while (curArg < args.length) {
        if (skipOptionals) {
          while (curParam < paramLength && params[curParam].isOptional()) curParam++;
        }

        if (curParam == paramLength) break;

        if (params[curParam].isOptional()) {
          if (isAssignableByConversion(params[curParam].getType(), types[curArg], context) &&
              isApplicableInternal(curParam + 1, curArg + 1, false, notOptional)) {
            map[curParam] = new ArgInfo<Arg>(args[curArg]);
            return true;
          }
          skipOptionals = true;
        }
        else {
          if (!isAssignableByConversion(params[curParam].getType(), types[curArg], context)) {
            for (int i = startParam; i < curParam; i++) map[i] = null;
            return false;
          }
          map[curParam] = new ArgInfo<Arg>(args[curArg]);
          notOptional--;
          curArg++;
          curParam++;
        }
      }

      List<Arg> varargs = new ArrayList<Arg>();
      for (; curArg < args.length; curArg++) {
        if (!isAssignableByConversion(vararg, types[curArg], context)) {
          for (int i = startParam; i < curParam; i++) map[i] = null;
          return false;
        }
        varargs.add(args[curArg]);
      }
      map[paramLength] = new ArgInfo<Arg>(varargs, true);
      return true;
    }
  }

  public static int getOptionalParamCount(GrClosureSignature signature, boolean hasNamedArgs) {
    return getOptionalParamCount(signature.getParameters(), hasNamedArgs);
  }

  public static int getOptionalParamCount(GrClosureParameter[] parameters, boolean hasNamedArgs) {
    int count = 0;
    int i = 0;
    if (hasNamedArgs) i++;
    for (; i < parameters.length; i++) {
      GrClosureParameter parameter = parameters[i];
      if (parameter.isOptional()) count++;
    }
    return count;
  }

  public static class ArgInfo<ArgType> {
    public static final ArgInfo[] EMPTY_ARRAY = new ArgInfo[0];

    public List<ArgType> args;
    public final boolean isMultiArg;

    public ArgInfo(List<ArgType> args, boolean multiArg) {
      this.args = args;
      isMultiArg = multiArg;
    }

    public ArgInfo(ArgType arg) {
      this.args = Collections.singletonList(arg);
      this.isMultiArg = false;
    }

    public ArgInfo(boolean isMultiArg) {
      this.args = Collections.emptyList();
      this.isMultiArg = isMultiArg;
    }

    public static <ArgType> ArgInfo<ArgType>[] empty_array() {
      return EMPTY_ARRAY;
    }
  }

  private static class InnerArg {
    List<PsiElement> list;
    PsiType type;

    InnerArg(PsiType type, PsiElement... elements) {
      this.list = new ArrayList<PsiElement>(Arrays.asList(elements));
      this.type = type;
    }
  }

  @Nullable
  public static Map<GrExpression, Pair<PsiParameter, PsiType>> mapArgumentsToParameters(@NotNull GroovyResolveResult resolveResult,
                                                                                        @NotNull GroovyPsiElement context,
                                                                                        final boolean partial,
                                                                                        @NotNull final GrNamedArgument[] namedArgs,
                                                                                        @NotNull final GrExpression[] expressionArgs,
                                                                                        @NotNull GrClosableBlock[] closureArguments) {
    final GrClosureSignature signature;
    final PsiParameter[] parameters;
    final PsiElement element = resolveResult.getElement();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (element instanceof PsiMethod) {
      signature = createSignature((PsiMethod)element, substitutor);
      parameters = ((PsiMethod)element).getParameterList().getParameters();
    }
    else if (element instanceof GrClosableBlock) {
      signature = createSignature((GrClosableBlock)element);
      parameters = ((GrClosableBlock)element).getAllParameters();
    }
    else {
      return null;
    }

    final ArgInfo<PsiElement>[] argInfos = mapParametersToArguments(signature, namedArgs, expressionArgs, context, closureArguments, partial);
    if (argInfos == null) {
      return null;
    }

    final HashMap<GrExpression, Pair<PsiParameter, PsiType>> result = new HashMap<GrExpression, Pair<PsiParameter, PsiType>>();
    for (int i = 0; i < argInfos.length; i++) {
      ArgInfo<PsiElement> info = argInfos[i];
      for (PsiElement arg : info.args) {
        if (arg instanceof GrNamedArgument) {
          arg = ((GrNamedArgument)arg).getExpression();
        }
        final GrExpression expression = (GrExpression)arg;
        PsiType type = parameters[i].getType();
        if (info.isMultiArg && type instanceof PsiArrayType) {
          type = ((PsiArrayType)type).getComponentType();
        }
        result.put(expression, Pair.create(parameters[i], substitutor.substitute(type)));
      }
    }

    return result;
  }


  @Nullable
  public static ArgInfo<PsiElement>[] mapParametersToArguments(@NotNull GrClosureSignature signature,
                                                               @Nullable GrArgumentList list,
                                                               @NotNull GroovyPsiElement context,
                                                               @NotNull GrClosableBlock[] closureArguments) {
    return mapParametersToArguments(signature, list, context, closureArguments, false);
  }

  @Nullable
  public static ArgInfo<PsiElement>[] mapParametersToArguments(@NotNull GrClosureSignature signature,
                                                               @Nullable GrArgumentList list,
                                                               @NotNull GroovyPsiElement context,
                                                               @NotNull GrClosableBlock[] closureArguments, final boolean partial) {
    final GrNamedArgument[] namedArgs = list == null ? GrNamedArgument.EMPTY_ARRAY : list.getNamedArguments();
    final GrExpression[] expressionArgs = list == null ? GrExpression.EMPTY_ARRAY : list.getExpressionArguments();
    return mapParametersToArguments(signature, namedArgs, expressionArgs, context, closureArguments, partial);
  }

  @Nullable
  public static ArgInfo<PsiElement>[] mapParametersToArguments(@NotNull GrClosureSignature signature,
                                                               @NotNull GrNamedArgument[] namedArgs,
                                                               @NotNull GrExpression[] expressionArgs,
                                                               @NotNull GroovyPsiElement context,
                                                               @NotNull GrClosableBlock[] closureArguments,
                                                               final boolean partial) {
    List<InnerArg> innerArgs = new ArrayList<InnerArg>();

    boolean hasNamedArgs = namedArgs.length > 0;
    GrClosureParameter[] params = signature.getParameters();

    if (hasNamedArgs) {
      if (params.length == 0) return null;
      PsiType type = params[0].getType();
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        innerArgs.add(new InnerArg(new GrMapType(context.getResolveScope()), namedArgs));
      }
      else {
        return null;
      }
    }

    for (GrExpression expression : expressionArgs) {
      PsiType type = expression.getType();
      if (expression instanceof GrNewExpression && com.intellij.psi.util.PsiUtil.resolveClassInType(type) == null) {
        type = null;
      }
      type = TypeConversionUtil.erasure(type);
      innerArgs.add(new InnerArg(type, expression));
    }

    for (GrClosableBlock closureArgument : closureArguments) {
      innerArgs.add(new InnerArg(TypeConversionUtil.erasure(closureArgument.getType()), closureArgument));
    }

    final ArgInfo<InnerArg>[] innerMap = mapParametersToArguments(signature, innerArgs.toArray(new InnerArg[innerArgs.size()]), new Function<InnerArg, PsiType>() {
        @Override
        public PsiType fun(InnerArg o) {
          return o.type;
        }
      }, context, partial);
    if (innerMap == null) return null;

    ArgInfo<PsiElement>[] map = new ArgInfo[innerMap.length];
    int i = 0;
    if (hasNamedArgs) {
      map[i] = new ArgInfo<PsiElement>(innerMap[i].args.iterator().next().list, true);
      i++;
    }

    for (; i < innerMap.length; i++) {
      final ArgInfo<InnerArg> innerArg = innerMap[i];
      List<PsiElement> argList = new ArrayList<PsiElement>();
      for (InnerArg arg : innerArg.args) {
        argList.addAll(arg.list);
      }
      boolean multiArg = innerArg.isMultiArg || argList.size() > 1;
      map[i] = new ArgInfo<PsiElement>(argList, multiArg);
    }

    return map;
  }

  public static List<MethodSignature> generateAllSignaturesForMethod(GrMethod method, PsiSubstitutor substitutor) {
    GrClosureSignature signature = createSignature(method, substitutor);
    String name = method.getName();
    PsiTypeParameter[] typeParameters = method.getTypeParameters();

    return generateAllMethodSignaturesByClosureSignature(name, signature, typeParameters, substitutor);
  }

  public static MultiMap<MethodSignature, PsiMethod> findMethodSignatures(PsiMethod[] methods) {
    MultiMap<MethodSignature, PsiMethod> map = new MultiMap<MethodSignature, PsiMethod>();
    for (PsiMethod method : methods) {
      final PsiMethod actual = method instanceof GrReflectedMethod ? ((GrReflectedMethod)method).getBaseMethod() : method;
      map.putValue(method.getSignature(PsiSubstitutor.EMPTY), actual);
    }

    return map;
  }

  private static MethodSignature generateSignature(String name,
                                                   List<PsiType> paramTypes,
                                                   PsiTypeParameter[] typeParameters,
                                                   PsiSubstitutor substitutor) {
    return MethodSignatureUtil.createMethodSignature(name, paramTypes.toArray(new PsiType[paramTypes.size()]), typeParameters, substitutor);
  }

  public static List<MethodSignature> generateAllMethodSignaturesByClosureSignature(@NotNull String name,
                                                                                     @NotNull GrClosureSignature signature,
                                                                                     @NotNull PsiTypeParameter[] typeParameters,
                                                                                     @NotNull PsiSubstitutor substitutor) {
    GrClosureParameter[] params = signature.getParameters();

    ArrayList<PsiType> newParams = new ArrayList<PsiType>(params.length);
    ArrayList<GrClosureParameter> opts = new ArrayList<GrClosureParameter>(params.length);
    ArrayList<Integer> optInds = new ArrayList<Integer>(params.length);

    for (int i = 0; i < params.length; i++) {
      if (params[i].isOptional()) {
        opts.add(params[i]);
        optInds.add(i);
      }
      else {
        newParams.add(params[i].getType());
      }
    }

    List<MethodSignature> result = new ArrayList<MethodSignature>(opts.size() + 1);
    result.add(generateSignature(name, newParams, typeParameters, substitutor));
    for (int i = 0; i < opts.size(); i++) {
      newParams.add(optInds.get(i), opts.get(i).getType());
      result.add(generateSignature(name, newParams, typeParameters, substitutor));
    }
    return result;
  }

  public static List<MethodSignature> generateAllMethodSignaturesByClosureSignature(@NotNull String name,
                                                                                     @NotNull GrClosureSignature signature) {
    return generateAllMethodSignaturesByClosureSignature(name, signature, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
  }

  @Nullable
  public static PsiType getTypeByTypeArg(ArgInfo<PsiType> arg, PsiManager manager, GlobalSearchScope resolveScope) {
    if (arg.isMultiArg) {
      if (arg.args.size() == 0) return PsiType.getJavaLangObject(manager, resolveScope).createArrayType();
      PsiType leastUpperBound = null;

      for (PsiType type : arg.args) {
        leastUpperBound = TypesUtil.getLeastUpperBoundNullable(leastUpperBound, type, manager);
      }
      if (leastUpperBound == null) return null;
      return leastUpperBound.createArrayType();
    }
    else {
      if (arg.args.size() > 0) return arg.args.get(0);
      return null;
    }
  }

  @Nullable
  public static PsiType getTypeByArg(ArgInfo<PsiElement> arg, PsiManager manager, GlobalSearchScope resolveScope) {
    if (arg.isMultiArg) {
      if (arg.args.size() == 0) return PsiType.getJavaLangObject(manager, resolveScope).createArrayType();
      PsiType leastUpperBound = null;
      PsiElement first = arg.args.get(0);
      if (first instanceof GrNamedArgument) {
        GrNamedArgument[] args=new GrNamedArgument[arg.args.size()];
        for (int i = 0, size = arg.args.size(); i < size; i++) {
          args[i] = (GrNamedArgument)arg.args.get(i);
        }
        return new GrMapType(first, args);
      }
      else {
        for (PsiElement elem : arg.args) {
          if (elem instanceof GrExpression) {
            leastUpperBound = TypesUtil.getLeastUpperBoundNullable(leastUpperBound, ((GrExpression)elem).getType(), manager);
          }
        }
        if (leastUpperBound == null) return null;
        return leastUpperBound.createArrayType();
      }
    }
    else {
      if (arg.args.size() == 0) return null;
      PsiElement elem = arg.args.get(0);
      if (elem instanceof GrExpression) {
        return ((GrExpression)elem).getType();
      }
      return null;
    }
  }

}
