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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.*;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class GroovyFileBaseImpl extends PsiFileBase implements GroovyFileBase, GrControlFlowOwner {

  private GrMethod[] myMethods = null;

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myMethods = null;
  }

  protected GroovyFileBaseImpl(FileViewProvider viewProvider, @NotNull Language language) {
    super(viewProvider, language);
  }

  public GroovyFileBaseImpl(IFileElementType root, IFileElementType root1, FileViewProvider provider) {
    this(provider, root.getLanguage());
    init(root, root1);
  }

  @NotNull
  public FileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  public String toString() {
    return "Groovy script";
  }

  public GrTypeDefinition[] getTypeDefinitions() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(GroovyElementTypes.TYPE_DEFINITION_TYPES, GrTypeDefinition.ARRAY_FACTORY);
    }

    return calcTreeElement().getChildrenAsPsiElements(GroovyElementTypes.TYPE_DEFINITION_TYPES, GrTypeDefinition.ARRAY_FACTORY);
  }

  public GrTopLevelDefinition[] getTopLevelDefinitions() {
    return findChildrenByClass(GrTopLevelDefinition.class);
  }

  public GrMethod[] getTopLevelMethods() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(GroovyElementTypes.METHOD_DEFINITION, GrMethod.ARRAY_FACTORY);
    }

    return calcTreeElement().getChildrenAsPsiElements(GroovyElementTypes.METHOD_DEFINITION, GrMethod.ARRAY_FACTORY);
  }

  @Override
  public GrMethod[] getMethods() {
    if (myMethods == null) {
      List<GrMethod> result = new ArrayList<GrMethod>();
      
      GrMethod[] methods = getTopLevelMethods();
      for (GrMethod method : methods) {
        final GrReflectedMethod[] reflectedMethods = method.getReflectedMethods();
        if (reflectedMethods.length > 0) {
          result.addAll(Arrays.asList(reflectedMethods));
        }
        else {
          result.add(method);
        }
      }

      myMethods = result.toArray(new GrMethod[result.size()]);
    }
    return myMethods;
  }

  public GrVariableDeclaration[] getTopLevelVariableDeclarations() {
    return findChildrenByClass(GrVariableDeclaration.class);
  }

  public GrTopStatement[] getTopStatements() {
    return findChildrenByClass(GrTopStatement.class);
  }

  public boolean importClass(PsiClass aClass) {
    return addImportForClass(aClass) != null;
  }

  public void removeImport(GrImportStatement importStatement) throws IncorrectOperationException {
    PsiElement before = importStatement.getPrevSibling();
    while (before instanceof PsiWhiteSpace || hasElementType(before, GroovyTokenTypes.mNLS)) {
      before = before.getPrevSibling();
    }

    PsiElement rangeStart = importStatement;
    if (before != null && !(before instanceof PsiImportStatement) && before != importStatement.getPrevSibling()) {
      rangeStart = before.getNextSibling();
      final PsiElement el = addBefore(GroovyPsiElementFactory.getInstance(getProject()).createLineTerminator(2), rangeStart);
      rangeStart=el.getNextSibling();
    }

    PsiElement rangeEnd = importStatement;
    while (true) {
      final PsiElement next = rangeEnd.getNextSibling();
      if (!(next instanceof PsiWhiteSpace) && !hasElementType(next, GroovyTokenTypes.mSEMI)) {
        break;
      }
      rangeEnd = next;
    }
    final PsiElement last = hasElementType(rangeEnd.getNextSibling(), GroovyTokenTypes.mNLS) ? rangeEnd.getNextSibling() : rangeEnd;
    if (rangeStart != null && last != null) {
      deleteChildRange(rangeStart, last);
    }
  }

  private static boolean hasElementType(PsiElement next, final IElementType type) {
    if (next == null) {
      return false;
    }
    final ASTNode astNode = next.getNode();
    if (astNode != null && astNode.getElementType() == type) {
      return true;
    }
    return false;
  }

  public void removeElements(PsiElement[] elements) throws IncorrectOperationException {
    for (PsiElement element : elements) {
      if (element.isValid()) {
        if (element.getParent() != this) throw new IncorrectOperationException();
        deleteChildRange(element, element);
      }
    }
  }

  @NotNull
  @Override
  public GrStatement[] getStatements() {
    return findChildrenByClass(GrStatement.class);
  }

  @NotNull
  public GrStatement addStatementBefore(@NotNull GrStatement statement, @Nullable GrStatement anchor) throws IncorrectOperationException {
    final PsiElement result = addBefore(statement, anchor);
    if (anchor != null) {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", anchor.getNode());
    }
    return (GrStatement) result;
  }

  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException {
    GrStatement statement = addStatementBefore(declaration, anchor);
    assert statement instanceof GrVariableDeclaration;
    return ((GrVariableDeclaration) statement);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitFile(this);
  }

  public void acceptChildren(GroovyElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement) child).accept(visitor);
      }

      child = child.getNextSibling();
    }
  }

  @NotNull
  public PsiClass[] getClasses() {
    return getTypeDefinitions();
  }

  public void clearCaches() {
    super.clearCaches();
    myControlFlow = null;
  }

  private volatile SoftReference<Instruction[]> myControlFlow = null;

  public Instruction[] getControlFlow() {
    SoftReference<Instruction[]> flow = myControlFlow;
    Instruction[] result = flow != null ? flow.get() : null;
    if (result == null) {
      result = new ControlFlowBuilder(getProject()).buildControlFlow(this);
      myControlFlow = new SoftReference<Instruction[]>(result);
    }
    return ControlFlowBuilder.assertValidPsi(result);
  }

  @Override
  public boolean isTopControlFlowOwner() {
    return false;
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    if (last instanceof GrTopStatement) {
      PsiImplUtil.deleteStatementTail(this, last);
    }
    super.deleteChildRange(first, last);
  }
}
