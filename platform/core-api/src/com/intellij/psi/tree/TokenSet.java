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

import com.intellij.openapi.diagnostic.LogUtil;

import java.util.Arrays;

/**
 * A set of element types.
 */

public class TokenSet {
  public static final TokenSet EMPTY = new TokenSet();
  private final boolean[] mySet = new boolean[IElementType.getAllocatedTypesCount()] ;

  private volatile IElementType[] myTypes;

  /**
   * Returns the array of element types contained in the set.
   *
   * @return the contents of the set.
   */

  public IElementType[] getTypes() {
    IElementType[] types = myTypes;
    if (types == null) {
      int elementCount = 0;
      for (boolean bit : mySet) {
        if (bit) elementCount++;
      }

      types = new IElementType[elementCount];
      int count = 0;
      for (short i = IElementType.FIRST_TOKEN_INDEX; i < mySet.length; i++) {
        if (mySet[i]) {
          types[count++] = IElementType.find(i);
        }
      }
      
      myTypes = types;
    }

    return types;
  }

  /**
   * Returns a new token set containing the specified element types.
   *
   * @param types the element types contained in the set.
   * @return the new token set.
   */

  public static TokenSet create(IElementType... types) {
    TokenSet set = new TokenSet();
    for (IElementType type : types) {
      if (type != null) {
        final short index = type.getIndex();
        assert index >= 0 : "Unregistered elements are not allowed here: " + LogUtil.objectAndClass(type);
        set.mySet[index] = true;
      }
    }
    return set;
  }

  /**
   * Returns a token set containing the union of the specified token sets.
   *
   * @param sets the token sets to unite.
   * @return the new token set.
   */

  public static TokenSet orSet(TokenSet... sets) {
    TokenSet newSet = new TokenSet();
    for (TokenSet set : sets) {
      for (int i = 0; i < newSet.mySet.length; i++) {
        if (i >= set.mySet.length) break;
        newSet.mySet[i] |= set.mySet[i];
      }
    }
    return newSet;
  }

  /**
   * Returns a token set containing the intersection of the specified token sets.
   *
   * @param a the first token set to intersect.
   * @param b the second token set to intersect.
   * @return the new token set.
   */

  public static TokenSet andSet(TokenSet a, TokenSet b) {
    TokenSet set = new TokenSet();
    final boolean[] aset = a.mySet;
    final boolean[] bset = b.mySet;
    final boolean[] newset = set.mySet;
    final int alen = aset.length;
    final int blen = bset.length;
    final int andSize = Math.max(newset.length, Math.max(alen, blen));

    for (int i = 0; i < andSize; i++) {
      newset[i] = (i < alen && aset[i]) && (i < blen && bset[i]);
    }
    return set;
  }

  /**
   * @deprecated use {@link #contains(IElementType)} instead. This appears to be a better naming.
   */
  public boolean isInSet(IElementType t) {
    return contains(t);
  }

  /**
   * Checks if the specified element type is contained in the set.
   *
   * @param t the element type to search for.
   * @return true if the element type is found in the set, false otherwise.
   */
  public boolean contains(IElementType t) {
    if (t == null) return false;
    final short i = t.getIndex();
    return 0 <= i && i < mySet.length && mySet[i];
  }

  public TokenSet minus(TokenSet t) {
    TokenSet set = new TokenSet();
    for (int i = 0; i < mySet.length; i++) {
      set.mySet [i] = mySet [i] && (i >= t.mySet.length || !t.mySet[i]);
    }
    return set;
  }

  public static TokenSet not(TokenSet t) {
    TokenSet set = new TokenSet();
    for (int i = 0; i < t.mySet.length; i++) {
      set.mySet [i] = (i >= t.mySet.length || !t.mySet[i]);
    }
    return set;
  }

  @Override
  public String toString() {
    return Arrays.asList(getTypes()).toString();
  }
}