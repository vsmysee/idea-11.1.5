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
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.SoftHashMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * There are moments when a computation A requires the result of computation B, which in turn requires C, which (unexpectedly) requires A.
 * If there are no other ways to solve it, it helps to track all the computations in the thread stack and return some default value when
 * asked to compute A for the second time. {@link RecursionGuard#doPreventingRecursion(Object, Computable)} does precisely this.
 *
 * It's quite useful to cache some computation results to avoid performance problems. But not everyone realises that in the above situation it's
 * incorrect to cache the results of B and C, because they all are based on the default incomplete result of the A calculation. If the actual
 * computation sequence were C->A->B->C, the result of the outer C most probably wouldn't be the same as in A->B->C->A, where it depends on
 * the null A result directly. The natural wish is that the program with cache enabled has the same results as the one without cache. In the above
 * situation the result of C would depend on the order of invocations of C and A, which can be hardly predictable in multi-threaded environments.
 *
 * Therefore if you use any kind of cache, it probably would make your program safer to cache only when it's safe to do this. See
 * {@link com.intellij.openapi.util.RecursionGuard#markStack()} and {@link com.intellij.openapi.util.RecursionGuard.StackStamp#mayCacheNow()}
 * for the advice.
 *
 * @see RecursionGuard
 * @see RecursionGuard.StackStamp
 * @author peter
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class RecursionManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.RecursionManager");
  private static final Object NULL = new Object();
  private static final ThreadLocal<CalculationStack> ourStack = new ThreadLocal<CalculationStack>() {
    @Override
    protected CalculationStack initialValue() {
      return new CalculationStack();
    }
  };

  /**
   * @see RecursionGuard#doPreventingRecursion(Object, boolean, Computable)
   */
  @SuppressWarnings("JavaDoc")
  @Nullable
  public static <T> T doPreventingRecursion(@NotNull Object key, boolean memoize, Computable<T> computation) {
    return createGuard(computation.getClass().getName()).doPreventingRecursion(key, memoize, computation);
  }

  /**
   * @param id just some string to separate different recursion prevention policies from each other
   * @return a helper object which allow you to perform reentrancy-safe computations and check whether caching will be safe.
   */
  public static RecursionGuard createGuard(@NonNls final String id) {
    return new RecursionGuard() {
      @Override
      public <T> T doPreventingRecursion(@NotNull Object key, boolean memoize, Computable<T> computation) {
        MyKey realKey = new MyKey(id, key);
        final CalculationStack stack = ourStack.get();

        if (stack.checkReentrancy(realKey)) {
          return null;
        }

        if (memoize) {
          Object o = stack.getMemoizedValue(realKey);
          if (o != null) {
            SoftHashMap<MyKey, SoftReference> map = stack.intermediateCache.get(realKey);
            if (map != null) {
              for (MyKey noCacheUntil : map.keySet()) {
                stack.prohibitResultCaching(noCacheUntil);
              }
            }

            //noinspection unchecked
            return o == NULL ? null : (T)o;
          }
        }

        int oldHash = realKey.hashCode();

        final int sizeBefore = stack.progressMap.size();
        stack.beforeComputation(realKey);
        final int sizeAfter = stack.progressMap.size();
        int startStamp = stack.memoizationStamp;

        try {
          T result = computation.compute();

          if (memoize) {
            stack.maybeMemoize(realKey, result == null ? NULL : result, startStamp);
          }

          return result;
        }
        finally {
          try {
            stack.afterComputation(realKey, sizeBefore, sizeAfter);
          }
          catch (Throwable e) {
            throw new RuntimeException("Throwable in afterComputation", e);
          }

          stack.checkDepth("4");

          if (oldHash != realKey.hashCode()) {
            throw new AssertionError("Object has changed its hashCode: " + key);
          }
        }
      }

      @Override
      public StackStamp markStack() {
        final int stamp = ourStack.get().reentrancyCount;
        return new StackStamp() {
          @Override
          public boolean mayCacheNow() {
            return stamp == ourStack.get().reentrancyCount;
          }
        };
      }

      @Override
      public List<Object> currentStack() {
        ArrayList<Object> result = new ArrayList<Object>();
        LinkedHashMap<MyKey, Integer> map = ourStack.get().progressMap;
        for (MyKey pair : map.keySet()) {
          if (pair.first.equals(id)) {
            result.add(pair.second);
          }
        }
        return result;
      }

      @Override
      public void prohibitResultCaching(Object since) {
        MyKey realKey = new MyKey(id, since);
        final CalculationStack stack = ourStack.get();
        stack.enableMemoization(realKey, stack.prohibitResultCaching(realKey));
        stack.memoizationStamp++;
      }

    };
  }

  private static class MyKey extends Pair<String, Object> {
    public MyKey(String first, Object second) {
      super(first, second);
    }
  }

  private static class CalculationStack {
    private int reentrancyCount;
    private int memoizationStamp;
    private int depth;
    private final LinkedHashMap<MyKey, Integer> progressMap = new LinkedHashMap<MyKey, Integer>();
    private final Set<MyKey> toMemoize = new THashSet<MyKey>();
    private final THashMap<MyKey, MyKey> key2ReentrancyDuringItsCalculation = new THashMap<MyKey, MyKey>();
    private final SoftHashMap<MyKey, SoftHashMap<MyKey, SoftReference>> intermediateCache = new SoftHashMap<MyKey, SoftHashMap<MyKey, SoftReference>>();
    private int enters = 0;
    private int exits = 0;

    boolean checkReentrancy(MyKey realKey) {
      if (progressMap.containsKey(realKey)) {
        enableMemoization(realKey, prohibitResultCaching(realKey));

        return true;
      }
      return false;
    }

    @Nullable
    Object getMemoizedValue(MyKey realKey) {
      SoftHashMap<MyKey, SoftReference> map = intermediateCache.get(realKey);
      if (map == null) return null;

      if (depth == 0) {
        throw new AssertionError("Memoized values with empty stack");
      }

      for (MyKey key : map.keySet()) {
        final SoftReference reference = map.get(key);
        if (reference != null) {
          final Object result = reference.get();
          if (result != null) {
            return result;
          }
        }
      }

      return null;
    }

    final void beforeComputation(MyKey realKey) {
      enters++;
      
      if (progressMap.isEmpty()) {
        assert reentrancyCount == 0 : "Non-zero stamp with empty stack: " + reentrancyCount;
      }

      checkDepth("1");

      int sizeBefore = progressMap.size();
      progressMap.put(realKey, reentrancyCount);
      depth++;

      checkDepth("2");

      int sizeAfter = progressMap.size();
      if (sizeAfter != sizeBefore + 1) {
        LOG.error("Key doesn't lead to the map size increase: " + sizeBefore + " " + sizeAfter + " " + realKey.second);
      }
    }

    final void maybeMemoize(MyKey realKey, @NotNull Object result, int startStamp) {
      if (memoizationStamp == startStamp && toMemoize.contains(realKey)) {
        SoftHashMap<MyKey, SoftReference> map = intermediateCache.get(realKey);
        if (map == null) {
          intermediateCache.put(realKey, map = new SoftHashMap<MyKey, SoftReference>());
        }
        final MyKey reentered = key2ReentrancyDuringItsCalculation.get(realKey);
        assert reentered != null;
        map.put(reentered, new SoftReference<Object>(result));
      }
    }

    final void afterComputation(MyKey realKey, int sizeBefore, int sizeAfter) {
      exits++;
      if (sizeAfter != progressMap.size()) {
        LOG.error("Map size changed: " + progressMap.size() + " " + sizeAfter + " " + realKey.second);
      }

      if (depth != progressMap.size()) {
        LOG.error("Inconsistent depth after computation; depth=" + depth + "; map=" + progressMap);
      }

      Integer value = progressMap.remove(realKey);
      depth--;
      toMemoize.remove(realKey);
      key2ReentrancyDuringItsCalculation.remove(realKey);

      if (depth == 0) {
        intermediateCache.clear();
        LOG.assertTrue(key2ReentrancyDuringItsCalculation.isEmpty(), "non-empty key2ReentrancyDuringItsCalculation");
        LOG.assertTrue(toMemoize.isEmpty(), "non-empty toMemoize");
      }

      if (sizeBefore != progressMap.size()) {
        LOG.error("Map size doesn't decrease: " + progressMap.size() + " " + sizeBefore + " " + realKey.second);
      }

      if (value == null) {
        LOG.error(realKey.second + " has changed its equals/hashCode");
      }

      reentrancyCount = value;
      checkZero();

    }

    private void enableMemoization(MyKey realKey, Set<MyKey> loop) {
      toMemoize.addAll(loop);
      List<MyKey> stack = new ArrayList<MyKey>(progressMap.keySet());

      for (MyKey key : loop) {
        final MyKey existing = key2ReentrancyDuringItsCalculation.get(key);
        if (existing == null || stack.indexOf(realKey) >= stack.indexOf(key)) {
          key2ReentrancyDuringItsCalculation.put(key, realKey);
        }
      }
    }

    private Set<MyKey> prohibitResultCaching(MyKey realKey) {
      reentrancyCount++;

      if (!checkZero()) {
        throw new AssertionError("zero1");
      }

      Set<MyKey> loop = new THashSet<MyKey>();
      boolean inLoop = false;
      for (Map.Entry<MyKey, Integer> entry: progressMap.entrySet()) {
        if (inLoop) {
          entry.setValue(reentrancyCount);
          loop.add(entry.getKey());
        }
        else if (entry.getKey().equals(realKey)) {
          inLoop = true;
        }
      }

      if (!checkZero()) {
        throw new AssertionError("zero2");
      }
      return loop;
    }

    private void checkDepth(String s) {
      int oldDepth = depth;
      if (oldDepth != progressMap.size()) {
        depth = progressMap.size();
        throw new AssertionError("_Inconsistent depth " + s + "; depth=" + oldDepth + "; enters=" + enters + "; exits=" + exits + "; map=" + progressMap);
      }
    }

    private boolean checkZero() {
      if (!progressMap.isEmpty() && !new Integer(0).equals(progressMap.get(progressMap.keySet().iterator().next()))) {
        LOG.error("Prisoner Zero has escaped: " + progressMap + "; value=" + progressMap.get(progressMap.keySet().iterator().next()));
        return false;
      }
      return true;
    }

  }

}
