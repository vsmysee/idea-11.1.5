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
package com.intellij.psi.tree;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface for token types returned from lexical analysis and for types
 * of nodes in the AST tree. All used element types are added to a registry which
 * can be enumerated or accessed by index.
 *
 * @see com.intellij.lexer.Lexer#getTokenType()
 * @see com.intellij.lang.ASTNode#getElementType()
 */
public class IElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.tree.IElementType");

  public static final short FIRST_TOKEN_INDEX = 1;
  private static short ourCounter = FIRST_TOKEN_INDEX;

  private static final short MAX_INDEXED_TYPES = 15000;
  private static final List<IElementType> ourRegistry = new ArrayList<IElementType>(700);
  private final short myIndex;

  /**
   * Default enumeration predicate which matches all token types.
   *
   * @see #enumerate(Predicate)
   */
  public static final Predicate TRUE = new Predicate() {
    @Override
    public boolean matches(IElementType type) {
      return true;
    }
  };

  static int getAllocatedTypesCount() {
    return ourCounter;
  }

  public static final IElementType[] EMPTY_ARRAY = new IElementType[0];
  private final String myDebugName;
  @NotNull private final Language myLanguage;

  /**
   * Enumerates all registered token types which match the specified predicate.
   *
   * @param p the predicate which should be matched by the element types.
   * @return the array of matching element types.
   */
  public static IElementType[] enumerate(Predicate p) {
    List<IElementType> matches = new ArrayList<IElementType>();
    IElementType[] copy;
    synchronized (ourRegistry) {
      copy = ourRegistry.toArray(new IElementType[ourRegistry.size()]);
    }
    for (IElementType value : copy) {
      if (p.matches(value)) {
        matches.add(value);
      }
    }
    return matches.toArray(new IElementType[matches.size()]);
  }

  /**
   * Creates and registers a new element type for the specified language.
   *
   * @param debugName the name of the element type, used for debugging purposes.
   * @param language  the language with which the element type is associated.
   */
  public IElementType(@NotNull @NonNls String debugName, @Nullable Language language) {
    this(debugName, language, true);
  }

  protected IElementType(String debugName, Language language, final boolean register) {
    myDebugName = debugName;
    myLanguage = language == null ? Language.ANY : language;
    if (register) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      myIndex = ourCounter++;
      LOG.assertTrue(ourCounter < MAX_INDEXED_TYPES, "Too many element types registered. Out of (short) range.");
      synchronized (ourRegistry) {
        ourRegistry.add(this);
      }
    }
    else {
      myIndex = -1;
    }
  }

  /**
   * Returns the language associated with the element type.
   *
   * @return the associated language.
   */
  @NotNull
  public Language getLanguage() {
    return myLanguage;
  }

  /**
   * Returns the index of the element type in the table of all registered element
   * types.
   *
   * @return the element type index.
   */
  public final short getIndex() {
    return myIndex;
  }

  public String toString() {
    return myDebugName;
  }

  /**
   * Returns the element type registered at the specified index.
   *
   * @param idx the index for which the element type should be returned.
   * @return the element type at the specified index.
   */
  public static IElementType find(short idx) {
    synchronized (ourRegistry) {
      if (idx == 0) return ourRegistry.get(0); // We've changed FIRST_TOKEN_INDEX from 0 to 1. This is just for old plugins to avoid crashes.  
      return ourRegistry.get(idx - FIRST_TOKEN_INDEX);
    }
  }

  /**
   * Predicate for matching element types.
   *
   * @see IElementType#enumerate(Predicate)
   */
  public interface Predicate {
    boolean matches(IElementType type);
  }

  /**
   * Controls whitespace balancing behavior of PsiBuilder.
   * <p>By default, empty composite elements (containing no children) are bounded to the right neighbour, forming following tree:
   * <pre>
   *  [previous_element]
   *  [whitespace]
   *  [empty_element]
   *    &lt;empty&gt;
   *  [next_element]
   * </pre>
   * <p>Left-bound elements are bounded to the left neighbour instead:
   * <pre>
   *  [previous_element]
   *  [empty_element]
   *    &lt;empty&gt;
   *  [whitespace]
   *  [next_element]
   * </pre>
   * <p>See {@linkplain com.intellij.lang.impl.PsiBuilderImpl#prepareLightTree()} for details.
   * @return true if empty elements of this type should be bound to the left.
   */
  public boolean isLeftBound() {
    return false;
  }
}