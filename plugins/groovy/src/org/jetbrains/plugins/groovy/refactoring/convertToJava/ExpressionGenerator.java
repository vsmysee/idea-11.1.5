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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.conversions.ConvertGStringToStringIntention;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrArrayTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SubtypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrLiteralClassType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrRangeType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock.EMPTY_ARRAY;
import static org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil.*;

/**
 * @author Maxim.Medvedev
 */
public class ExpressionGenerator extends Generator {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.ExpressionGenerator");

  private StringBuilder builder;
  private GroovyPsiElementFactory factory;

  private final ExpressionContext context;

  public ExpressionGenerator(StringBuilder builder, ExpressionContext context) {
    this.builder = builder;
    this.context = context;

    factory = GroovyPsiElementFactory.getInstance(context.project);
  }

  public ExpressionGenerator(ExpressionContext context) {
    this.builder = new StringBuilder();
    this.context = context;

    factory = GroovyPsiElementFactory.getInstance(context.project);
  }

  @Override
  public StringBuilder getBuilder() {
    return builder;
  }

  @Override
  public ExpressionContext getContext() {
    return context;
  }

  @Override
  public void visitClosure(GrClosableBlock closure) {
    new ClosureGenerator(builder, context).generate(closure);
  }


  public void visitExpression(GrExpression expression) {
    LOG.error("this method should not be invoked");
  }

  @Override
  public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
    generateMethodCall(methodCallExpression);
  }

  private void generateMethodCall(GrMethodCall methodCallExpression) {
    final GrExpression invoked = methodCallExpression.getInvokedExpression();
    final GrExpression[] exprs = methodCallExpression.getExpressionArguments();
    final GrNamedArgument[] namedArgs = methodCallExpression.getNamedArguments();
    final GrClosableBlock[] clArgs = methodCallExpression.getClosureArguments();
    if (invoked instanceof GrReferenceExpression) {
      final GroovyResolveResult resolveResult = ((GrReferenceExpression)invoked).advancedResolve();
      final PsiElement resolved = resolveResult.getElement();
      if (resolved instanceof PsiMethod) {
        final GrExpression qualifier = ((GrReferenceExpression)invoked).getQualifier();//todo replace null-qualifier with this-reference

        invokeMethodOn(
          ((PsiMethod)resolved),
          qualifier,
          exprs, namedArgs, clArgs,
          resolveResult.getSubstitutor(),
          methodCallExpression
        );
        return;
      }
      else if (resolved instanceof GrReferenceExpression || resolved == null) {
        final GrExpression qualifier = ((GrReferenceExpression)invoked).getQualifier();
        final GrExpression[] args =
          generateArgsForInvokeMethod(((GrReferenceExpression)invoked).getReferenceName(), exprs, namedArgs, clArgs, methodCallExpression);
        invokeMethodByName(qualifier, "invokeMethod", args, GrNamedArgument.EMPTY_ARRAY, EMPTY_ARRAY, this, methodCallExpression);
        return;
      }
    }

    invokeMethodByName(invoked, "call", exprs, namedArgs, clArgs, this, methodCallExpression);
  }

  private GrExpression[] generateArgsForInvokeMethod(String name,
                                                     GrExpression[] exprs,
                                                     GrNamedArgument[] namedArgs,
                                                     GrClosableBlock[] clArgs,
                                                     GroovyPsiElement psiContext) {
    GrExpression[] result = new GrExpression[2];
    result[0] = factory.createExpressionFromText("\"" + name + "\"");
    StringBuilder builder = new StringBuilder();
    builder.append('[');
    if (namedArgs.length > 0) {
      builder.append('[');
      for (GrNamedArgument namedArg : namedArgs) {
        builder.append(namedArg.getText()).append(',');
      }
      builder.delete(builder.length()-1, builder.length());
      //builder.removeFromTheEnd(1);
      builder.append("],");
    }
    for (GrExpression expr : exprs) {
      builder.append(expr.getText()).append(',');
    }

    for (GrClosableBlock clArg : clArgs) {
      builder.append(clArg.getText()).append(',');
    }
    if (namedArgs.length + exprs.length + clArgs.length > 0) builder.delete(builder.length()-1, builder.length());
    //if (namedArgs.length + exprs.length + clArgs.length > 0) builder.removeFromTheEnd(1);
    builder.append("] as Object[]");

    result[1] = factory.createExpressionFromText(builder.toString(), psiContext);
    return result;
  }

  @Override
  public void visitNewExpression(GrNewExpression newExpression) {
    boolean hasFieldInitialization = hasFieldInitialization(newExpression);
    StringBuilder builder;

    final PsiType type = newExpression.getType();
    final String varName;
    if (hasFieldInitialization) {
      builder = new StringBuilder();
      varName = suggestVarName(type, newExpression, context);
      writeType(builder, type, newExpression);
      builder.append(' ').append(varName).append(" = ");
    }
    else {
      varName = null;
      builder = this.builder;
    }

    final GrExpression qualifier = newExpression.getQualifier();
    if (qualifier != null) {
      qualifier.accept(this);
      builder.append('.');
    }

    final GrTypeElement typeElement = newExpression.getTypeElement();
    final GrArrayDeclaration arrayDeclaration = newExpression.getArrayDeclaration();
    final GrCodeReferenceElement referenceElement = newExpression.getReferenceElement();

    builder.append("new ");
    if (typeElement != null) {
      final PsiType builtIn = typeElement.getType();
      LOG.assertTrue(builtIn instanceof PsiPrimitiveType);
      final PsiType boxed = TypesUtil.boxPrimitiveType(builtIn, newExpression.getManager(), newExpression.getResolveScope());
      writeType(builder, boxed, newExpression);
    }
    else if (referenceElement != null) {
      writeCodeReferenceElement(builder, referenceElement);
    }

    final GrArgumentList argList = newExpression.getArgumentList();
    if (argList != null) {

      GrClosureSignature signature = null;

      final GroovyResolveResult resolveResult = newExpression.resolveConstructorGenerics();
      final PsiElement constructor = resolveResult.getElement();
      if (constructor instanceof PsiMethod) {
        signature = GrClosureSignatureUtil.createSignature((PsiMethod)constructor, resolveResult.getSubstitutor());
      }
      else if (referenceElement != null) {
        final GroovyResolveResult clazzResult = referenceElement.advancedResolve();
        final PsiElement clazz = clazzResult.getElement();
        if (clazz instanceof PsiClass && ((PsiClass)clazz).getConstructors().length == 0) {
          signature = GrClosureSignatureUtil.createSignature(PsiParameter.EMPTY_ARRAY, null);
        }
      }

      final GrNamedArgument[] namedArgs = hasFieldInitialization ? GrNamedArgument.EMPTY_ARRAY : argList.getNamedArguments();
      new ArgumentListGenerator(builder, context).generate(
        signature,
        argList.getExpressionArguments(),
        namedArgs,
        EMPTY_ARRAY,
        newExpression
      );
    }

    final GrAnonymousClassDefinition anonymous = newExpression.getAnonymousClassDefinition();
    if (anonymous != null) {
      writeTypeBody(builder, anonymous);
    }

    if (arrayDeclaration != null) {
      final GrExpression[] boundExpressions = arrayDeclaration.getBoundExpressions();
      for (GrExpression boundExpression : boundExpressions) {
        builder.append('[');
        boundExpression.accept(this);
        builder.append(']');
      }
      if (boundExpressions.length == 0) {
        builder.append("[]");
      }
    }

    if (hasFieldInitialization) {
      builder.append(';');
      context.myStatements.add(builder.toString());
      final GrNamedArgument[] namedArguments = argList.getNamedArguments();
      for (GrNamedArgument namedArgument : namedArguments) {
        final String fieldName = namedArgument.getLabelName();
        if (fieldName == null) {
          //todo try to initialize field
          final GrArgumentLabel label = namedArgument.getLabel();
          LOG.info("cannot initialize field " + (label == null ? "<null>" : label.getText()));
        }
        else {
          final GroovyResolveResult resolveResult = referenceElement.advancedResolve();
          final PsiElement resolved = resolveResult.getElement();
          LOG.assertTrue(resolved instanceof PsiClass);
          initializeField(varName, type, ((PsiClass)resolved), resolveResult.getSubstitutor(), fieldName, namedArgument.getExpression());
        }
      }
    }
  }

  private void initializeField(String varName,
                               PsiType type,
                               PsiClass resolved,
                               PsiSubstitutor substitutor,
                               String fieldName,
                               GrExpression expression) {
    StringBuilder builder = new StringBuilder();
    final PsiMethod setter = GroovyPropertyUtils.findPropertySetter(resolved, fieldName, false, true);
    if (setter != null) {
      final GrVariableDeclaration var = factory.createVariableDeclaration(ArrayUtil.EMPTY_STRING_ARRAY, null, type, varName);
      final GrReferenceExpression caller = factory.createReferenceExpressionFromText(varName, var);
      invokeMethodOn(setter, caller, new GrExpression[]{expression}, GrNamedArgument.EMPTY_ARRAY, EMPTY_ARRAY, substitutor,
                     expression);
    }
    else {
      builder.append(varName).append('.').append(fieldName).append(" = ");
      expression.accept(new ExpressionGenerator(builder, context));
    }
    context.myStatements.add(builder.toString());
  }

  @Override
  public String toString() {
    return builder.toString();
  }

  private static boolean hasFieldInitialization(GrNewExpression newExpression) {
    final GrArgumentList argumentList = newExpression.getArgumentList();
    if (argumentList == null) return false;
    if (argumentList.getNamedArguments().length == 0) return false;

    final GrCodeReferenceElement refElement = newExpression.getReferenceElement();
    if (refElement == null) return false;
    final GroovyResolveResult resolveResult = newExpression.resolveConstructorGenerics();
    final PsiElement constructor = resolveResult.getElement();
    if (constructor instanceof PsiMethod) {
      return ((PsiMethod)constructor).getParameterList().getParametersCount() == 0;
    }

    final PsiElement resolved = refElement.resolve();
    return resolved instanceof PsiClass;
  }

  private void writeTypeBody(StringBuilder builder, GrAnonymousClassDefinition anonymous) {
    builder.append("{\n");
    final GeneratorClassNameProvider classNameProvider = new GeneratorClassNameProvider();
    final ClassItemGeneratorImpl classItemGenerator = new ClassItemGeneratorImpl(context.extend());
    new ClassGenerator(classNameProvider, classItemGenerator).writeMembers(builder, anonymous, true);
    builder.append('}');
  }

  @Override
  public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
    generateMethodCall(applicationStatement);
  }


  @Override
  public void visitConditionalExpression(GrConditionalExpression expression) {
    final GrExpression condition = expression.getCondition();
    final GrExpression thenBranch = expression.getThenBranch();
    final GrExpression elseBranch = expression.getElseBranch();

    final boolean elvis = expression instanceof GrElvisExpression;
    final String var;
    if (elvis) {
      var = createVarByInitializer(condition);
    }
    else {
      var = null;
    }
    final PsiType type = condition.getType();
    if (type == null || TypesUtil.unboxPrimitiveTypeWrapper(type) == PsiType.BOOLEAN) {
      if (elvis) {
        builder.append(var);
      }
      else {
        condition.accept(this);
      }
    }
    else {
      final GroovyResolveResult[] results = ResolveUtil.getMethodCandidates(type, "asBoolean", expression, PsiType.EMPTY_ARRAY);
      final GroovyResolveResult result = PsiImplUtil.extractUniqueResult(results);
      GenerationUtil.invokeMethodByResolveResult(
        elvis ? factory.createReferenceExpressionFromText(var, expression) : condition,
        result,
        "asBoolean",
        GrExpression.EMPTY_ARRAY, GrNamedArgument.EMPTY_ARRAY, EMPTY_ARRAY,
        this,
        expression
      );
    }

    builder.append('?');
    if (thenBranch != null) {
      if (elvis) {
        builder.append(var);
      }
      else {
        thenBranch.accept(this);
      }
    }

    builder.append(':');
    if (elseBranch != null) {
      elseBranch.accept(this);
    }
  }

  /**
   * x= expr ->
   *   x = expr
   *   x.set(expr)[: x.get()]
   * x+= expr ->
   *   x+=expr
   *   x= plus(x, expr)
   *   x.set(plus(x, expr))[expr]
   * x[a] = 4 ->
   *   x[a] = 4
   *   x.putAt(a, 4) [4]
   */
  @Override
  public void visitAssignmentExpression(GrAssignmentExpression expression) {
    final GrExpression lValue = expression.getLValue();
    GrExpression rValue = expression.getRValue();
    final IElementType token = expression.getOperationToken();

    PsiElement realLValue = PsiUtil.skipParentheses(lValue, false);
    if (realLValue instanceof GrReferenceExpression && rValue != null) {
      GroovyResolveResult resolveResult = ((GrReferenceExpression)realLValue).advancedResolve();
      PsiElement resolved = resolveResult.getElement();
      if (resolved instanceof GrVariable && context.analyzedVars.toWrap((GrVariable)resolved)) {
        //write assignment to wrapped local var
        writeAssignmentWithRefSetter((GrExpression)realLValue, expression);
        return;
      }
      else if (resolved instanceof PsiMethod && resolveResult.isInvokedOnProperty()) {
        //write assignment via setter
        writeAssignmentWithSetter(((GrReferenceExpression)realLValue).getQualifier(), (PsiMethod)resolved, expression);
        return;
      }
      else if (resolved == null || resolved instanceof GrReferenceExpression) {
        //write unresolved reference assignment via setter GroovyObject.setProperty(String name, Object value)
        final GrExpression qualifier = ((GrReferenceExpression)realLValue).getQualifier();
        final PsiType type = qualifier != null ? qualifier.getType() : factory.createExpressionFromText("this", expression).getType();

        final GrExpression[] args = new GrExpression[2];
        args[0] = factory.createExpressionFromText("\"" + ((GrReferenceExpression)realLValue).getReferenceName() + "\"");
        args[1] = getRValue(expression);
        final PsiElement method = PsiImplUtil
          .extractUniqueElement(ResolveUtil.getMethodCandidates(type, "setProperty", expression, args[0].getType(), args[1].getType()));

        if (method instanceof PsiMethod) {
          writeAssignmentWithSetter(qualifier, (PsiMethod)method, args, GrNamedArgument.EMPTY_ARRAY, EMPTY_ARRAY, PsiSubstitutor.EMPTY,
                                    expression);
          return;
        }
      }
    }
    else if (realLValue instanceof GrIndexProperty) {   //qualifier[args] = rValue
      //write assignment via qualifier.putAt(Args, Value) method
      final GroovyResolveResult result = PsiImplUtil.extractUniqueResult(((GrIndexProperty)realLValue).multiResolve(false));
      final PsiElement resolved = result.getElement();
      if (resolved instanceof PsiMethod) {
        final GrExpression[] args = ((GrIndexProperty)realLValue).getArgumentList().getExpressionArguments();
        writeAssignmentWithSetter(((GrIndexProperty)realLValue).getSelectedExpression(), (PsiMethod)resolved,
                                  ArrayUtil.append(args, getRValue(expression)), GrNamedArgument.EMPTY_ARRAY, EMPTY_ARRAY,
                                  result.getSubstitutor(), expression);
        return;
      }
    }

    final PsiType lType = GenerationUtil.getDeclaredType(lValue, context);

    if (token == mASSIGN) {
      //write simple assignment
      lValue.accept(this);
      builder.append(" = ");

      final PsiType rType = GenerationUtil.getDeclaredType(rValue, context);
      if (lType != null && rType != null && !TypesUtil.isAssignableByMethodCallConversion(lType, rType, expression)) {
        builder.append('(');
        GenerationUtil.writeType(builder, lType, expression);
        builder.append(')');
      }
      if (rValue != null) {
        rValue.accept(this);
      }
    }
    else {
      //write assignment such as +=, -=, etc
      final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(expression.multiResolve(false));
      final PsiElement resolved = resolveResult.getElement();

      if (resolved instanceof PsiMethod && !shouldNotReplaceOperatorWithMethod(lValue.getType(), rValue, expression.getOperationToken())) {
        lValue.accept(this);
        builder.append(" = ");

        final PsiType rType = GenerationUtil.getDeclaredType((PsiMethod)resolved, resolveResult.getSubstitutor(), context);
        if (lType != null && rType != null && !TypesUtil.isAssignableByMethodCallConversion(lType, rType, expression)) {
          builder.append('(');
          GenerationUtil.writeType(builder, lType, expression);
          builder.append(')');
        }
        invokeMethodOn(
          ((PsiMethod)resolved),
          (GrExpression)lValue.copy(),
          rValue == null ? GrExpression.EMPTY_ARRAY : new GrExpression[]{rValue},
          GrNamedArgument.EMPTY_ARRAY,
          EMPTY_ARRAY,
          resolveResult.getSubstitutor(),
          expression
        );
      }
      else {
        writeSimpleBinaryExpression(expression.getOpToken(), lValue, rValue);
      }
    }
  }

  private void writeAssignmentWithRefSetter(GrExpression ref, GrAssignmentExpression expression) {
    GrExpression rValue = getRValue(expression);

    if (PsiUtil.isExpressionUsed(expression)) {
      LOG.assertTrue(context.getRefSetterName(expression) != null);
      builder.append(context.getRefSetterName(expression)).append('(');
      ref.accept(this);
      builder.append(", ");
      if (rValue != null) {
        rValue.accept(this);
      }
      builder.append(')');
    }
    else {
      ref.accept(this);
      builder.append(".set(");
      if (rValue != null) {
        rValue.accept(this);
      }
      builder.append(')');
    }
  }

  /**
   * returns rValue         for lValue =  expr
   * lValue+Rvalue  for lValue += rValue
   */
  @Nullable
  private GrExpression getRValue(GrAssignmentExpression expression) {
    GrExpression rValue = expression.getRValue();
    if (rValue == null) return null;

    GrExpression lValue = expression.getLValue();
    IElementType opToken = expression.getOperationToken();
    if (opToken == GroovyTokenTypes.mASSIGN) return rValue;
    Pair<String, IElementType> pair = GenerationUtil.getBinaryOperatorType(opToken);
    LOG.assertTrue(pair != null);

    return factory.createExpressionFromText(lValue.getText() + pair.getFirst() + rValue.getText(), expression);
  }

  private void writeAssignmentWithSetter(GrExpression qualifier, PsiMethod setter, GrAssignmentExpression assignment) {
    GrExpression rValue = getRValue(assignment);
    LOG.assertTrue(rValue != null);
    writeAssignmentWithSetter(qualifier, setter, new GrExpression[]{rValue}, GrNamedArgument.EMPTY_ARRAY, EMPTY_ARRAY, PsiSubstitutor.EMPTY,
                              assignment);
  }

  private void writeAssignmentWithSetter(@Nullable GrExpression qualifier,
                                         PsiMethod method,
                                         GrExpression[] exprs,
                                         GrNamedArgument[] namedArgs,
                                         GrClosableBlock[] closures,
                                         PsiSubstitutor substitutor,
                                         GrAssignmentExpression assignment) {
    if (PsiUtil.isExpressionUsed(assignment)) {
      String setterName = context.getSetterName(method, assignment);
      GrExpression[] args;
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        args = exprs;
      }
      else {
        args = new GrExpression[exprs.length + 1];
        if (qualifier == null) {
          qualifier = factory.createExpressionFromText("this", assignment);
        }
        args[0] = qualifier;
        System.arraycopy(exprs, 0, args, 1, exprs.length);
      }
      invokeMethodByName(null, setterName, args, namedArgs, closures, this, assignment);
    }
    else {
      invokeMethodOn(method, qualifier, exprs, namedArgs, closures, substitutor, assignment);
    }
  }

  @Override
  public void visitBinaryExpression(GrBinaryExpression expression) {
    final GrExpression left = expression.getLeftOperand();
    GrExpression right = expression.getRightOperand();
    final PsiType ltype = left.getType();
    final PsiElement token = expression.getOperationToken();
    final IElementType op = expression.getOperationTokenType();

    if (op == mREGEX_FIND) {
      builder.append(GroovyCommonClassNames.JAVA_UTIL_REGEX_PATTERN).append(".compile(");
      if (right != null) {
        right.accept(this);
      }
      builder.append(").matcher(");
      left.accept(this);
      builder.append(')');
      return;
    }
    if (op == mREGEX_MATCH) {
      builder.append(GroovyCommonClassNames.JAVA_UTIL_REGEX_PATTERN).append(".matches(");
      if (right != null) {
        right.accept(this);
      }
      builder.append(", ");
      left.accept(this);
      builder.append(')');
      return;
    }
    if (shouldNotReplaceOperatorWithMethod(ltype, right, op)) {
      writeSimpleBinaryExpression(token, left, right);
      return;
    }

    final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(expression.multiResolve(false));
    final PsiElement resolved = resolveResult.getElement();
    if (resolved instanceof PsiMethod) {
      if (right == null) {
        right = factory.createExpressionFromText("null");
      }
      invokeMethodOn(
        ((PsiMethod)resolved),
        left,
        new GrExpression[]{right},
        GrNamedArgument.EMPTY_ARRAY,
        EMPTY_ARRAY,
        resolveResult.getSubstitutor(),
        expression
      );
      if (op == mGE) {
        builder.append(" >= 0");
      }
      else if (op == mGT) {
        builder.append(" > 0");
      }
      else if (op == mLT) {
        builder.append(" < 0");
      }
      else if (op == mLE) builder.append(" <= 0");
    }
    else {
      writeSimpleBinaryExpression(token, left, right);
    }
  }

  private static boolean shouldNotReplaceOperatorWithMethod(PsiType ltype, @Nullable GrExpression right, IElementType op) {
    return GenerationSettings.dontReplaceOperatorsWithMethodsForNumbers &&
           (TypesUtil.isNumericType(ltype) && (right == null || TypesUtil.isNumericType(right.getType())) ||
            (op == mPLUS || op == mPLUS_ASSIGN) && ltype != null && TypesUtil.isClassType(ltype, CommonClassNames.JAVA_LANG_STRING));
  }

  private void writeSimpleBinaryExpression(PsiElement opToken, GrExpression left, GrExpression right) {
    left.accept(this);
    builder.append(' ');
    builder.append(opToken.getText());
    if (right != null) {
      builder.append(' ');
      right.accept(this);
    }
  }

  @Override
  public void visitUnaryExpression(GrUnaryExpression expression) {
    final boolean postfix = expression instanceof GrPostfixExpression;

    final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(expression.multiResolve(false));
    final PsiElement resolved = resolveResult.getElement();
    final GrExpression operand = expression.getOperand();

    IElementType opType = expression.getOperationTokenType();

    if (resolved instanceof PsiMethod) {
      if (opType == mLNOT) {
        builder.append('!');
      }
      else if (opType == mINC || opType == mDEC) {
        if (!postfix || expression.getParent() instanceof GrStatementOwner || expression.getParent() instanceof GrControlStatement) {
          if (generatePrefixIncDec((PsiMethod)resolved, operand, expression)) return;
        }
      }

      if (operand != null && shouldNotReplaceOperatorWithMethod(operand.getType(), null, expression.getOperationTokenType())) {
        writeSimpleUnary(operand, expression, this);
      }
      else {
        invokeMethodOn(
          ((PsiMethod)resolved),
          operand,
          GrExpression.EMPTY_ARRAY,
          GrNamedArgument.EMPTY_ARRAY,
          EMPTY_ARRAY,
          resolveResult.getSubstitutor(),
          expression
        );
      }
    }
    else if (operand != null) {
      if (postfix) {
        operand.accept(this);
        builder.append(expression.getOperationToken().getText());
      }
      else {
        builder.append(expression.getOperationToken().getText());
        operand.accept(this);
      }
    }
  }

  private boolean generatePrefixIncDec(PsiMethod method,
                                       GrExpression operand,
                                       GrUnaryExpression unary) {
    if (!(operand instanceof GrReferenceExpression)) return false;

    final GrExpression qualifier = ((GrReferenceExpression)operand).getQualifier();
    final GroovyResolveResult resolveResult = ((GrReferenceExpression)operand).advancedResolve();
    final PsiElement resolved = resolveResult.getElement();
    if (resolved instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)resolved)) {
      final PsiMethod getter = (PsiMethod)resolved;
      final String propertyName = GroovyPropertyUtils.getPropertyNameByGetter(getter);
      final PsiType type;
      if (qualifier == null) {
        type = null;
      }
      else {
        type = qualifier.getType();
        if (type == null) return false;
      }
      final PsiMethod setter = GroovyPropertyUtils.findPropertySetter(type, propertyName, unary);
      if (setter == null) return false;

      final ExpressionGenerator generator = new ExpressionGenerator(new StringBuilder(), context);

      if (shouldNotReplaceOperatorWithMethod(operand.getType(), null, unary.getOperationTokenType())) {
        writeSimpleUnary(operand, unary, generator);
      }
      else {
        generator.invokeMethodOn(
          method,
          operand,
          GrExpression.EMPTY_ARRAY, GrNamedArgument.EMPTY_ARRAY, EMPTY_ARRAY,
          resolveResult.getSubstitutor(),
          unary
        );
      }
      final GrExpression fromText = factory.createExpressionFromText(generator.toString(), unary);
      invokeMethodOn(
        setter,
        qualifier,
        new GrExpression[]{fromText}, GrNamedArgument.EMPTY_ARRAY, EMPTY_ARRAY,
        resolveResult.getSubstitutor(),
        unary
      );
    }
    else if (resolved instanceof PsiVariable) {
      boolean wrap = context.analyzedVars.toWrap((PsiVariable)resolved);
      boolean doNeedExpression = PsiUtil.isExpressionUsed(unary);
      StringBuilder curBuilder;
      ExpressionGenerator curGenerator;
      if (doNeedExpression && wrap) {
        curBuilder = new StringBuilder();
        curGenerator = new ExpressionGenerator(curBuilder, context);
      }
      else {
        curBuilder = builder;
        curGenerator = this;
      }


      boolean shouldInsertParentheses = !wrap && doNeedExpression;
      if (shouldInsertParentheses) {
        curBuilder.append('(');
      }

      operand.accept(curGenerator);
      if (wrap) {
        curBuilder.append(".set(");
      }
      else {
        curBuilder.append(" = ");
      }
      if (shouldNotReplaceOperatorWithMethod(operand.getType(), null, unary.getOperationTokenType())) {
        writeSimpleUnary((GrExpression)operand.copy(), unary, curGenerator);
      }
      else {
        curGenerator.invokeMethodOn(
          method,
          (GrExpression)operand.copy(),
          GrExpression.EMPTY_ARRAY, GrNamedArgument.EMPTY_ARRAY, EMPTY_ARRAY,
          resolveResult.getSubstitutor(),
          unary
        );
      }
      if (shouldInsertParentheses) {
        curBuilder.append(')');
      }
      if (wrap) {
        curBuilder.append(')');
        if (doNeedExpression) {
          curBuilder.append(';');
          context.myStatements.add(curBuilder.toString());
          operand.accept(this);
          builder.append(".get()");
        }
      }
    }
    return true;
  }

  private static void writeSimpleUnary(GrExpression operand, GrUnaryExpression unary, ExpressionGenerator generator) {
    String opTokenText = unary.getOperationToken().getText();
    boolean isPrefix = !(unary instanceof GrPostfixExpression);
    if (isPrefix) {
      generator.getBuilder().append(opTokenText);
    }
    operand.accept(generator);
    if (!isPrefix) {
      generator.getBuilder().append(opTokenText);
    }
  }

  @Override
  //doesn't visit GrString and regexps
  public void visitLiteralExpression(GrLiteral literal) {
    final TypeConstraint[] constraints = GroovyExpectedTypesProvider.calculateTypeConstraints(literal);

    final String text = literal.getText();
    final Object value;
    if (text.startsWith("'''") || text.startsWith("\"\"\"")) {
      String string = ((String)literal.getValue());
      LOG.assertTrue(string != null);
      string = string.replace("\n", "\\n").replace("\r", "\\r");
      value = string;
    }
    else {
      value = literal.getValue();
    }

    boolean isChar = false;
    for (TypeConstraint constraint : constraints) {
      if (constraint instanceof SubtypeConstraint && TypesUtil.unboxPrimitiveTypeWrapper(constraint.getDefaultType()) == PsiType.CHAR) {
        isChar = true;
      }
    }
    if (isChar) {
      builder.append('\'').append(StringUtil.escapeQuotes(String.valueOf(value))).append('\'');
    }
    else if (value instanceof String) {
      builder.append('"').append(StringUtil.escapeQuotes((String)value)).append('"');
    }
    else {
      builder.append(value);
    }
  }

  @Override
  public void visitGStringExpression(GrString gstring) {
    final String newExprText = ConvertGStringToStringIntention.convertGStringLiteralToStringLiteral(gstring);
    final GrExpression newExpr = factory.createExpressionFromText(newExprText, gstring);
    newExpr.accept(this);
  }

  @Override
  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    final GrExpression qualifier = referenceExpression.getQualifier();
    final GroovyResolveResult resolveResult = referenceExpression.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();

    //class name used as expression. Should be converted to <className>.class
    if (resolved instanceof PsiClass && PsiUtil.isExpressionUsed(referenceExpression)) {
      builder.append(((PsiClass)resolved).getQualifiedName());
      builder.append(".class");
      return;
    }

    //don't try to resolve local vars that are provided my this generator (they are listed in myUsedVarNames)
    if (resolved == null && qualifier == null && context.myUsedVarNames.contains(referenceExpression.getReferenceName())) {
      builder.append(referenceExpression.getReferenceName());
      return;
    }

    //all refs in script that are not resolved are saved in 'binding' of the script
    if (qualifier == null &&
        (resolved == null ||
         resolved instanceof GrReferenceExpression ||
         resolved instanceof LightElement && !(resolved instanceof ClosureSyntheticParameter)) &&
        !(referenceExpression.getParent() instanceof GrCall) &&
        PsiUtil.isInScriptContext(referenceExpression)) {
      final GrExpression thisExpr = factory.createExpressionFromText("this", referenceExpression);
      thisExpr.accept(this);
      builder.append(".getBinding().getProperty(\"").append(referenceExpression.getName()).append("\")");
      return;
    }

    final IElementType type = referenceExpression.getDotTokenType();

    GrExpression qualifierToUse = qualifier;

    if (type == mMEMBER_POINTER) {
      LOG.assertTrue(qualifier != null);
      builder.append("new ").append(GroovyCommonClassNames.ORG_CODEHAUS_GROOVY_RUNTIME_METHOD_CLOSURE).append('(');
      qualifier.accept(this);
      builder.append(", \"").append(referenceExpression.getReferenceName()).append("\")");
      return;
    }

    if (type == mOPTIONAL_DOT) {
      LOG.assertTrue(qualifier != null);

      String qualifierName = createVarByInitializer(qualifier);
      builder.append(qualifierName).append(" == null ? null : ");

      qualifierToUse = factory.createReferenceExpressionFromText(qualifierName, referenceExpression);
    }


    if (resolveResult.isInvokedOnProperty()) {
      //property-style access to accessor (e.g. qual.prop should be translated to qual.getProp())
      LOG.assertTrue(resolved instanceof PsiMethod);
      LOG.assertTrue(GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)resolved));
      invokeMethodOn(
        ((PsiMethod)resolved),
        qualifierToUse,
        GrExpression.EMPTY_ARRAY,
        GrNamedArgument.EMPTY_ARRAY,
        EMPTY_ARRAY,
        resolveResult.getSubstitutor(),
        referenceExpression
      );
    }
    else {
      if (qualifierToUse != null) {
        qualifierToUse.accept(this);
        builder.append('.');
      }

      if (resolved instanceof PsiNamedElement && !(resolved instanceof GrReferenceExpression)) {
        final String refName = ((PsiNamedElement)resolved).getName();

        if (resolved instanceof GrVariable && context.analyzedVars.toWrap((GrVariable)resolved)) {
          //this var should be wrapped by groovy.lang.Reference. so we add .get() tail.
          builder.append(context.analyzedVars.toVarName((GrVariable)resolved));
          if (!PsiUtil.isAccessedForWriting(referenceExpression)) {
            builder.append(".get()");
          }
        }
        else {
          builder.append(refName);
        }
      }
      else {
        //unresolved reference
        final String refName = referenceExpression.getReferenceName();
        if (refName != null) {
          if (PsiUtil.isAccessedForWriting(referenceExpression)) {
            builder.append(refName);
          }
          else {
            builder.append("getProperty(\"").append(refName).append("\")");
          }
        }
        else {
          final PsiElement nameElement = referenceExpression.getReferenceNameElement();
          if (nameElement instanceof GrExpression) {
            ((GrExpression)nameElement).accept(this);
          }
          else if (nameElement != null) {
            builder.append(nameElement.toString());
          }
        }
      }
    }
  }

  private String createVarByInitializer(@NotNull GrExpression initializer) {
    GrExpression inner = initializer;
    while (inner instanceof GrParenthesizedExpression) inner = ((GrParenthesizedExpression)inner).getOperand();
    if (inner != null) initializer = inner;

    if (initializer instanceof GrReferenceExpression) {
      final GrExpression qualifier = ((GrReferenceExpression)initializer).getQualifier();
      if (qualifier == null) {
        final PsiElement resolved = ((GrReferenceExpression)initializer).resolve();
        if (resolved instanceof GrVariable && !(resolved instanceof GrField)) {

          //don't create new var. it is already exists
          return ((GrVariable)resolved).getName();
        }
      }
    }
    final String name = suggestVarName(initializer, context);
    final StringBuilder builder = new StringBuilder();
    builder.append("final ");
    writeType(builder, initializer.getType(), initializer);
    builder.append(' ').append(name).append(" = ");
    initializer.accept(new ExpressionGenerator(builder, context));
    builder.append(';');
    context.myStatements.add(builder.toString());
    return name;
  }

  @Override
  public void visitThisSuperReferenceExpression(GrThisSuperReferenceExpression expr) {
    if (context.isInAnonymousContext() && expr.getQualifier() == null) {
      builder.append(expr.getReferenceName());
      return;
    }
    final PsiElement resolved = expr.resolve();
    LOG.assertTrue(resolved instanceof PsiClass);

    if (!(resolved instanceof PsiAnonymousClass)) {
      builder.append(((PsiClass)resolved).getQualifiedName()).append('.');
    }
    builder.append(expr.getReferenceName());
  }

  @Override
  public void visitCastExpression(GrTypeCastExpression typeCastExpression) {
    final GrTypeElement typeElement = typeCastExpression.getCastTypeElement();
    final GrExpression operand = typeCastExpression.getOperand();
    generateCast(typeElement, operand);
  }

  private void generateCast(GrTypeElement typeElement, GrExpression operand) {
    builder.append('(');
    writeType(builder, typeElement.getType(), typeElement);
    builder.append(')');

    boolean insertParentheses =
      operand instanceof GrBinaryExpression && ((GrBinaryExpression)operand).getOperationTokenType() == GroovyTokenTypes.mEQUAL;
    if (insertParentheses) builder.append('(');

    if (operand != null) {
      operand.accept(this);
    }
    if (insertParentheses) builder.append(')');
  }

  @Override
  public void visitSafeCastExpression(GrSafeCastExpression typeCastExpression) {
    final GrExpression operand = (GrExpression)PsiUtil.skipParenthesesIfSensibly(typeCastExpression.getOperand(), false);
    final GrTypeElement typeElement = typeCastExpression.getCastTypeElement();

    if (operand instanceof GrListOrMap && ((GrListOrMap)operand).isMap() && typeElement != null) {
      AnonymousFromMapGenerator.writeAnonymousMap((GrListOrMap)operand, typeElement, builder, context);
      return;
    }

    if (operand instanceof GrListOrMap && !((GrListOrMap)operand).isMap() && typeElement instanceof GrArrayTypeElement) {
      builder.append("new ");
      writeType(builder, typeElement.getType(), typeCastExpression);
      builder.append('{');
      final GrExpression[] initializers = ((GrListOrMap)operand).getInitializers();
      for (GrExpression initializer : initializers) {
        initializer.accept(this);
        builder.append(", ");
      }
      if (initializers.length > 0) {
        builder.delete(builder.length()-2,builder.length());
        //builder.removeFromTheEnd(2);
      }
      builder.append('}');
      return;
    }

    final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(typeCastExpression.multiResolve(false));
    final PsiElement resolved = resolveResult.getElement();

    if (resolved instanceof PsiMethod) {
      final GrExpression typeParam = factory.createExpressionFromText(typeElement == null ? "null" : typeElement.getText());
      invokeMethodOn(
        ((PsiMethod)resolved),
        operand,
        new GrExpression[]{typeParam},
        GrNamedArgument.EMPTY_ARRAY,
        EMPTY_ARRAY,
        resolveResult.getSubstitutor(),
        typeCastExpression
      );
    }
    else {
      generateCast(typeElement, operand);
    }
  }

  @Override
  public void visitInstanceofExpression(GrInstanceOfExpression expression) {
    final GrExpression operand = expression.getOperand();
    final GrTypeElement typeElement = expression.getTypeElement();
    operand.accept(this);
    builder.append(" instanceof ");

    if (typeElement != null) {
      typeElement.accept(this);
    }
  }

  @Override
  public void visitBuiltinTypeClassExpression(GrBuiltinTypeClassExpression expression) {
    PsiElement firstChild = expression.getFirstChild();
    LOG.assertTrue(firstChild != null);
    ASTNode node = firstChild.getNode();
    LOG.assertTrue(node != null);
    final IElementType type = node.getElementType();
    final String boxed = TypesUtil.getBoxedTypeName(type);
    builder.append(boxed);
    if (expression.getParent() instanceof GrIndexProperty) {
      builder.append("[]");
    }
    builder.append(".class");
  }

  @Override
  public void visitParenthesizedExpression(GrParenthesizedExpression expression) {
    builder.append('(');
    final GrExpression operand = expression.getOperand();
    if (operand != null) {
      operand.accept(this);
    }
    builder.append(')');
  }

  @Override
  public void visitIndexProperty(GrIndexProperty expression) {
    final GrExpression selectedExpression = expression.getSelectedExpression();
    final PsiType thisType = selectedExpression.getType();

    final GrArgumentList argList = expression.getArgumentList();

    if (argList.getAllArguments().length == 0) {                       // int[] or String[]
      if (selectedExpression instanceof GrBuiltinTypeClassExpression) {
        selectedExpression.accept(this);
        return;
      }
      else if (selectedExpression instanceof GrReferenceExpression) {
        PsiElement resolved = ((GrReferenceExpression)selectedExpression).resolve();
        if (resolved instanceof PsiClass) {
          builder.append(((PsiClass)resolved).getQualifiedName());
          builder.append("[].class");
          return;
        }
      }
    }
    final PsiType[] argTypes = PsiUtil.getArgumentTypes(argList);
    final PsiManager manager = expression.getManager();
    final GlobalSearchScope resolveScope = expression.getResolveScope();

    final GrExpression[] exprArgs = argList.getExpressionArguments();
    final GrNamedArgument[] namedArgs = argList.getNamedArguments();

    if (!PsiImplUtil.isSimpleArrayAccess(thisType, argTypes, manager, resolveScope)) {
      final GroovyResolveResult candidate = PsiImplUtil.extractUniqueResult(expression.multiResolve(false));
      PsiElement element = candidate.getElement();
      if (element != null || !PsiUtil.isLValue(expression)) {                     //see the case of l-value in assignment expression
        if (element instanceof GrGdkMethod && ((GrGdkMethod)element).getStaticMethod().getParameterList().getParameters()[0].getType().equalsToText("java.util.Map<K,V>")) {
          PsiClass map = JavaPsiFacade.getInstance(context.project).findClass(CommonClassNames.JAVA_UTIL_MAP, expression.getResolveScope());
          if (map != null) {
            PsiMethod[] gets = map.findMethodsByName("get", false);
            invokeMethodOn(gets[0], selectedExpression, exprArgs, namedArgs, EMPTY_ARRAY, PsiSubstitutor.EMPTY, expression);
            return;
          }
        }
        else if (element instanceof GrGdkMethod && ((GrGdkMethod)element).getStaticMethod().getParameterList().getParameters()[0].getType().equalsToText("java.util.List<T>")) {
          PsiClass list = JavaPsiFacade.getInstance(context.project).findClass(CommonClassNames.JAVA_UTIL_LIST, expression.getResolveScope());
          if (list != null) {
            PsiMethod[] gets = list.findMethodsByName("get", false);
            invokeMethodOn(gets[0], selectedExpression, exprArgs, namedArgs, EMPTY_ARRAY, PsiSubstitutor.EMPTY, expression);
            return;
          }
          
        }
        invokeMethodByResolveResult(selectedExpression, candidate, "getAt", exprArgs, namedArgs, EMPTY_ARRAY, this, expression);
        return;
      }
    }

    selectedExpression.accept(this);
    builder.append('[');
    final GrExpression arg = exprArgs[0];
    arg.accept(this);
    builder.append(']');
  }

  public void invokeMethodOn(PsiMethod method,
                             @Nullable GrExpression caller,
                             GrExpression[] exprs,
                             GrNamedArgument[] namedArgs,
                             GrClosableBlock[] closures,
                             PsiSubstitutor substitutor,
                             GroovyPsiElement context) {
    if (method instanceof GrGdkMethod) {

      GrExpression[] newArgs = new GrExpression[exprs.length + 1];
      System.arraycopy(exprs, 0, newArgs, 1, exprs.length);
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        newArgs[0] = factory.createExpressionFromText("null");
      }
      else {
        if (caller == null) {
          caller = factory.createExpressionFromText("this", context);
        }
        newArgs[0] = caller;
      }
      invokeMethodOn(((GrGdkMethod)method).getStaticMethod(), null, newArgs, namedArgs, closures, substitutor, context);
      return;
    }

    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        builder.append(containingClass.getQualifiedName()).append('.');
      }
    }
    else {
      //LOG.assertTrue(caller != null, "instance method call should have caller");

      if (caller != null) {

        final boolean castNeeded = isCastNeeded(caller, method, this.context);
        if (castNeeded) {
          builder.append('(');
          final PsiType type = caller.getType();
          builder.append('(');
          writeType(builder, type, context);
          builder.append(')');
        }
        caller.accept(this);
        if (castNeeded) {
          builder.append(')');
        }
        builder.append('.');
      }
    }
    builder.append(method.getName());
    final GrClosureSignature signature = GrClosureSignatureUtil.createSignature(method, substitutor);
    new ArgumentListGenerator(builder, this.context).generate(signature, exprs, namedArgs, closures, context);
  }


  @Override
  public void visitListOrMap(GrListOrMap listOrMap) {
    final PsiType type = listOrMap.getType();

    //can be PsiArrayType or GrLiteralClassType
    LOG.assertTrue(type instanceof GrLiteralClassType || type instanceof PsiArrayType || type instanceof PsiClassType);

    if (listOrMap.isMap()) {
      if (listOrMap.getNamedArguments().length == 0) {
        builder.append("new ");
        writeType(builder, type, listOrMap);
        builder.append("()");
      }
      else {
        String varName = generateMapVariableDeclaration(listOrMap, type);
        generateMapElementInsertions(listOrMap, varName);
        builder.append(varName);
      }
    }
    else {
      boolean isArray = type instanceof PsiArrayType;
      builder.append("new ");
      writeType(builder, type, listOrMap);
      if (listOrMap.getInitializers().length == 0) {
        if (isArray) {
          builder.replace(builder.length() - 2, builder.length(), "[0]");
        }
        else {
          builder.append("()");
        }
      }
      else {
        if (isArray) {
          builder.append('{');
        }
        else {
          builder.append("(java.util.Arrays.asList(");
        }
        genInitializers(listOrMap);
        if (isArray) {
          builder.append('}');
        }
        else {
          builder.append("))");
        }
      }
    }
  }

  private void generateMapElementInsertions(GrListOrMap listOrMap, String varName) {
    for (GrNamedArgument arg : listOrMap.getNamedArguments()) {
      StringBuilder insertion = new StringBuilder();
      insertion.append(varName).append(".put(");
      final String stringKey = arg.getLabelName();
      if (stringKey != null) {
        insertion.append('"').append(stringKey).append('"');
      }
      else {
        final GrArgumentLabel label = arg.getLabel();
        final GrExpression expression = label == null ? null : label.getExpression();
        if (expression != null) {
          expression.accept(new ExpressionGenerator(insertion, context));
        }
        else {
          //todo should we generate an exception?
        }
      }
      insertion.append(", ");
      final GrExpression expression = arg.getExpression();
      if (expression != null) {
        expression.accept(new ExpressionGenerator(insertion, context));
      }
      else {
        //todo should we generate an exception?
      }

      insertion.append(");");
      context.myStatements.add(insertion.toString());
    }
  }

  private String generateMapVariableDeclaration(GrListOrMap listOrMap, PsiType type) {
    StringBuilder declaration = new StringBuilder();

    writeType(declaration, type, listOrMap);
    final String varName = suggestVarName(type, listOrMap, this.context);
    declaration.append(' ').append(varName).append(" = new ");
    writeType(declaration, type, listOrMap);

    declaration.append('(');
    //insert count of elements in list or map

    declaration.append(listOrMap.getNamedArguments().length);
    declaration.append(");");
    context.myStatements.add(declaration.toString());
    return varName;
  }

  private void genInitializers(GrListOrMap list) {
    LOG.assertTrue(!list.isMap());

    final GrExpression[] initializers = list.getInitializers();
    for (GrExpression expr : initializers) {
      expr.accept(this);
      builder.append(", ");
    }

    if (initializers.length > 0) {
      builder.delete(builder.length()-2, builder.length());
      //builder.removeFromTheEnd(2);
    }
  }

  @Override
  public void visitRangeExpression(GrRangeExpression range) {
    final PsiType type = range.getType();
    LOG.assertTrue(type instanceof GrRangeType);
    final PsiClass resolved = ((GrRangeType)type).resolve();
    builder.append("new ");
    if (resolved == null) {
      builder.append(GroovyCommonClassNames.GROOVY_LANG_OBJECT_RANGE);
    }
    else {
      builder.append(resolved.getQualifiedName());
    }
    builder.append('(');
    final GrExpression left = range.getLeftOperand();
    left.accept(this);

    builder.append(", ");

    final GrExpression right = range.getRightOperand();
    if (right != null) {
      right.accept(this);
    }

    builder.append(')');
  }
}
