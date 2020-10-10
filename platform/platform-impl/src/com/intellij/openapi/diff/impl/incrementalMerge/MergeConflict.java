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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.TextRange;

class MergeConflict extends ChangeType.ChangeSide implements DiffRangeMarker.RangeInvalidListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.MergeConflict");
  private final Change.HighlighterHolder myCommonHighlighterHolder = new Change.HighlighterHolder();
  private final MergeList myMergeList;
  private final DiffRangeMarker myCommonRange;
  private ConflictChange[] myChanges;

  private MergeConflict(TextRange commonRange, MergeList mergeList) {
    myCommonRange = new DiffRangeMarker((DocumentEx)mergeList.getBaseDocument(),commonRange, this);
    myMergeList = mergeList;
  }

  public Change.HighlighterHolder getHighlighterHolder() {
    return myCommonHighlighterHolder;
  }

  public DiffRangeMarker getRange() {
    return myCommonRange;
  }

  public static Change[] createChanges(TextRange leftMarker, TextRange baseMarker, TextRange rightMarker, MergeList mergeList) {
    MergeConflict conflict = new MergeConflict(baseMarker, mergeList);
    return conflict.createChanges(leftMarker, rightMarker);
  }

  private Change[] createChanges(TextRange leftMarker, TextRange rightMarker) {
    LOG.assertTrue(myChanges == null);
    myChanges = new ConflictChange[]{new ConflictChange(this, FragmentSide.SIDE1, leftMarker),
                                     new ConflictChange(this, FragmentSide.SIDE2, rightMarker)};
    return myChanges;
  }

  public void conflictRemoved() {
    for (ConflictChange change : myChanges) {
      change.getOriginalSide().getHighlighterHolder().removeHighlighters();
    }
    myCommonHighlighterHolder.removeHighlighters();
    myMergeList.removeChanges(myChanges);
    myCommonRange.removeListener(this);
  }

  public Document getOriginalDocument(FragmentSide mergeSide) {
    return myMergeList.getChanges(mergeSide).getDocument(MergeList.BRANCH_SIDE);
  }

  public void onRangeInvalidated() {
    conflictRemoved();
  }

  public void onChangeRemoved(FragmentSide mergeSide, ConflictChange conflictChange) {
    LOG.assertTrue(myChanges[mergeSide.getIndex()] == conflictChange);
    myChanges[mergeSide.getIndex()] = null;
  }

  public MergeList getMergeList() {
    return myMergeList;
  }
}
