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

package com.intellij.psi.formatter.common;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.FormatterUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class AbstractBlock implements ASTBlock {
  public static final List<Block> EMPTY = Collections.emptyList();

  protected final ASTNode myNode;
  protected final Wrap myWrap;
  protected final Alignment myAlignment;

  private List<Block> mySubBlocks;
  private Boolean myIncomplete;

  protected AbstractBlock(@NotNull ASTNode node, @Nullable Wrap wrap, @Nullable Alignment alignment) {
    myNode = node;
    myWrap = wrap;
    myAlignment = alignment;
  }

  @Override
  @NotNull
  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  @Override
  @NotNull
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {

      final List<Block> list = buildChildren();
      mySubBlocks = list.size() > 0 ? list:EMPTY;
    }
    return mySubBlocks;
  }

  protected abstract List<Block> buildChildren();

  @Override
  public Wrap getWrap() {
    return myWrap;
  }

  @Override
  public Indent getIndent() {
    return null;
  }

  @Override
  public Alignment getAlignment() {
    return myAlignment;
  }

  @Override
  public ASTNode getNode() {
    return myNode;
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(getChildIndent(), getFirstChildAlignment());
  }

  @Nullable
  private Alignment getFirstChildAlignment() {
    List<Block> subBlocks = getSubBlocks();
    for (final Block subBlock : subBlocks) {
      Alignment alignment = subBlock.getAlignment();
      if (alignment != null) {
        return alignment;
      }
    }
    return null;
  }

  @Nullable
  protected Indent getChildIndent() {
    return null;
  }

  @Override
  public boolean isIncomplete() {
    if (myIncomplete == null) {
      myIncomplete = FormatterUtil.isIncomplete(getNode());
    }
    return myIncomplete;
  }

  @Override
  public String toString() {
    if (myNode == null) {
      return super.toString();
    }
    return myNode.getText() + " " + getTextRange();
  }
}
