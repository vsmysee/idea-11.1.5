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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * User: cdr
 */
public class RangeHighlighterTree extends RangeMarkerTree<RangeHighlighterEx> {
  private final MarkupModelImpl myMarkupModel;

  public RangeHighlighterTree(@NotNull Document document, @NotNull MarkupModelImpl markupModel) {
    super(document);
    myMarkupModel = markupModel;
  }

  @Override
  protected int compareEqualStartIntervals(@NotNull IntervalNode<RangeHighlighterEx> i1, @NotNull IntervalNode<RangeHighlighterEx> i2) {
    RHNode o1 = (RHNode)i1;
    RHNode o2 = (RHNode)i2;
    if (o1.myLayer != o2.myLayer) {
      return o2.myLayer - o1.myLayer;
    }
    return super.compareEqualStartIntervals(i1, i2);
  }

  @NotNull
  @Override
  protected RHNode createNewNode(@NotNull RangeHighlighterEx key, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    return new RHNode(key, start, end, greedyToLeft, greedyToRight,layer);
  }

  class RHNode extends RangeMarkerTree<RangeHighlighterEx>.RMNode {
    final int myLayer;

    public RHNode(@NotNull final RangeHighlighterEx key,
                  int start,
                  int end,
                  boolean greedyToLeft,
                  boolean greedyToRight,
                  int layer) {
      super(key, start, end, greedyToLeft, greedyToRight);
      myLayer = layer;
    }

    //range highlighters are strongly referenced
    @Override
    protected Getter<RangeHighlighterEx> createGetter(@NotNull RangeHighlighterEx interval) {
      return (RangeHighlighterImpl)interval;
    }
  }

  @Override
  void reportInvalidation(RangeHighlighterEx markerEx, Object reason) {
    super.reportInvalidation(markerEx, reason);
    myMarkupModel.fireBeforeRemoved(markerEx);
  }
}
