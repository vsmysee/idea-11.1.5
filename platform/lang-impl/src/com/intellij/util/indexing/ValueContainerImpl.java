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

package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import com.intellij.util.containers.EmptyIterator;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import gnu.trove.TObjectObjectProcedure;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */
class ValueContainerImpl<Value> extends UpdatableValueContainer<Value> implements Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.ValueContainerImpl");
  private final static Object myNullValue = new Object();
  private THashMap<Value, Object> myInputIdMapping;

  public ValueContainerImpl() {
    // per statistic most maps (80%) has one value
    myInputIdMapping = new THashMap<Value, Object>(1);
  }

  @Override
  public void addValue(int inputId, Value value) {
    value = maskNull(value);
    final Object input = myInputIdMapping.get(value);
    if (input == null) {
      //idSet = new TIntHashSet(3, 0.98f);
      myInputIdMapping.put(value, inputId);
    }
    else {
      final TIntHashSet idSet;
      if (input instanceof Integer) {
        idSet = new IdSet(3, 0.98f);
        idSet.add(((Integer)input).intValue());
        myInputIdMapping.put(value, idSet);
      }
      else {
        idSet = (TIntHashSet)input;
      }
      idSet.add(inputId);
    }
  }

  @Override
  public int size() {
    return myInputIdMapping.size();
  }

  @Override
  public void removeAssociatedValue(int inputId) {
    if (myInputIdMapping.isEmpty()) return;
    List<Value> toRemove = null;
    for (final Iterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();
      if (isAssociated(value, inputId)) {
        if (toRemove == null) toRemove = new SmartList<Value>(value);
        else {
          LOG.error("Expected only one value per-inputId");
          toRemove.add(value);
        }
      }
    }

    if (toRemove != null) {
      for (Value value : toRemove) {
        removeValue(inputId, value);
      }
    }
  }

  @Override
  public boolean removeValue(int inputId, Value value) {
    if (myInputIdMapping.isEmpty()) return false; // skipping hash code for value
    value = maskNull(value);
    final Object input = myInputIdMapping.get(value);
    if (input == null) {
      return false;
    }
    if (input instanceof TIntHashSet) {
      final TIntHashSet idSet = (TIntHashSet)input;
      final boolean reallyRemoved = idSet.remove(inputId);
      if (reallyRemoved) {
        idSet.compact();
      }
      if (!idSet.isEmpty()) {
        return reallyRemoved;
      }
    }
    else if (input instanceof Integer) {
      if (((Integer)input).intValue() != inputId) {
        return false;
      }
    }
    myInputIdMapping.remove(value);
    return true;
  }

  private Value maskNull(Value value) {
    if (value == null) {
      return (Value)myNullValue;
    }
    return value;
  }

  @Override
  public Iterator<Value> getValueIterator() {
    if (myInputIdMapping.isEmpty()) {
      return EmptyIterator.getInstance();
    }

    return new Iterator<Value>() {
      final Iterator<Value> iterator = myInputIdMapping.keySet().iterator();
      
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public Value next() {
        Value next = iterator.next();
        if (next == myNullValue) next = null;
        return next;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public List<Value> toValueList() {
    if (myInputIdMapping.isEmpty()) {
      return Collections.emptyList();
    }
    return new ArrayList<Value>(myInputIdMapping.keySet());
  }

  @Override
  public boolean isAssociated(Value value, final int inputId) {
    value = maskNull(value);
    final Object input = myInputIdMapping.get(value);
    if (input instanceof TIntHashSet) {
      return ((TIntHashSet)input).contains(inputId);
    }
    if (input instanceof Integer ){
      return inputId == ((Integer)input).intValue();
    }
    return false;
  }

  @Override
  public IntPredicate getValueAssociationPredicate(Value value) {
    final Object input = myInputIdMapping.get(value);
    if (input == null) return EMPTY_PREDICATE;
    if (input instanceof Integer) {
      return new IntPredicate() {
        final int myId = (Integer)input;
        @Override
        public boolean contains(int id) {
          return id == myId;
        }
      };
    }
    return new IntPredicate() {
      final TIntHashSet mySet = (TIntHashSet)input;
      @Override
      boolean contains(int id) {
        return mySet.contains(id);
      }
    };
  }

  @Override
  public IntIterator getInputIdsIterator(Value value) {
    value = maskNull(value);
    final Object input = myInputIdMapping.get(value);
    final IntIterator it;
    if (input instanceof TIntHashSet) {
      it = new IntSetIterator((TIntHashSet)input);
    }
    else if (input instanceof Integer ){
      it = new SingleValueIterator(((Integer)input).intValue());
    }
    else {
      it = EMPTY_ITERATOR;
    }
    return it;
  }

  @Override
  public ValueContainerImpl<Value> clone() {
    try {
      final ValueContainerImpl clone = (ValueContainerImpl)super.clone();
      clone.myInputIdMapping = mapCopy(myInputIdMapping);
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public static final IntIterator EMPTY_ITERATOR = new IntIterator() {
    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public int next() {
      return 0;
    }

    @Override
    public int size() {
      return 0;
    }
  };

  public ValueContainerImpl<Value> copy() {
    final ValueContainerImpl<Value> container = new ValueContainerImpl<Value>();
    myInputIdMapping.forEachEntry(new TObjectObjectProcedure<Value, Object>() {
      @Override
      public boolean execute(Value key, Object val) {
        if (val instanceof TIntHashSet) {
          container.myInputIdMapping.put(key, ((TIntHashSet)val).clone());
        } else {
          container.myInputIdMapping.put(key, val);
        }
        return true;
      }
    });
    return container;
  }

  private static class SingleValueIterator implements IntIterator {
    private final int myValue;
    private boolean myValueRead = false;

    private SingleValueIterator(int value) {
      myValue = value;
    }

    @Override
    public boolean hasNext() {
      return !myValueRead;
    }

    @Override
    public int next() {
      try {
        return myValue;
      }
      finally {
        myValueRead = true;
      }
    }

    @Override
    public int size() {
      return 1;
    }
  }

  private static class IntSetIterator implements IntIterator {
    private final TIntIterator mySetIterator;
    private final int mySize;

    public IntSetIterator(final TIntHashSet set) {
      mySetIterator = set.iterator();
      mySize = set.size();
    }

    @Override
    public boolean hasNext() {
      return mySetIterator.hasNext();
    }

    @Override
    public int next() {
      return mySetIterator.next();
    }

    @Override
    public int size() {
      return mySize;
    }
  }

  private THashMap<Value, Object> mapCopy(final THashMap<Value, Object> map) {
    if (map == null) {
      return null;
    }
    final THashMap<Value, Object> cloned = map.clone();
    cloned.forEachEntry(new TObjectObjectProcedure<Value, Object>() {
      @Override
      public boolean execute(Value key, Object val) {
        if (val instanceof TIntHashSet) {
          cloned.put(key, ((TIntHashSet)val).clone());
        }
        return true;
      }
    });

    return cloned;
  }

  private static final IntPredicate EMPTY_PREDICATE = new IntPredicate() {
    @Override
    public boolean contains(int id) {
      return false;
    }
  };

  private static class IdSet extends TIntHashSet {

    private IdSet(final int initialCapacity, final float loadFactor) {
      super(initialCapacity, loadFactor);
    }

    @Override
    public void compact() {
      if (((int)(capacity() * _loadFactor)/ Math.max(1, size())) >= 3) {
        super.compact();
      }
    }
  }

}
