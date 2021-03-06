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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.DisposableIterator;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Trinity;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.WalkingState;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import gnu.trove.TLongHashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * User: cdr
 */
public abstract class IntervalTreeImpl<T extends MutableInterval> extends RedBlackTree<T> implements IntervalTree<T> {
  private int keySize; // number of all intervals, counting all duplicates, some of them maybe gced
  protected final ReadWriteLock l = new ReentrantReadWriteLock();

  protected abstract int compareEqualStartIntervals(@NotNull IntervalNode<T> i1, @NotNull IntervalNode<T> i2);
  private final ReferenceQueue<T> myReferenceQueue = new ReferenceQueue<T>();
  private int deadReferenceCount;

  protected static class IntervalNode<E extends MutableInterval> extends RedBlackTree.Node<E> implements MutableInterval {
    private volatile int myStart;
    private volatile int myEnd;
    private volatile boolean isValid = true;
    protected final List<Getter<E>> intervals;
    protected int maxEnd; // max of all intervalEnd()s among all children.
    protected int delta;  // delta of startOffset. getStartOffset() = myStartOffset + Sum of deltas up to root

    private volatile long cachedDeltaUpToRoot; // field (packed to long for atomicity) containing deltaUpToRoot, node modCount and allDeltasUpAreNull flag
    // fields are packed as following
    //  private int modCount; // if it equals to the com.intellij.openapi.editor.impl.RedBlackTree.modCount then deltaUpToRoot can be used, otherwise it is expired
    //  private int deltaUpToRoot; // sum of all deltas up to the root (including this node' delta). Has valid value only if modCount == IntervalTreeImpl.this.modCount
    //  private boolean allDeltasUpAreNull;  // true if all deltas up the tree (including this node) are 0. Has valid value only if modCount == IntervalTreeImpl.this.modCount

    private final IntervalTreeImpl<E> myIntervalTree;

    public IntervalNode(IntervalTreeImpl<E> intervalTree, @NotNull E key, int start, int end) {
      // maxEnd == 0 so to not disrupt existing maxes
      myIntervalTree = intervalTree;
      myStart = start;
      myEnd = end;
      intervals = new SmartList<Getter<E>>(createGetter(key));
    }

    @Override
    public IntervalNode<E> getLeft() {
      return (IntervalNode<E>)left;
    }

    @Override
    public IntervalNode<E> getRight() {
      return (IntervalNode<E>)right;
    }

    @Override
    public IntervalNode<E> getParent() {
      return (IntervalNode<E>)parent;
    }

    @Override
    public boolean processAliveKeys(@NotNull Processor<? super E> processor) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < intervals.size(); i++) {
        Getter<E> interval = intervals.get(i);
        E key = interval.get();
        if (key != null && !processor.process(key)) return false;
      }
      return true;
    }

    @Override
    public boolean hasAliveKey(boolean purgeDead) {
      boolean hasAliveInterval = false;
      for (int i = intervals.size() - 1; i >= 0; i--) {
        Getter<E> interval = intervals.get(i);
        if (interval.get() != null) {
          hasAliveInterval = true;
          if (purgeDead) {
            continue;
          }
          else {
            break;
          }
        }
        if (purgeDead) {
          myIntervalTree.assertUnderWriteLock();
          removeIntervalInternal(i);
        }
      }
      return hasAliveInterval;
    }

    // removes interval and the node, if node became empty
    // returns true if node was removed
    private boolean removeInterval(@NotNull E key) {
      myIntervalTree.checkBelongsToTheTree(key, true);
      myIntervalTree.assertUnderWriteLock();
      for (int i = intervals.size() - 1; i >= 0; i--) {
        Getter<E> interval = intervals.get(i);
        E t = interval.get();
        if (t == key) {
          removeIntervalInternal(i);
          if (intervals.isEmpty()) {
            myIntervalTree.removeNode(this);
            return true;
          }
          return false;
        }
      }
      assert false: "interval not found: "+key +"; "+ intervals;
      return false;
    }

    public void removeIntervalInternal(int i) {
      intervals.remove(i);
      assert myIntervalTree.keySize > 0 : myIntervalTree.keySize;
      myIntervalTree.keySize--;
    }

    public void addInterval(@NotNull E interval) {
      myIntervalTree.assertUnderWriteLock();
      intervals.add(createGetter(interval));
      myIntervalTree.keySize++;
      myIntervalTree.setNode(interval, this);
    }

    protected Getter<E> createGetter(@NotNull E interval) {
      return new WeakReferencedGetter<E>(interval, myIntervalTree.myReferenceQueue);
    }

    private static class WeakReferencedGetter<T> extends WeakReference<T> implements Getter<T> {
      public WeakReferencedGetter(T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
      }

      @NonNls
      @Override
      public String toString() {
        return "Ref: " + get();
      }
    }

    protected int computeDeltaUpToRoot() {
      restart:
      while (true) { // have to restart on failure to update cached offsets in case of concurrent modification
        if (!isValid()) return 0;
        int treeModCount = myIntervalTree.modCount;
        long packedOffsets = cachedDeltaUpToRoot;
        if (modCount(packedOffsets) == treeModCount) {
          return deltaUpToRoot(packedOffsets);
        }
        try {
          myIntervalTree.l.readLock().lock();

          IntervalNode<E> node = this;
          IntervalNode<E> treeRoot = myIntervalTree.getRoot();
          if (treeRoot == null) return delta; // someone modified the tree in the meantime
          int deltaUp = 0;
          boolean allDeltasAreNull = true;
          int height = 0;
          long path = 0; // path to this node from the root; 0 bit means we choose left subtree, 1 bit means we choose right subtree
          while (node != treeRoot) {
            long nodePackedOffsets = node.cachedDeltaUpToRoot;
            if (node.isValid() && modCount(nodePackedOffsets) == treeModCount) {
              deltaUp = deltaUpToRoot(nodePackedOffsets) - node.delta;
              allDeltasAreNull = allDeltasUpAreNull(nodePackedOffsets);
              break;
            }
            IntervalNode<E> parent = node.getParent();
            if (parent == null) {
              return deltaUp;  // can happen when remove node and explicitly set valid to true (e.g. in RangeMarkerTree)
            }
            path = (path << 1) |  (parent.getLeft() == node ? 0 : 1);
            node = parent;
            height++;
          }
          // path to this node fits to long
          assert height < 63 : height;

          // cache deltas in every node from the root down this
          while (true) {
            if (node.isValid()) {
              int nodeDelta = node.delta;
              deltaUp += nodeDelta;
              allDeltasAreNull &= nodeDelta == 0;
              if (!node.tryToSetCachedValues(deltaUp, allDeltasAreNull, treeModCount)) {
                continue restart;
              }
            }

            if (node == this) break;
            node = (path & 1) == 0 ? node.getLeft() : node.getRight();
            path >>= 1;
            if (node == null) return deltaUp; // can only happen in case of concurrently modification
          }

          assert deltaUp == 0 || !allDeltasAreNull;
          return deltaUp;
        }
        finally {
          myIntervalTree.l.readLock().unlock();
        }
      }
    }

    protected int changeDelta(int change) {
      if (change != 0) {
        setCachedValues(0, false, 0); // deltaUpToRoot is not valid anymore
        return delta += change;
      }
      return delta;
    }
    protected void clearDelta() {
      if (delta != 0) {
        setCachedValues(0, false, 0); // deltaUpToRoot is not valid anymore
        delta = 0;
      }
    }

    @Override
    public int setIntervalStart(int start) {
      return myStart = start;
    }

    @Override
    public int setIntervalEnd(int end) {
      return myEnd = end;
    }

    @Override
    public boolean isValid() {
      return isValid;
    }

    @Override
    public boolean setValid(boolean value) {
      return isValid = value;
    }

    @Override
    public int intervalStart() {
      return myStart;
    }

    @Override
    public int intervalEnd() {
      return myEnd;
    }

    public IntervalTreeImpl<E> getTree() {
      return myIntervalTree;
    }

    /**
     *     packing/unpacking cachedDeltaUpToRoot field parts
     *     Bits layout:
     *     XXXXXXXXNMMMMMMMM where
     *     XXXXXXXX - 31bit int containing cached delta up to root
     *     N        - 1bit flag.  if set then all deltas up to root are null
     *     MMMMMMMM - 32bit int containing this node modification count
     */
    private static AtomicFieldUpdater<IntervalNode, Long> cachedDeltaUpdater = AtomicFieldUpdater.forLongField(IntervalNode.class);

    private void setCachedValues(int deltaUpToRoot, boolean allDeltaUpToRootAreNull, int modCount) {
      cachedDeltaUpToRoot = packValues(deltaUpToRoot, allDeltaUpToRootAreNull, modCount);
    }

    private static long packValues(long deltaUpToRoot, boolean allDeltaUpToRootAreNull, int modCount) {
      return deltaUpToRoot << 33 | (allDeltaUpToRootAreNull ? 0x100000000L : 0) | modCount;
    }

    private boolean tryToSetCachedValues(int deltaUpToRoot, boolean allDeltasUpAreNull, int treeModCount) {
      if (myIntervalTree.modCount != treeModCount) return false;
      long newValue = packValues(deltaUpToRoot, allDeltasUpAreNull, treeModCount);
      long oldValue = cachedDeltaUpToRoot;
      return cachedDeltaUpdater.compareAndSetLong(this, oldValue, newValue);
    }

    private static boolean allDeltasUpAreNull(long packedOffsets) {
      return ((packedOffsets >> 32) & 1) != 0;
    }
    private static int modCount(long packedOffsets) {
      return (int)packedOffsets;
    }
    private static int deltaUpToRoot(long packedOffsets) {
      return (int)(packedOffsets >> 33);
    }

    @NonNls
    @Override
    public String toString() {
      return "Node: " + intervals;
    }
  }

  private void assertUnderWriteLock() {
    assert isAcquired(l.writeLock()) : l.writeLock();
  }
  private static boolean isAcquired(Lock l) {
    String s = l.toString();
    return s.contains("Locked by thread");
  }

  private void pushDeltaFromRoot(IntervalNode<T> node) {
    if (node != null) {
      long packedOffsets = node.cachedDeltaUpToRoot;
      if (IntervalNode.allDeltasUpAreNull(packedOffsets) && node.isValid() && IntervalNode.modCount(packedOffsets) == modCount) return;
      pushDeltaFromRoot(node.getParent());
      pushDelta(node);
    }
  }

  @NotNull
  protected abstract IntervalNode<T> createNewNode(@NotNull T key, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer);
  protected abstract IntervalNode<T> lookupNode(@NotNull T key);
  protected abstract void setNode(@NotNull T key, IntervalNode<T> node);

  private int compareNodes(@NotNull IntervalNode<T> i1, int delta1, @NotNull IntervalNode<T> i2, int delta2, @NotNull List<IntervalNode<T>> invalid) {
    if (!i2.hasAliveKey(false)) {
      invalid.add(i2); //gced
    }
    int start1 = i1.intervalStart() + delta1;
    int start2 = i2.intervalStart() + delta2;
    if (start1 != start2) return start1 - start2;
    return compareEqualStartIntervals(i1, i2);
  }

  protected IntervalNode<T> getRoot() {
    return (IntervalNode<T>)root;
  }

  @Override
  public boolean process(@NotNull Processor<? super T> processor) {
    try {
      l.readLock().lock();
      checkMax(true);
      return process(getRoot(), processor, modCount);
    }
    finally {
      l.readLock().unlock();
    }
  }

  private boolean process(final IntervalNode<T> root, final Processor<? super T> processor, final int modCountBefore) {
    if (root == null) return true;

    WalkingState.TreeGuide<IntervalNode<T>> guide = getGuide();
    return WalkingState.processAll(root, guide, new Processor<IntervalNode<T>>() {
      @Override
      public boolean process(IntervalNode<T> node) {
        if (!node.processAliveKeys(processor)) return false;
        if (modCount != modCountBefore) throw new ConcurrentModificationException();
        return true;
      }
    });
  }

  @Override
  public boolean processOverlappingWith(int start, int end, @NotNull Processor<? super T> processor) {
    try {
      l.readLock().lock();
      checkMax(true);
      return processOverlappingWith(getRoot(), start, end, processor, modCount, 0);
    }
    finally {
      l.readLock().unlock();
    }
  }

  private boolean processOverlappingWith(IntervalNode<T> root,
                                         int start,
                                         int end,
                                         Processor<? super T> processor,
                                         int modCountBefore,
                                         int deltaUpToRootExclusive) {
    if (root == null) {
      return true;
    }
    assert root.isValid();

    int delta = deltaUpToRootExclusive + root.delta;
    if (start > maxEndOf(root, deltaUpToRootExclusive)) {
      return true; // right of the rightmost interval in the subtree
    }

    if (!processOverlappingWith(root.getLeft(), start, end, processor, modCountBefore, delta)) return false;
    int myStartOffset = root.intervalStart() + delta;
    int myEndOffset = root.intervalEnd() + delta;
    boolean overlaps = Math.max(myStartOffset, start) <= Math.min(myEndOffset, end);
    if (overlaps) {
      if (!root.processAliveKeys(processor)) return false;
      if (modCount != modCountBefore) throw new ConcurrentModificationException();
    }

    if (end < myStartOffset) {
      return true; // left of the root, cant be in the right subtree
    }

    return processOverlappingWith(root.getRight(), start, end, processor, modCountBefore, delta);
  }

  public boolean processOverlappingWithOutside(int start, int end, @NotNull Processor<? super T> processor) {
    try {
      l.readLock().lock();
      checkMax(true);
      return processOverlappingWithOutside(getRoot(), start, end, processor, modCount, 0);
    }
    finally {
      l.readLock().unlock();
    }
  }
  private boolean processOverlappingWithOutside(IntervalNode<T> root,
                                                int start,
                                                int end,
                                                @NotNull Processor<? super T> processor,
                                                int modCountBefore,
                                                int deltaUpToRootExclusive) {
    if (root == null) {
      return true;
    }
    assert root.isValid();

    int delta = deltaUpToRootExclusive + root.delta;
    int rootMaxEnd = maxEndOf(root, deltaUpToRootExclusive);
    int rootStartOffset = root.intervalStart() + delta;
    int rootEndOffset = root.intervalEnd() + delta;

    if (!processOverlappingWithOutside(root.getLeft(), start, end, processor, modCountBefore, delta)) return false;

    boolean toProcess = rootStartOffset < start || rootEndOffset > end;
    if (toProcess) {
      if (!root.processAliveKeys(processor)) return false;
      if (modCount != modCountBefore) throw new ConcurrentModificationException();
    }

    if (rootStartOffset >= start && rootMaxEnd <= end) return true; // cant intersect outside

    return processOverlappingWithOutside(root.getRight(), start, end, processor, modCountBefore, delta);
  }


  @Override
  public boolean processContaining(int offset, @NotNull Processor<? super T> processor) {
    try {
      l.readLock().lock();
      checkMax(true);
      return processContaining(getRoot(), offset, processor, modCount, 0);
    }
    finally {
      l.readLock().unlock();
    }
  }
  private boolean processContaining(IntervalNode<T> root,
                                    int offset,
                                    Processor<? super T> processor,
                                    int modCountBefore,
                                    int deltaUpToRootExclusive) {
    if (root == null) {
      return true;
    }
    assert root.isValid();
    int delta = deltaUpToRootExclusive + root.delta;
    if (offset > maxEndOf(root, deltaUpToRootExclusive)) {
      return true; // right of the rightmost interval in the subtree
    }

    if (!processContaining(root.getLeft(), offset, processor, modCountBefore, delta)) return false;
    int myStartOffset = root.intervalStart() + delta;
    int myEndOffset = root.intervalEnd() + delta;
    boolean overlaps = myStartOffset <= offset && offset < myEndOffset;

    if (overlaps) {
      if (!root.processAliveKeys(processor)) return false;
      if (modCount != modCountBefore) throw new ConcurrentModificationException();
    }

    if (offset < myStartOffset) {
      return true; // left of the root, cant be in the right subtree
    }

    return processContaining(root.getRight(), offset, processor, modCountBefore, delta);
  }

  @NotNull
  DisposableIterator<T> overlappingIterator(final int startOffset, final int endOffset) {
    ProperTextRange.assertProperRange(startOffset, endOffset, "");

    l.readLock().lock();

    try {
      final IntervalNode<T> firstOverlap = findMinOverlappingWith(getRoot(), new TextRangeInterval(startOffset, endOffset), modCount, 0);
      if (firstOverlap == null) {
        l.readLock().unlock();
        return DisposableIterator.EMPTY;
      }
      final int firstOverlapDelta = firstOverlap.computeDeltaUpToRoot();
      final int firstOverlapStart = firstOverlap.intervalStart() + firstOverlapDelta;
      final int modCountBefore = modCount;

      return new DisposableIterator<T>() {
        private IntervalNode<T> currentNode = firstOverlap;
        private int deltaUpToRootExclusive = firstOverlapDelta-firstOverlap.delta;
        private int indexInCurrentList = 0;
        private T current;

        @Override
        public boolean hasNext() {
          if (current != null) return true;
          if (currentNode == null) return false;

          if (modCount != modCountBefore) throw new ConcurrentModificationException();
          while (indexInCurrentList != currentNode.intervals.size()) {
            T t = currentNode.intervals.get(indexInCurrentList++).get();
            if (t != null) {
              current = t;
              return true;
            }
          }
          indexInCurrentList = 0;
          while (true) {
            currentNode = nextNode(currentNode);
            if (currentNode == null) {
              return false;
            }
            if (overlaps(currentNode, startOffset, endOffset, deltaUpToRootExclusive)) {
              assert currentNode.intervalStart() + deltaUpToRootExclusive + currentNode.delta >= firstOverlapStart;
              indexInCurrentList = 0;
              while (indexInCurrentList != currentNode.intervals.size()) {
                T t = currentNode.intervals.get(indexInCurrentList++).get();
                if (t != null) {
                  current = t;
                  return true;
                }
              }
              indexInCurrentList = 0;
            }
          }
        }

        @Override
        public T next() {
          if (!hasNext()) throw new NoSuchElementException();
          T t = current;
          current = null;
          return t;
        }

        @Override
        public void remove() {
          throw new IncorrectOperationException();
        }

        @Override
        public void dispose() {
          l.readLock().unlock();
        }

        // next node in in-order traversal
        private IntervalNode<T> nextNode(@NotNull IntervalNode<T> root) {
          assert root.isValid() : root;
          int delta = deltaUpToRootExclusive + root.delta;
          int myMaxEnd = maxEndOf(root, deltaUpToRootExclusive);
          if (startOffset > myMaxEnd) return null; // tree changed

          // try to go right down
          IntervalNode<T> right = root.getRight();
          if (right != null) {
            int rightMaxEnd = maxEndOf(right, delta);
            if (startOffset <= rightMaxEnd) {
              int rightDelta = delta + right.delta;
              while (right.getLeft() != null && startOffset <= maxEndOf(right.getLeft(), rightDelta)) {
                right = right.getLeft();
                rightDelta += right.delta;
              }
              deltaUpToRootExclusive = rightDelta - right.delta;
              return right;
            }
          }

          // go up
          while (true) {
            IntervalNode<T> parent = root.getParent();
            if (parent == null) return null;
            if (parent.intervalStart() + deltaUpToRootExclusive > endOffset) return null; // can't move right
            deltaUpToRootExclusive -= parent.delta;

            if (parent.getLeft() == root) {
              return parent;
            }

            root = parent;
          }
        }
      };
    }
    catch (RuntimeException e) {
      l.readLock().unlock();
      throw e;
    }
    catch (Error e) {
      l.readLock().unlock();
      throw e;
    }
  }

  private boolean overlaps(IntervalNode<T> root, int startOffset, int endOffset, int deltaUpToRootExclusive) {
    if (root == null) return false;
    int delta = root.delta + deltaUpToRootExclusive;
    int start = root.intervalStart() + delta;
    int end = root.intervalEnd() + delta;
    return Math.max(start, startOffset) <= Math.min(end, endOffset);
  }

  // finds previous in the in-order traversal
  private IntervalNode<T> previous(@NotNull IntervalNode<T> node) {
    IntervalNode<T> left = node.getLeft();
    if (left != null) {
      while (left.getRight() != null) {
        left = left.getRight();
      }
      return left;
    }
    IntervalNode<T> parent = node.getParent();
    while (parent != null) {
      if (parent.getRight() == node) break;
      node = parent;
      parent = parent.getParent();
    }
    return parent;
  }

  protected IntervalNode<T> findOrInsert(@NotNull IntervalNode<T> node) {
    assertUnderWriteLock();
    node.color = Color.RED;
    node.setParent(null);
    node.setValid(true);
    node.maxEnd = 0;
    node.clearDelta();
    node.setLeft(null);
    node.setRight(null);

    List<IntervalNode<T>> gced = new SmartList<IntervalNode<T>>();
    if (root == null) {
      root = node;
    }
    else {
      IntervalNode<T> current = getRoot();
      while (true) {
        pushDelta(current);
        int compResult = compareNodes(node, 0, current, 0, gced);
        if (compResult == 0) {
          return current;
        }
        if (compResult < 0) {
          if (current.getLeft() == null) {
            current.setLeft(node);
            break;
          }
          current = current.getLeft();
        }
        else /*if (compResult > 0)*/ {
          if (current.getRight() == null) {
            current.setRight(node);
            break;
          }
          current = current.getRight();
        }
      }
      node.setParent(current);
    }
    node.setCachedValues(0, true, modCount);
    correctMaxUp(node);
    onInsertNode();
    keySize += node.intervals.size();
    insertCase1(node);
    verifyProperties();

    deleteNodes(gced);
    return node;
  }

  private void deleteNodes(@NotNull List<IntervalNode<T>> collectedAway) {
    if (collectedAway.isEmpty()) return;
    try {
      l.writeLock().lock();
      for (IntervalNode<T> node : collectedAway) {
        removeNode(node);
      }
    }
    finally {
      l.writeLock().unlock();
    }
  }

  public IntervalTreeImpl.IntervalNode<T> addInterval(@NotNull T interval, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    try {
      l.writeLock().lock();
      checkMax(true);
      processReferenceQueue();
      modCount++;
      IntervalNode<T> newNode = createNewNode(interval, start, end, greedyToLeft, greedyToRight, layer);
      IntervalNode<T> insertedNode = findOrInsert(newNode);
      if (insertedNode == newNode) {
        setNode(interval, insertedNode);
      }
      else {
        // merged
        insertedNode.addInterval(interval);
      }
      checkMax(true);
      checkBelongsToTheTree(interval, true);
      return insertedNode;
    }
    finally {
      l.writeLock().unlock();
    }
  }

  // returns true if all markers are valid
  public boolean checkMax(boolean assertInvalid) {
    if (!VERIFY) return false;

    try {
      l.readLock().lock();

      Ref<Boolean> allValid = new Ref<Boolean>(true);
      int[] keyCounter = new int[1];
      int[] nodeCounter = new int[1];
      TLongHashSet ids = new TLongHashSet(keySize);
      checkMax(getRoot(), 0, assertInvalid, allValid, keyCounter, nodeCounter, ids, true);
      if (assertInvalid) {
        assert nodeSize() == nodeCounter[0] : "node size: "+ nodeSize() +"; actual: "+nodeCounter[0];
        assert keySize == keyCounter[0] : "key size: "+ keySize +"; actual: "+keyCounter[0];
        assert keySize >= nodeSize() : keySize + "; "+nodeSize();
      }
      return allValid.get();
    }
    finally {
      l.readLock().unlock();
    }
  }

  // returns real (minStart, maxStart, maxEnd)
  private Trinity<Integer,Integer,Integer> checkMax(IntervalNode<T> root,
                                                    int deltaUpToRootExclusive,
                                                    boolean assertInvalid,
                                                    Ref<Boolean> allValid,
                                                    int[] keyCounter,
                                                    int[] nodeCounter,
                                                    TLongHashSet ids, boolean allDeltasUpAreNull) {
    if (root == null) return Trinity.create(Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE);
    long packedOffsets = root.cachedDeltaUpToRoot;
    if (IntervalNode.modCount(packedOffsets) == modCount) {
      assert IntervalNode.allDeltasUpAreNull(packedOffsets) == (root.delta == 0 && allDeltasUpAreNull);
      assert IntervalNode.deltaUpToRoot(packedOffsets) == root.delta + deltaUpToRootExclusive;
    }
    T liveInterval = null;
    for (int i = root.intervals.size() - 1; i >= 0; i--) {
      T t = root.intervals.get(i).get();
      if (t == null) continue;
      liveInterval = t;
      checkBelongsToTheTree(t, false);
      boolean added = ids.add(((RangeMarkerImpl)t).getId());
      assert added : t;
    }
    if (assertInvalid && liveInterval != null) {
      checkBelongsToTheTree(liveInterval, true);
    }

    keyCounter[0]+= root.intervals.size();
    nodeCounter[0]++;
    int delta = deltaUpToRootExclusive + (root.isValid() ? root.delta : 0);
    Trinity<Integer, Integer, Integer> l = checkMax(root.getLeft(), delta, assertInvalid, allValid, keyCounter, nodeCounter, ids, root.delta == 0 && allDeltasUpAreNull);
    int minLeftStart = l.first;
    int maxLeftStart = l.second;
    int maxLeftEnd = l.third;
    Trinity<Integer, Integer, Integer> r = checkMax(root.getRight(), delta, assertInvalid, allValid, keyCounter, nodeCounter, ids, root.delta == 0 && allDeltasUpAreNull);
    int maxRightEnd = r.third;
    int minRightStart = r.first;
    int maxRightStart = r.second;
    if (!root.isValid()) {
      allValid.set(false);
      if (assertInvalid) assert false : root;
      return Trinity.create(Math.min(minLeftStart, minRightStart), Math.max(maxLeftStart, maxRightStart), Math.max(maxRightEnd, maxLeftEnd));
    }
    IntervalNode<T> parent = root.getParent();
    if (parent != null && assertInvalid && root.hasAliveKey(false)) {
      int c = compareNodes(root, delta, parent, delta - root.delta, new SmartList<IntervalNode<T>>());
      assert c != 0;
      assert c < 0 && parent.getLeft() == root || c > 0 && parent.getRight() == root;
    }
    assert delta + root.maxEnd == Math.max(maxLeftEnd, Math.max(maxRightEnd, delta + root.intervalEnd()));
    int myStartOffset = delta + root.intervalStart();
    assert maxLeftStart <= myStartOffset;
    assert minRightStart >= myStartOffset;
    assert myStartOffset >= 0;
    assert minLeftStart == Integer.MAX_VALUE || minLeftStart <= myStartOffset;
    assert maxRightStart == Integer.MIN_VALUE || maxRightStart >= myStartOffset;
    int minStart = Math.min(minLeftStart, myStartOffset);
    int maxStart = Math.max(myStartOffset, Math.max(maxLeftStart, maxRightStart));
    assert minStart <= maxStart;
    return Trinity.create(minStart, maxStart, root.maxEnd + delta);
  }

  @Override
  protected Node<T> maximumNode(Node<T> n) {
    IntervalNode<T> root = (IntervalNode<T>)n;
    pushDelta(root.getParent());
    pushDelta(root);
    while (root.getRight() != null) {
      root = root.getRight();
      pushDelta(root);
    }
    return root;
  }

  protected void checkBelongsToTheTree(T interval, boolean assertInvalid) {
    IntervalNode<T> root = lookupNode(interval);
    if (root == null) return;
    assert root.getTree() == this : root.getTree() +"; this: "+this;
    if (!VERIFY) return;

    if (assertInvalid) {
      assert !root.intervals.isEmpty();
      boolean contains = false;
      for (int i = root.intervals.size() - 1; i >= 0; i--) {
        T key = root.intervals.get(i).get();
        if (key == null) continue;
        contains |= key == interval;
        IntervalNode<T> node = lookupNode(key);
        assert node == root : node;
        assert node.getTree() == this : node;
      }

      assert contains : root.intervals + "; " + interval;
    }

    IntervalNode<T> e = root;
    while (e.getParent() != null) e = e.getParent();
    assert e == getRoot(); // assert the node belongs to our tree
  }

  @Override
  public boolean removeInterval(@NotNull T interval) {
    if (!interval.isValid()) return false;
    try {
      l.writeLock().lock();
      modCount++;
      if (!interval.isValid()) return false;
      checkBelongsToTheTree(interval, true);
      checkMax(true);
      processReferenceQueue();

      IntervalNode<T> node = lookupNode(interval);
      if (node == null) return false;

      reportInvalidation(interval, "Explicit Dispose");

      node.removeInterval(interval);
      setNode(interval, null);

      checkMax(true);
      return true;
    }
    finally {
      l.writeLock().unlock();
    }
  }

  // run under write lock
  void removeNode(@NotNull IntervalNode<T> node) {
    deleteNode(node);
    IntervalNode<T> parent = node.getParent();
    correctMaxUp(parent);
  }

  @Override
  protected void deleteNode(@NotNull Node<T> n) {
    assertUnderWriteLock();
    IntervalNode<T> node = (IntervalNode<T>)n;
    pushDeltaFromRoot(node);
    assertAllDeltasAreNull(node);
    super.deleteNode(n);

    keySize -= node.intervals.size();
    assert keySize >= 0 : keySize;
  }

  @Override
  public int size() {
    return keySize;
  }

  // returns true if all deltas involved are still 0
  protected boolean pushDelta(IntervalNode<T> root) {
    if (root == null || !root.isValid()) return true;
    IntervalNode<T> parent = root.getParent();
    assertAllDeltasAreNull(parent);
    int delta = root.delta;
    root.setCachedValues(0, true, 0);
    if (delta != 0) {
      root.setIntervalStart(root.intervalStart() + delta);
      root.setIntervalEnd(root.intervalEnd() + delta);
      root.maxEnd += delta;
      root.delta = 0;
      //noinspection NonShortCircuitBooleanExpression
      return
      incDelta(root.getLeft(), delta) &
      incDelta(root.getRight(), delta);
    }
    root.setCachedValues(0, true, modCount);
    return true;
  }

  // returns true if all deltas involved are still 0
  private boolean incDelta(IntervalNode<T> root, int delta) {
    if (root == null) return true;
    if (root.isValid()) {
      int newDelta = root.changeDelta(delta);
      return newDelta == 0;
    }
    else {
      //noinspection NonShortCircuitBooleanExpression
      return
      incDelta(root.getLeft(), delta) &
      incDelta(root.getRight(), delta);
    }
  }

  @Override
  protected IntervalNode<T> swapWithMaxPred(Node<T> root, Node<T> maxPred) {
    checkMax(false);
    IntervalNode<T> a = (IntervalNode<T>)root;
    IntervalNode<T> d = (IntervalNode<T>)maxPred;
    Color acolor = a.color;
    Color dcolor = d.color;
    assert !a.isValid() || a.delta == 0 : a.delta;
    for (IntervalNode<T> n = a.getLeft(); n != null; n = n.getRight()) {
      assert !n.isValid() || n.delta == 0 : n.delta;
    }
    swapNodes(a, d);

    // set range of the key to be deleted so it wont disrupt maxes
    a.setValid(false);
    //a.key.setIntervalStart(d.key.intervalStart());
    //a.key.setIntervalEnd(d.key.intervalEnd());

    //correctMaxUp(a);
    a.color = dcolor;
    d.color = acolor;
    correctMaxUp(a);

    checkMax(false);
    assert a.delta == 0 : a.delta;
    assert d.delta == 0 : d.delta;
    return a;
  }
  private void swapNodes(IntervalNode<T> n1, IntervalNode<T> n2) {
    IntervalNode<T> l1 = n1.getLeft();
    IntervalNode<T> r1 = n1.getRight();
    IntervalNode<T> p1 = n1.getParent();
    IntervalNode<T> l2 = n2.getLeft();
    IntervalNode<T> r2 = n2.getRight();
    IntervalNode<T> p2 = n2.getParent();

    if (p1 != null) {
      if (p1.getLeft() == n1) p1.setLeft(n2); else p1.setRight(n2);
    }
    else {
      root = n2;
    }
    if (p2 != null) {
      if (p2.getLeft() == n2) p2.setLeft(p2 == n1 ? l2 : n1); else p2.setRight(p2 == n1 ? r2 : n1);
    }
    else {
      root = n1;
    }
    n1.setParent(p2 == n1 ? n2 : p2);
    n2.setParent(p1);

    n1.setLeft(l2);
    n2.setLeft(l1 == n2 ? n1 : l1);
    if (l1 != null) l1.setParent(n2 == l1 ? p1 : n2);
    if (r1 != null) r1.setParent(n2);
    n1.setRight(r2);
    n2.setRight(r1);
    if (l2 != null) l2.setParent(n1);
    if (r2 != null) r2.setParent(n1);
  }

  // returns real max endOffset of all intervals below
  private int maxEndOf(IntervalNode<T> node, int deltaUpToRootExclusive) {
    if (node == null) {
      return 0;
    }
    if (node.isValid()) {
      return node.maxEnd + node.delta + deltaUpToRootExclusive;
    }
    // since node is invalid, ignore node.delta
    return Math.max(maxEndOf(node.getLeft(), deltaUpToRootExclusive), maxEndOf(node.getRight(), deltaUpToRootExclusive));
  }

  // max of n.left's maxend, n.right's maxend and its own interval endOffset
  protected void correctMax(@NotNull IntervalNode<T> node, int deltaUpToRoot) {
    if (!node.isValid()) return;
    int realMax = Math.max(Math.max(maxEndOf(node.getLeft(), deltaUpToRoot), maxEndOf(node.getRight(), deltaUpToRoot)),
                           deltaUpToRoot + node.intervalEnd());
    node.maxEnd = realMax - deltaUpToRoot;
  }

  private void correctMaxUp(IntervalNode<T> node) {
    int delta = node == null ? 0 : node.computeDeltaUpToRoot();
    assert delta == 0 : delta;
    while (node != null) {
      if (node.isValid()) {
        int d = node.delta;
        correctMax(node, delta);
        delta -= d;
      }
      node = node.getParent();
    }
    assert delta == 0 : delta;
  }

  @Override
  protected void rotateRight(Node<T> n) {
    checkMax(false);
    IntervalNode<T> node1 = (IntervalNode<T>)n;
    IntervalNode<T> node2 = node1.getLeft();
    IntervalNode<T> node3 = node1.getRight();

    IntervalNode<T> parent = node1.getParent();
    int deltaUp = parent == null ? 0 : parent.computeDeltaUpToRoot();
    pushDelta(node1);
    pushDelta(node2);
    pushDelta(node3);

    super.rotateRight(node1);

    if (node3 != null) {
      correctMax(node3, deltaUp);
    }
    correctMax(node1, deltaUp);
    correctMax(node2, deltaUp);
    assertAllDeltasAreNull(node1);
    assertAllDeltasAreNull(node2);
    assertAllDeltasAreNull(node3);
    checkMax(false);
  }

  @Override
  protected void rotateLeft(Node<T> n) {
    checkMax(false);
    IntervalNode<T> node1 = (IntervalNode<T>)n;
    IntervalNode<T> node2 = node1.getLeft();
    IntervalNode<T> node3 = node1.getRight();

    IntervalNode<T> parent = node1.getParent();
    int deltaUp = parent == null ? 0 : parent.computeDeltaUpToRoot();
    pushDelta(node1);
    pushDelta(node2);
    pushDelta(node3);
    checkMax(false);
    super.rotateLeft(node1);

    if (node2 != null) {
      correctMax(node2, deltaUp);
    }
    correctMax(node1, deltaUp);
    correctMax(node3, deltaUp);
    assertAllDeltasAreNull(node1);
    assertAllDeltasAreNull(node2);
    assertAllDeltasAreNull(node3);

    checkMax(false);
  }

  @Override
  protected void replaceNode(@NotNull Node<T> node, Node<T> child) {
    IntervalNode<T> myNode = (IntervalNode<T>)node;
    pushDelta(myNode);
    pushDelta((IntervalNode<T>)child);

    super.replaceNode(node, child);
    if (child != null && myNode.isValid()) {
      ((IntervalNode<T>)child).changeDelta(myNode.delta);
      //todo correct max up to root??
    }
  }

  private void assertAllDeltasAreNull(IntervalNode<T> node) {
    if (node == null) return;
    if (!node.isValid()) return;
    assert node.delta == 0;
    long packedOffsets = node.cachedDeltaUpToRoot;
    assert IntervalNode.modCount(packedOffsets) != modCount || IntervalNode.allDeltasUpAreNull(packedOffsets);
  }

  private IntervalNode<T> findMinOverlappingWith(IntervalNode<T> root, Interval interval, int modCountBefore, int deltaUpToRootExclusive) {
    if (root == null) {
      return null;
    }
    assert root.isValid();

    int delta = deltaUpToRootExclusive + root.delta;
    if (interval.intervalStart() > maxEndOf(root, deltaUpToRootExclusive)) {
      return null; // right of the rightmost interval in the subtree
    }

    IntervalNode<T> inLeft = findMinOverlappingWith(root.getLeft(), interval, modCountBefore, delta);
    if (inLeft != null) return inLeft;
    int myStartOffset = root.intervalStart() + delta;
    int myEndOffset = root.intervalEnd() + delta;
    boolean overlaps = Math.max(myStartOffset, interval.intervalStart()) <= Math.min(myEndOffset, interval.intervalEnd());
    if (overlaps) return root;
    if (modCount != modCountBefore) throw new ConcurrentModificationException();

    if (interval.intervalEnd() < myStartOffset) {
      return null; // left of the root, cant be in the right subtree
    }

    return findMinOverlappingWith(root.getRight(), interval, modCountBefore, delta);
  }

  public void changeData(T interval, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    try {
      l.writeLock().lock();

      IntervalNode<T> node = lookupNode(interval);
      if (node == null) return;
      int before = size();
      boolean nodeRemoved = node.removeInterval(interval);
      assert nodeRemoved || !node.intervals.isEmpty();

      IntervalNode<T> insertedNode = addInterval(interval, start, end, greedyToLeft, greedyToRight, layer);
      assert node != insertedNode;

      int after = size();
      // can be gced
      assert before >= after : before +";" + after;
      checkBelongsToTheTree(interval, true);
      checkMax(true);
    }
    finally {
      l.writeLock().unlock();
    }
  }


  // called under write lock
  private void processReferenceQueue() {
    int dead = 0;
    while (myReferenceQueue.poll() != null) {
      dead++;
    }

    deadReferenceCount += dead;
    if (deadReferenceCount > Math.max(1, size() / 3)) {
      purgeDeadNodes();
      deadReferenceCount = 0;
    }
  }

  private void purgeDeadNodes() {
    assertUnderWriteLock();
    List<IntervalNode<T>> gced = new SmartList<IntervalNode<T>>();
    collectGced(getRoot(), gced);
    deleteNodes(gced);
    checkMax(true);
  }

  @Override
  public void clear() {
    process(new Processor<T>() {
      @Override
      public boolean process(T t) {
        reportInvalidation(t, "Clear all");
        return true;
      }
    });
    l.writeLock().lock();
    try {
      super.clear();
      keySize = 0;
    }
    finally {
      l.writeLock().unlock();
    }
  }

  private void collectGced(IntervalNode<T> root, List<IntervalNode<T>> gced) {
    if (root == null) return;
    if (!root.hasAliveKey(true)) gced.add(root);
    collectGced(root.getLeft(), gced);
    collectGced(root.getRight(), gced);
  }


  private void printSorted() { printSorted(getRoot());}
  private void printSorted(IntervalNode<T> root) {
    if (root == null) return;
    printSorted(root.getLeft());
    System.out.println(root);
    printSorted(root.getRight());
  }

  void reportInvalidation(T markerEx, @NonNls Object reason) {
  }

  private static class IntervalTreeGuide<T extends MutableInterval> implements WalkingState.TreeGuide<IntervalNode<T>> {
    @Override
    public IntervalNode<T> getNextSibling(@NotNull IntervalNode<T> element) {
      IntervalNode<T> parent = element.getParent();
      if (parent == null) return null;
      return parent.getLeft() == element ? parent.getRight() : null;
    }

    @Override
    public IntervalNode<T> getPrevSibling(@NotNull IntervalNode<T> element) {
      IntervalNode<T> parent = element.getParent();
      if (parent == null) return null;
      return parent.getRight() == element ? parent.getLeft() : null;
    }

    @Override
    public IntervalNode<T> getFirstChild(@NotNull IntervalNode<T> element) {
      IntervalNode<T> left = element.getLeft();
      return left == null ? element.getRight() : left;
    }

    @Override
    public IntervalNode<T> getParent(@NotNull IntervalNode<T> element) {
      return element.getParent();
    }
  }

  private static final IntervalTreeGuide INTERVAL_TREE_GUIDE_INSTANCE = new IntervalTreeGuide();
  private static <T extends MutableInterval> WalkingState.TreeGuide<IntervalNode<T>> getGuide() {
    //noinspection unchecked
    return (WalkingState.TreeGuide)INTERVAL_TREE_GUIDE_INSTANCE;
  }


  public int maxHeight() {
    return maxHeight(root);
  }

  private int maxHeight(Node<T> root) {
    return root == null ? 0 : 1 + Math.max(maxHeight(root.left), maxHeight(root.right));
  }
}
