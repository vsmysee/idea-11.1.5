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

package org.jetbrains.plugins.groovy.lang.completion;


import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.ModifierChooser;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.lang.ASTNode;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocInlinedTag;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * @author ilyas
 */
public class GroovyCompletionData {
  public static final String[] BUILT_IN_TYPES = {"boolean", "byte", "char", "short", "int", "float", "long", "double", "void"};
  public static final String[] MODIFIERS = new String[]{"private", "public", "protected", "transient", "abstract", "native", "volatile", "strictfp", "static"};
  static final String[] INLINED_DOC_TAGS = {"code", "docRoot", "inheritDoc", "link", "linkplain", "literal"};
  static final String[] DOC_TAGS = {"author", "deprecated", "exception", "param", "return", "see", "serial", "serialData",
      "serialField", "since", "throws", "version"};

  public static void addGroovyKeywords(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    PsiElement parent = position.getParent();
    if (parent instanceof GrLiteral) {
      return;
    }
    
    if (!PlatformPatterns.psiElement().afterLeaf(".", ".&", "@", "*.", "?.").accepts(position)) {
      if (suggestPackage(position)) {
        result.addElement(keyword(PsiKeyword.PACKAGE, TailType.INSERT_SPACE));
      }
      if (suggestImport(position)) {
        result.addElement(keyword(PsiKeyword.IMPORT, TailType.INSERT_SPACE));
      }

      addTypeDefinitionKeywords(result, position);
      for (String keyword : addExtendsImplements(position)) {
        result.addElement(keyword(keyword, TailType.HUMBLE_SPACE_BEFORE_WORD));
      }

      registerControlCompletion(position, result);

      if (parent instanceof GrExpression) {
        addKeywords(result, false, PsiKeyword.TRUE, PsiKeyword.FALSE, PsiKeyword.NULL, PsiKeyword.SUPER, PsiKeyword.THIS);
        result.addElement(keyword(PsiKeyword.NEW, TailType.INSERT_SPACE));
        result.addElement(keyword("as", TailType.HUMBLE_SPACE_BEFORE_WORD));
      }

      if (isAfterForParameter(position)) {
        addKeywords(result, true, "in");
      }
      else if (isInfixOperatorPosition(position)) {
        addKeywords(result, true, "in", PsiKeyword.INSTANCEOF);
      } else if (suggestThrows(position)) {
        result.addElement(keyword(PsiKeyword.THROWS, TailType.INSERT_SPACE));
      } else if (suggestPrimitiveTypes(position)) {
        boolean inCast = psiElement()
          .afterLeaf(psiElement().withText("(").withParent(psiElement(GrParenthesizedExpression.class, GrTypeCastExpression.class)))
          .accepts(position);

        addKeywords(result, !inCast, BUILT_IN_TYPES);
      }

      if (psiElement(GrReferenceExpression.class).inside(or(psiElement(GrWhileStatement.class), psiElement(GrForStatement.class))).accepts(parent)) {
        addKeywords(result, false, PsiKeyword.BREAK, PsiKeyword.CONTINUE);
      }
      else if (psiElement(GrReferenceExpression.class).inside(GrCaseSection.class).accepts(parent)) {
        addKeywords(result, false, PsiKeyword.BREAK);
      }

      if (psiElement().withSuperParent(2, GrImportStatement.class).accepts(position)) {
        if (psiElement().afterLeaf(PsiKeyword.IMPORT).accepts(position)) {
          addKeywords(result, true, PsiKeyword.STATIC);
        }
      } else {
        if (suggestModifiers(position)) {
          addModifiers(position, result);
        }
        if (psiElement().afterLeaf(MODIFIERS).accepts(position) ||
            GroovyCompletionUtil.isInTypeDefinitionBody(position) && GroovyCompletionUtil.isNewStatement(position, true)) {
          addKeywords(result, true, PsiKeyword.SYNCHRONIZED);
        }
        if (suggestFinalDef(position) || psiElement().afterLeaf(
          psiElement().withText("(").withParent(GrForStatement.class)).accepts(position)) {
          addKeywords(result, true, PsiKeyword.FINAL, "def");
        }
      }
    }
  }

  private static boolean isAfterForParameter(PsiElement position) {
    ElementPattern<PsiElement> forParameter =
      psiElement().withParents(GrParameter.class, GrTraditionalForClause.class, GrForStatement.class);
    return psiElement().withParent(GrReferenceExpression.class).afterLeaf(forParameter).accepts(position) ||
           forParameter.accepts(position) && psiElement().afterLeaf(psiElement(GroovyTokenTypes.mIDENT)).accepts(position);
  }

  public static void addModifiers(PsiElement position, CompletionResultSet result) {
    PsiClass scope = PsiTreeUtil.getParentOfType(position, PsiClass.class);
    PsiModifierList modifierList = ModifierChooser.findModifierList(position);
    addKeywords(result, true, ModifierChooser.addMemberModifiers(modifierList, scope != null && scope.isInterface()));
  }

  private static void addTypeDefinitionKeywords(CompletionResultSet result, PsiElement position) {
    if (suggestClassInterfaceEnum(position)) {
      addKeywords(result, true, PsiKeyword.CLASS, PsiKeyword.INTERFACE, PsiKeyword.ENUM);
    }
    if (afterAtInType(position)) {
      result.addElement(keyword(PsiKeyword.INTERFACE, TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  @NotNull
  private static String[] addExtendsImplements(PsiElement context) {
    if (context.getParent() == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    PsiElement elem = context.getParent();
    boolean ext = !(elem instanceof GrExtendsClause);
    boolean impl = !(elem instanceof GrImplementsClause);

    if (elem instanceof GrTypeDefinitionBody) { //inner class
      elem = PsiUtil.skipWhitespaces(context.getPrevSibling(), false);
    }
    else {
      elem = PsiUtil.skipWhitespaces(elem.getPrevSibling(), false);
    }

    ext &= elem instanceof GrInterfaceDefinition || elem instanceof GrClassDefinition;
    impl &= elem instanceof GrEnumTypeDefinition || elem instanceof GrClassDefinition;
    if (!ext && !impl) return ArrayUtil.EMPTY_STRING_ARRAY;

    PsiElement[] children = elem.getChildren();
    for (PsiElement child : children) {
      ext &= !(child instanceof GrExtendsClause);
      if (child instanceof GrImplementsClause || child instanceof GrTypeDefinitionBody) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
    }
    if (ext && impl) {
      return new String[]{PsiKeyword.EXTENDS, PsiKeyword.IMPLEMENTS};
    }
    
    return new String[]{ext ? PsiKeyword.EXTENDS : PsiKeyword.IMPLEMENTS};
  }

  public static void addKeywords(CompletionResultSet result, boolean space, String... keywords) {
    for (String s : keywords) {
      result.addElement(keyword(s, space ? TailType.HUMBLE_SPACE_BEFORE_WORD : TailType.NONE));
    }
  }

  private static LookupElement keyword(final String keyword, @NotNull TailType tail) {
    LookupElementBuilder element = LookupElementBuilder.create(keyword).setBold();
    return tail != TailType.NONE ? TailTypeDecorator.withTail(element, tail) : element;
  }

  private static void registerControlCompletion(PsiElement context, CompletionResultSet result) {
    String[] controlKeywords = {"try", "while", "with", "switch", "for", "return", "throw", "assert", "synchronized",};

    if (isControlStructure(context)) {
      addKeywords(result, true, controlKeywords);
    }
    if (inCaseSection(context)) {
      result.addElement(keyword("case", TailType.INSERT_SPACE));
      result.addElement(keyword("default", TailType.CASE_COLON));
    }
    if (afterTry(context)) {
      addKeywords(result, true, "catch", "finally");
    }
    if (afterIfOrElse(context)) {
      addKeywords(result, true, "else");
    }
  }

  public static void addGroovyDocKeywords(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    if (PlatformPatterns.psiElement(GroovyDocTokenTypes.mGDOC_TAG_NAME).andNot(PlatformPatterns.psiElement().afterLeaf(".")).accepts(position)) {
      String[] tags = position.getParent() instanceof GrDocInlinedTag ? INLINED_DOC_TAGS : DOC_TAGS;
      for (String docTag : tags) {
        result.addElement(TailTypeDecorator.withTail(LookupElementBuilder.create(docTag), TailType.INSERT_SPACE));
      }
    }
  }

  private static boolean suggestPackage(PsiElement context) {
    if (context.getParent() != null &&
        !(context.getParent() instanceof PsiErrorElement) &&
        context.getParent().getParent() instanceof GroovyFile &&
        ((GroovyFile) context.getParent().getParent()).getPackageDefinition() == null) {
      if (context.getParent() instanceof GrReferenceExpression) {
        return true;
      }
      if (context.getParent() instanceof GrApplicationStatement &&
          ((GrApplicationStatement) context.getParent()).getExpressionArguments()[0] instanceof GrReferenceExpression) {
        return true;
      }
      return false;
    }
    if (context.getTextRange().getStartOffset() == 0 && !(context instanceof OuterLanguageElement)) {
      return true;
    }

    final PsiElement leaf = GroovyCompletionUtil.getLeafByOffset(context.getTextRange().getStartOffset() - 1, context);
    if (leaf != null) {
      PsiElement parent = leaf.getParent();
      if (parent instanceof GroovyFile) {
        GroovyFile groovyFile = (GroovyFile) parent;
        if (groovyFile.getPackageDefinition() == null) {
          return GroovyCompletionUtil.isNewStatement(context, false);
        }
      }
    }

    return false;
  }

  private static boolean suggestImport(PsiElement context) {
    if (context.getParent() != null &&
        !(context.getParent() instanceof PsiErrorElement) &&
        GroovyCompletionUtil.isNewStatement(context, false) &&
        context.getParent().getParent() instanceof GroovyFile) {
      return true;
    }
    final PsiElement leaf = GroovyCompletionUtil.getLeafByOffset(context.getTextRange().getStartOffset() - 1, context);
    if (leaf != null) {
      PsiElement parent = leaf.getParent();
      if (parent instanceof GroovyFile) {
        return GroovyCompletionUtil.isNewStatement(context, false);
      }
    }
    return context.getTextRange().getStartOffset() == 0 && !(context instanceof OuterLanguageElement);
  }

  public static boolean suggestClassInterfaceEnum(PsiElement context) {
    if (suggestThrows(context) || addExtendsImplements(context).length > 0) {
      return false;
    }

    PsiElement parent = context.getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      return true;
    }

    if (parent instanceof GrReferenceExpression) {
      if (parent.getParent() instanceof GroovyFile) {
        return true;
      }
      if ((parent.getParent() instanceof GrApplicationStatement ||
           parent.getParent() instanceof GrCall) &&
          parent.getParent().getParent() instanceof GroovyFile) {
        return true;
      }
    }
    final PsiElement leaf = GroovyCompletionUtil.getLeafByOffset(context.getTextRange().getStartOffset() - 1, context);
    if (leaf != null) {
      PsiElement prev = leaf;
      prev = PsiImplUtil.realPrevious(prev);
      if (prev instanceof GrModifierList &&
          prev.getParent() != null &&
          prev.getParent().getParent() instanceof GroovyFile) {
        return true;
      }

      if (leaf.getParent() instanceof GroovyFile) {
        return GroovyCompletionUtil.isNewStatement(context, false);
      }
    }

    return false;
  }

  private static boolean afterAtInType(PsiElement context) {
    PsiElement previous = PsiImplUtil.realPrevious(context.getPrevSibling());
    if (previous != null &&
        GroovyTokenTypes.mAT.equals(previous.getNode().getElementType()) &&
        context.getParent() != null &&
        context.getParent().getParent() instanceof GroovyFile) {
      return true;
    }
    if (context.getParent() instanceof PsiErrorElement &&
        context.getParent().getParent() instanceof GrAnnotation) {
      return true;
    }
    return false;
  }

  private static boolean isControlStructure(PsiElement context) {
    final int offset = context.getTextRange().getStartOffset();
    PsiElement prevSibling = context.getPrevSibling();
    if (context.getParent() instanceof GrReferenceElement && prevSibling != null && prevSibling.getNode() != null) {
      ASTNode node = prevSibling.getNode();
      return !TokenSets.DOTS.contains(node.getElementType());
    }
    if (GroovyCompletionUtil.isNewStatement(context, true)) {
      final PsiElement leaf = GroovyCompletionUtil.getLeafByOffset(offset - 1, context);
      if (leaf != null && leaf.getParent() instanceof GrStatementOwner) {
        return true;
      }
    }

    if (context.getParent() != null) {
      PsiElement parent = context.getParent();

      if (parent instanceof GrExpression &&
          parent.getParent() instanceof GroovyFile) {
        return true;
      }

      if (parent instanceof GrReferenceExpression) {

        PsiElement superParent = parent.getParent();
        if (superParent instanceof GrExpression) {
          superParent = superParent.getParent();
        }

        if (superParent instanceof GrStatementOwner ||
            superParent instanceof GrIfStatement ||
            superParent instanceof GrForStatement ||
            superParent instanceof GrWhileStatement) {
          return true;
        }
      }

      return false;
    }

    return false;
  }

  private static boolean inCaseSection(PsiElement context) {
    if (context.getParent() instanceof GrReferenceExpression &&
        context.getParent().getParent() instanceof GrCaseSection) {
      return true;
    }
    final PsiElement left = GroovyCompletionUtil.nearestLeftSibling(context);
    if (left != null && left.getParent() != null &&
        left.getParent() instanceof GrSwitchStatement &&
        left.getPrevSibling() != null &&
        left.getPrevSibling().getNode() != null &&
        GroovyTokenTypes.mLCURLY.equals(left.getPrevSibling().getNode().getElementType())) {
      return true;
    }
    return false;
  }

  private static boolean afterTry(PsiElement context) {
    if (context != null &&
        GroovyCompletionUtil.nearestLeftSibling(context) instanceof GrTryCatchStatement) {
      GrTryCatchStatement tryStatement = (GrTryCatchStatement) GroovyCompletionUtil.nearestLeftSibling(context);
      if (tryStatement == null) return false;
      if (tryStatement.getFinallyClause() == null) {
        return true;
      }
    }
    if (context != null &&
        GroovyCompletionUtil.nearestLeftSibling(context) instanceof PsiErrorElement &&
        GroovyCompletionUtil.nearestLeftSibling(context).getPrevSibling() instanceof GrTryCatchStatement) {
      GrTryCatchStatement tryStatement = (GrTryCatchStatement) GroovyCompletionUtil.nearestLeftSibling(context).getPrevSibling();
      if (tryStatement == null) return false;
      if (tryStatement.getFinallyClause() == null) {
        return true;
      }
    }
    if (context != null &&
        (context.getParent() instanceof GrReferenceExpression || context.getParent() instanceof PsiErrorElement) &&
        GroovyCompletionUtil.nearestLeftSibling(context.getParent()) instanceof GrTryCatchStatement) {
      GrTryCatchStatement tryStatement = (GrTryCatchStatement) GroovyCompletionUtil.nearestLeftSibling(context.getParent());
      if (tryStatement == null) return false;
      if (tryStatement.getFinallyClause() == null) {
        return true;
      }
    }
    return false;
  }

  private static boolean afterIfOrElse(PsiElement context) {
    if (context.getParent() != null &&
        GroovyCompletionUtil.nearestLeftSibling(context.getParent()) instanceof GrIfStatement) {
      return true;
    }
    if (context.getParent() != null &&
        GroovyCompletionUtil.nearestLeftSibling(context) != null &&
        GroovyCompletionUtil.nearestLeftSibling(context).getPrevSibling() instanceof GrIfStatement) {
      GrIfStatement statement = (GrIfStatement) GroovyCompletionUtil.nearestLeftSibling(context).getPrevSibling();
      if (statement.getElseBranch() == null) {
        return true;
      }
    }
    if (context.getParent() != null &&
        context.getParent().getParent() instanceof GrCommandArgumentList &&
        context.getParent().getParent().getParent().getParent() instanceof GrIfStatement) {
      GrIfStatement statement = (GrIfStatement) context.getParent().getParent().getParent().getParent();
      if (statement.getElseBranch() == null) {
        return true;
      }
    }
    return false;
  }

  private static boolean suggestThrows(PsiElement context) {
    PsiElement candidate = null;
    if (GroovyCompletionUtil.isInTypeDefinitionBody(context)) {
      PsiElement run = context;
      while(!(run.getParent() instanceof GrTypeDefinitionBody)) {
        run = run.getParent();
        assert run != null;
      }
      candidate = PsiTreeUtil.getPrevSiblingOfType(run, GrMember.class);
    }
    else if (context.getParent() instanceof PsiErrorElement) {
     candidate = context.getParent().getPrevSibling();
    }

    return candidate instanceof GrMethod && ((GrMethod) candidate).getBlock() == null;
  }

  private static boolean suggestPrimitiveTypes(PsiElement context) {
    final PsiElement parent = context.getParent();
    if (parent == null) return false;

    PsiElement previous = PsiImplUtil.realPrevious(parent.getPrevSibling());
    if (parent instanceof GrReferenceElement && parent.getParent() instanceof GrArgumentList) {
      PsiElement prevSibling = context.getPrevSibling();
      if (prevSibling != null && prevSibling.getNode() != null) {
        if (!TokenSets.DOTS.contains(prevSibling.getNode().getElementType())) {
          return true;
        }
      } else if (!(previous != null && GroovyTokenTypes.mAT.equals(previous.getNode().getElementType()))) {
        return true;
      }

    }

    if (previous != null && GroovyTokenTypes.mAT.equals(previous.getNode().getElementType())) {
      return false;
    }
    if (GroovyCompletionUtil.asSimpleVariable(context) ||
        GroovyCompletionUtil.asTypedMethod(context) ||
        GroovyCompletionUtil.asVariableInBlock(context)) {
      return true;
    }
    if ((parent instanceof GrParameter &&
         ((GrParameter)parent).getTypeElementGroovy() == null) ||
        parent instanceof GrReferenceElement &&
        !(parent.getParent() instanceof GrImportStatement) &&
        !(parent.getParent() instanceof GrPackageDefinition) &&
        !(parent.getParent() instanceof GrArgumentList)) {
      PsiElement prevSibling = context.getPrevSibling();
      if (parent instanceof GrReferenceElement && prevSibling != null && prevSibling.getNode() != null) {
        ASTNode node = prevSibling.getNode();
        return !TokenSets.DOTS.contains(node.getElementType());
      } else {
        return true;
      }
    }
    if (PsiImplUtil.realPrevious(parent.getPrevSibling()) instanceof GrModifierList) {
      return true;
    }
    if (PsiImplUtil.realPrevious(context.getPrevSibling()) instanceof GrModifierList) {
      return true;
    }
    return parent instanceof GrExpression &&
           parent.getParent() instanceof GroovyFile &&
           GroovyCompletionUtil.isNewStatement(context, false);
  }

  private static boolean isInfixOperatorPosition(PsiElement context) {
    if (context.getParent() != null &&
        context.getParent() instanceof GrReferenceExpression &&
        context.getParent().getParent() != null &&
        context.getParent().getParent() instanceof GrCommandArgumentList) {
      return true;
    }
    if (GroovyCompletionUtil.nearestLeftSibling(context) instanceof PsiErrorElement &&
        GroovyCompletionUtil.endsWithExpression(GroovyCompletionUtil.nearestLeftSibling(context).getPrevSibling())) {
      return true;
    }
    if (context.getParent() instanceof GrReferenceExpression &&
        GroovyCompletionUtil.nearestLeftSibling(context.getParent()) instanceof PsiErrorElement &&
        GroovyCompletionUtil.endsWithExpression(GroovyCompletionUtil.nearestLeftSibling(context.getParent()).getPrevSibling())) {
      return true;
    }
    if (context.getParent() instanceof PsiErrorElement &&
        GroovyCompletionUtil.endsWithExpression(GroovyCompletionUtil.nearestLeftSibling(context.getParent()))) {
      return true;
    }

    return false;
  }

  private static boolean suggestModifiers(PsiElement context) {
    if (GroovyCompletionUtil.asSimpleVariable(context) || GroovyCompletionUtil.asTypedMethod(context)) {
      return true;
    }
    if (GroovyCompletionUtil.isFirstElementAfterPossibleModifiersInVariableDeclaration(context, false) &&
        !psiElement().afterLeaf("def").accepts(context)) {
      return true;
    }

    if (psiElement().afterLeaf(MODIFIERS).accepts(context) || psiElement().afterLeaf("synchronized").accepts(context)) {
      return true;
    }

    final PsiElement contextParent = context.getParent();
    if (contextParent instanceof GrReferenceElement && contextParent.getParent() instanceof GrTypeElement) {
      PsiElement parent = contextParent.getParent().getParent();
      if (parent instanceof GrVariableDeclaration &&
          (parent.getParent() instanceof GrTypeDefinitionBody || parent.getParent() instanceof GroovyFile) || parent instanceof GrMethod) {
        return true;
      }
    }
    if (contextParent instanceof GrField) {
      final GrVariable variable = (GrVariable)contextParent;
      if (variable.getTypeElementGroovy() == null) {
        return true;
      }
    }
    if (contextParent instanceof GrExpression &&
        contextParent.getParent() instanceof GroovyFile &&
        GroovyCompletionUtil.isNewStatement(context, false)) {
      return true;
    }
    if (context.getTextRange().getStartOffset() == 0 && !(context instanceof OuterLanguageElement)) {
      return true;
    }
    final PsiElement leaf = GroovyCompletionUtil.getLeafByOffset(context.getTextRange().getStartOffset() - 1, context);
    if (leaf != null && GroovyCompletionUtil.isNewStatement(context, false)) {
      PsiElement parent = leaf.getParent();
      if (parent instanceof GroovyFile) {
        return true;
      }
    }
    return contextParent instanceof GrExpression &&
           contextParent.getParent() instanceof GrApplicationStatement &&
           contextParent.getParent().getParent() instanceof GroovyFile &&
           GroovyCompletionUtil.isNewStatement(context, false);
  }

  public static boolean suggestFinalDef(PsiElement context) {
    if (GroovyCompletionUtil.asSimpleVariable(context) ||
        GroovyCompletionUtil.asTypedMethod(context) ||
        GroovyCompletionUtil.asVariableInBlock(context)) {
      return true;
    }
    if ((context.getParent() instanceof GrParameter &&
        ((GrParameter) context.getParent()).getTypeElementGroovy() == null) ||
        context.getParent() instanceof GrReferenceElement &&
            !(context.getParent() instanceof GrReferenceExpression) &&
            !(context.getParent().getParent() instanceof GrImportStatement) &&
            !(context.getParent().getParent() instanceof GrPackageDefinition)) {
      return true;
    }
    if (PsiImplUtil.realPrevious(context.getParent().getPrevSibling()) instanceof GrModifierList) {
      return true;
    }
    if (PsiImplUtil.realPrevious(context.getPrevSibling()) instanceof GrModifierList) {
      return true;
    }
    return context.getParent() instanceof GrExpression &&
        context.getParent().getParent() instanceof GroovyFile &&
        GroovyCompletionUtil.isNewStatement(context, false);
  }
}
