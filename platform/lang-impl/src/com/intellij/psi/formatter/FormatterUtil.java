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
package com.intellij.psi.formatter;

import com.intellij.codeInsight.actions.ReformatAndOptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class FormatterUtil {

  public static final Collection<String> FORMATTER_ACTION_NAMES = Collections.unmodifiableCollection(ContainerUtil.addAll(
    new HashSet<String>(), ReformatAndOptimizeImportsProcessor.COMMAND_NAME, ReformatCodeProcessor.COMMAND_NAME
  ));

  private FormatterUtil() {
  }

  public static boolean isWhitespaceOrEmpty(@Nullable ASTNode node) {
    if (node == null) return false;
    IElementType type = node.getElementType();
    return type == TokenType.WHITE_SPACE || (type != TokenType.ERROR_ELEMENT && node.getTextLength() == 0);
  }

  @Nullable
  public static ASTNode getPrevious(@Nullable ASTNode node, @NotNull IElementType... typesToIgnore) {
    if (node == null) return null;

    ASTNode prev = node.getTreePrev();
    ASTNode parent = node.getTreeParent();
    while (prev == null && parent != null) {
      prev = parent.getTreePrev();
      parent = parent.getTreeParent();
    }

    if (prev == null) {
      return null;
    }

    for (IElementType type : typesToIgnore) {
      if (prev.getElementType() == type) {
        return getPrevious(prev, typesToIgnore);
      }
    }

    return prev;
  }

  @Nullable
  public static ASTNode getPreviousLeaf(@Nullable ASTNode node, @NotNull IElementType... typesToIgnore) {
    ASTNode prev = getPrevious(node, typesToIgnore);
    if (prev == null) {
      return null;
    }

    ASTNode result = prev;
    ASTNode lastChild = prev.getLastChildNode();
    while (lastChild != null) {
      result = lastChild;
      lastChild = lastChild.getLastChildNode();
    }

    for (IElementType type : typesToIgnore) {
      if (result.getElementType() == type) {
        return getPreviousLeaf(result, typesToIgnore);
      }
    }
    return result;
  }

  @Nullable
  public static ASTNode getPreviousNonWhitespaceLeaf(@Nullable ASTNode node) {
    if (node == null) return null;
    ASTNode treePrev = node.getTreePrev();
    if (treePrev != null) {
      ASTNode candidate = TreeUtil.getLastChild(treePrev);
      if (candidate != null && !isWhitespaceOrEmpty(candidate)) {
        return candidate;
      }
      else {
        return getPreviousNonWhitespaceLeaf(candidate);
      }
    }
    final ASTNode treeParent = node.getTreeParent();

    if (treeParent == null || treeParent.getTreeParent() == null) {
      return null;
    }
    else {
      return getPreviousNonWhitespaceLeaf(treeParent);
    }
  }

  @Nullable
  public static ASTNode getPreviousNonWhitespaceSibling(@Nullable ASTNode node) {
    ASTNode prevNode = node == null ? null : node.getTreePrev();
    while (prevNode != null && isWhitespaceOrEmpty(prevNode)) {
      prevNode = prevNode.getTreePrev();
    }
    return prevNode;
  }

  @Nullable
  public static ASTNode getNextNonWhitespaceSibling(@Nullable ASTNode node) {
    ASTNode next = node == null ? null : node.getTreeNext();
    while (next != null && isWhitespaceOrEmpty(next)) {
      next = next.getTreeNext();
    }
    return next;
  }

  public static boolean isPrecededBy(@Nullable ASTNode node, IElementType expectedType) {
    ASTNode prevNode = node == null ? null : node.getTreePrev();
    while (prevNode != null && isWhitespaceOrEmpty(prevNode)) {
      prevNode = prevNode.getTreePrev();
    }
    if (prevNode == null) return false;
    return prevNode.getElementType() == expectedType;
  }

  public static boolean isPrecededBy(@Nullable ASTNode node, TokenSet expectedTypes) {
    ASTNode prevNode = node == null ? null : node.getTreePrev();
    while (prevNode != null && isWhitespaceOrEmpty(prevNode)) {
      prevNode = prevNode.getTreePrev();
    }
    if (prevNode == null) return false;
    return expectedTypes.contains(prevNode.getElementType());
  }

  public static boolean isFollowedBy(@Nullable ASTNode node, IElementType expectedType) {
    ASTNode nextNode = node == null ? null : node.getTreeNext();
    while (nextNode != null && isWhitespaceOrEmpty(nextNode)) {
      nextNode = nextNode.getTreeNext();
    }
    if (nextNode == null) return false;
    return nextNode.getElementType() == expectedType;
  }

  public static boolean isIncomplete(@Nullable ASTNode node) {
    ASTNode lastChild = node == null ? null : node.getLastChildNode();
    while (lastChild != null && lastChild.getElementType() == TokenType.WHITE_SPACE) {
      lastChild = lastChild.getTreePrev();
    }
    if (lastChild == null) return false;
    if (lastChild.getElementType() == TokenType.ERROR_ELEMENT) return true;
    return isIncomplete(lastChild);
  }

  public static boolean containsWhiteSpacesOnly(@Nullable ASTNode node) {
    if (node == null) return false;

    final boolean[] spacesOnly = {true};
    ((TreeElement)node).acceptTree(new RecursiveTreeElementWalkingVisitor() {
      @Override
      public void visitComposite(CompositeElement composite) {
        if (!spacesOnly(composite)) {
          super.visitComposite(composite);
        }
      }

      @Override
      public void visitLeaf(LeafElement leaf) {
        if (!spacesOnly(leaf)) {
          spacesOnly[0] = false;
          stopWalking();
        }
      }
    });

    return spacesOnly[0];
  }

  private static boolean spacesOnly(@Nullable TreeElement node) {
    if (node == null) return false;

    if (isWhitespaceOrEmpty(node)) return true;
    for (WhiteSpaceFormattingStrategy strategy : WhiteSpaceFormattingStrategyFactory.getAllStrategies()) {
      if (strategy.containsWhitespacesOnly(node)) {
        return true;
      }
    }
    return false;
  }

  public static void replaceWhiteSpace(final String whiteSpace,
                                       final ASTNode leafElement,
                                       final IElementType whiteSpaceToken,
                                       final @Nullable TextRange textRange) {
    final CharTable charTable = SharedImplUtil.findCharTableByTree(leafElement);

    if (textRange != null && textRange.getStartOffset() > leafElement.getTextRange().getStartOffset() &&
        textRange.getEndOffset() < leafElement.getTextRange().getEndOffset()) {
      StringBuilder newText = createNewLeafChars(leafElement, textRange, whiteSpace);
      LeafElement newElement =
        Factory.createSingleLeafElement(leafElement.getElementType(), newText, charTable, leafElement.getPsi().getManager());

      leafElement.getTreeParent().replaceChild(leafElement, newElement);
      return;
    }

    ASTNode treePrev = findPreviousWhiteSpace(leafElement, whiteSpaceToken);
    if (treePrev == null) {
      treePrev = getWsCandidate(leafElement);
    }

    if (treePrev != null &&
        treePrev.getText().trim().length() == 0 &&
        treePrev.getElementType() != whiteSpaceToken &&
        treePrev.getTextLength() > 0 &&
        whiteSpace.length() >
        0) {
      LeafElement whiteSpaceElement =
        Factory.createSingleLeafElement(treePrev.getElementType(), whiteSpace, charTable, SharedImplUtil.getManagerByTree(leafElement));

      ASTNode treeParent = treePrev.getTreeParent();
      treeParent.replaceChild(treePrev, whiteSpaceElement);
    }
    else {
      LeafElement whiteSpaceElement =
        Factory.createSingleLeafElement(whiteSpaceToken, whiteSpace, charTable, SharedImplUtil.getManagerByTree(leafElement));

      if (treePrev == null) {
        if (whiteSpace.length() > 0) {
          addWhiteSpace(leafElement, whiteSpaceElement);
        }
      }
      else {
        if (!(treePrev.getElementType() == whiteSpaceToken)) {
          if (whiteSpace.length() > 0) {
            addWhiteSpace(treePrev, whiteSpaceElement);
          }
        }
        else {
          if (treePrev.getElementType() == whiteSpaceToken) {
            final CompositeElement treeParent = (CompositeElement)treePrev.getTreeParent();
            if (whiteSpace.length() > 0) {
              //          LOG.assertTrue(textRange == null || treeParent.getTextRange().equals(textRange));
              treeParent.replaceChild(treePrev, whiteSpaceElement);
            }
            else {
              treeParent.removeChild(treePrev);
            }

            // There is a possible case that more than one PSI element is matched by the target text range.
            // That is the case, for example, for Python's multi-line expression. It may looks like below:
            //     import contextlib,\
            //       math, decimal
            // Here single range contains two blocks: '\' & '\n  '. So, we may want to replace that range to another text, hence,
            // we replace last element located there with it ('\n  ') and want to remove any remaining elements ('\').
            ASTNode removeCandidate = findPreviousWhiteSpace(whiteSpaceElement, whiteSpaceToken);
            while (textRange != null && removeCandidate != null && removeCandidate.getStartOffset() >= textRange.getStartOffset()) {
              treePrev = findPreviousWhiteSpace(removeCandidate, whiteSpaceToken);
              removeCandidate.getTreeParent().removeChild(removeCandidate);
              removeCandidate = treePrev;
            }
            //treeParent.subtreeChanged();
          }
        }
      }
    }
  }

  @Nullable
  private static ASTNode findPreviousWhiteSpace(final ASTNode leafElement, final IElementType whiteSpaceTokenType) {
    final int offset = leafElement.getTextRange().getStartOffset() - 1;
    if (offset < 0) return null;
    final PsiElement found = SourceTreeToPsiMap.treeElementToPsi(leafElement).getContainingFile().findElementAt(offset);
    if (found == null) return null;
    final ASTNode treeElement = found.getNode();
    if (treeElement != null && treeElement.getElementType() == whiteSpaceTokenType) return treeElement;
    return null;
  }

  @Nullable
  private static ASTNode getWsCandidate(@Nullable ASTNode node) {
    if (node == null) return null;
    ASTNode treePrev = node.getTreePrev();
    if (treePrev != null) {
      if (treePrev.getElementType() == TokenType.WHITE_SPACE) {
        return treePrev;
      }
      else if (treePrev.getTextLength() == 0) {
        return getWsCandidate(treePrev);
      }
      else {
        return node;
      }
    }
    final ASTNode treeParent = node.getTreeParent();

    if (treeParent == null || treeParent.getTreeParent() == null) {
      return node;
    }
    else {
      return getWsCandidate(treeParent);
    }
  }

  private static StringBuilder createNewLeafChars(final ASTNode leafElement, final TextRange textRange, final String whiteSpace) {
    final TextRange elementRange = leafElement.getTextRange();
    final String elementText = leafElement.getText();

    final StringBuilder result = new StringBuilder();

    if (elementRange.getStartOffset() < textRange.getStartOffset()) {
      result.append(elementText.substring(0, textRange.getStartOffset() - elementRange.getStartOffset()));
    }

    result.append(whiteSpace);

    if (elementRange.getEndOffset() > textRange.getEndOffset()) {
      result.append(elementText.substring(textRange.getEndOffset() - elementRange.getStartOffset()));
    }

    return result;
  }

  private static void addWhiteSpace(final ASTNode treePrev, final LeafElement whiteSpaceElement) {
    for (WhiteSpaceFormattingStrategy strategy : WhiteSpaceFormattingStrategyFactory.getAllStrategies()) {
      if (strategy.addWhitespace(treePrev, whiteSpaceElement)) {
        return;
      }
    }

    final ASTNode treeParent = treePrev.getTreeParent();
    treeParent.addChild(whiteSpaceElement, treePrev);
  }


  public static void replaceLastWhiteSpace(final ASTNode astNode, final String whiteSpace, final TextRange textRange) {
    ASTNode lastWS = TreeUtil.findLastLeaf(astNode);
    if (lastWS.getElementType() != TokenType.WHITE_SPACE) {
      lastWS = null;
    }
    if (lastWS != null && !lastWS.getTextRange().equals(textRange)) {
      return;
    }
    if (whiteSpace.length() == 0 && lastWS == null) {
      return;
    }
    if (lastWS != null && whiteSpace.length() == 0) {
      lastWS.getTreeParent().removeRange(lastWS, null);
      return;
    }

    LeafElement whiteSpaceElement = ASTFactory.whitespace(whiteSpace);

    if (lastWS == null) {
      astNode.addChild(whiteSpaceElement, null);
    }
    else {
      ASTNode treeParent = lastWS.getTreeParent();
      treeParent.replaceChild(lastWS, whiteSpaceElement);
    }
  }
}
