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
package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.diff.actions.MergeActionGroup;
import com.intellij.openapi.diff.actions.MergeOperations;
import com.intellij.openapi.diff.impl.EditorSource;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.util.GutterActionRenderer;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class DiffMarkup implements EditorSource, Disposable {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.openapi.diff.impl.highlighting.EditorTextAppender");
  private static final int LAYER = HighlighterLayer.SELECTION - 1;

  private final ArrayList<RangeHighlighter> myExtraHighLighters = new ArrayList<RangeHighlighter>();
  private final ArrayList<RangeHighlighter> myHighLighters = new ArrayList<RangeHighlighter>();
  private final HashSet<RangeHighlighter> myActionHighlighters = new HashSet<RangeHighlighter>();
  private final Project myProject;
  private final List<Disposable> myDisposables = new ArrayList<Disposable>();
  private boolean myDisposed = false;

  protected DiffMarkup(Project project, @NotNull Disposable parentDisposable) {
    myProject = project;
    Disposer.register(parentDisposable, this);
  }

  private MarkupModel getMarkupModel() {
    Editor editor = getEditor();
    return editor == null ? null : editor.getMarkupModel();
  }

  public void highlightText(Fragment fragment, boolean drawBorder, GutterIconRenderer gutterIconRenderer) {
    final TextDiffTypeEnum diffTypeEnum = fragment.getType();
    if (diffTypeEnum == null) return;
    TextDiffType type = TextDiffType.create(diffTypeEnum);
    if (type == null) return;
    TextRange range = fragment.getRange(getSide());
    TextAttributes attributes = type.getTextAttributes(getEditor());
    if (!drawBorder && range.getLength() == 0) return;
    RangeHighlighter rangeMarker;
    if (drawBorder && range.getLength() == 0) {
      TextAttributes textAttributes = new TextAttributes(null, null, attributes.getBackgroundColor(), EffectType.BOXED, Font.PLAIN);
      rangeMarker = getMarkupModel().addRangeHighlighter(range.getStartOffset(), range.getStartOffset(), LAYER, textAttributes, HighlighterTargetArea.EXACT_RANGE);
    }
    else {
      rangeMarker = getMarkupModel().addRangeHighlighter(range.getStartOffset(), range.getEndOffset(), LAYER,
                                                         attributes, HighlighterTargetArea.EXACT_RANGE);
    }
    if (gutterIconRenderer != null) {
      rangeMarker.setGutterIconRenderer(gutterIconRenderer);
    }
    Color stripeBarColor = attributes.getErrorStripeColor();
    if (stripeBarColor != null) {
      rangeMarker.setErrorStripeMarkColor(stripeBarColor);
      rangeMarker.setThinErrorStripeMark(true);
    }
    saveHighlighter(rangeMarker);
  }

  public void addLineMarker(int line, TextAttributesKey type) {
    RangeHighlighter marker = createLineMarker(type, line);
    if (marker == null) return;
    saveHighlighter(marker);
    marker.setLineMarkerRenderer(LineRenderer.bottom());
  }

  void setSeparatorMarker(int line, Consumer<Integer> consumer) {
    RangeHighlighter marker = getMarkupModel().addLineHighlighter(line, LAYER, null);
    marker.setLineSeparatorPlacement(SeparatorPlacement.TOP);
    final FragmentBoundRenderer renderer = new FragmentBoundRenderer(getEditor().getLineHeight(), getEditor(), consumer);
    marker.setLineSeparatorColor(renderer.getColor());
    marker.setLineSeparatorRenderer(renderer);
    marker.setLineMarkerRenderer(renderer);
    myExtraHighLighters.add(marker);
  }

  private RangeHighlighter createLineMarker(TextAttributesKey type, int line) {
    Color color = getLineSeparatorColorForType(type);
    RangeHighlighter lastHighlighter = getLastHighlighter();
    if (lastHighlighter != null &&
        lastHighlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE &&
        SeparatorPlacement.BOTTOM == lastHighlighter.getLineSeparatorPlacement()) {
      int lastLine = getDocument().getLineNumber(lastHighlighter.getStartOffset());
      LOG.assertTrue(lastLine <= line);
      if (lastLine == line) {
        Color lastLineColor = lastHighlighter.getLineSeparatorColor();
        if (Color.GRAY.equals(color) || !Color.GRAY.equals(lastLineColor)) {
          return null;
        }
        else {
          removeHighlighter(lastHighlighter);
        }
      }
    }
    RangeHighlighter marker = getMarkupModel().addLineHighlighter(line, LAYER, null);
//    saveHighlighter(marker);
    marker.setLineSeparatorColor(color);
    marker.setLineSeparatorPlacement(SeparatorPlacement.BOTTOM);
    // TODO[dyoma] type should be policy
    if (type == DiffColors.DIFF_DELETED) marker.setErrorStripeMarkColor(color);
    return marker;
  }

  private void removeHighlighter(RangeHighlighter highlighter) {
    highlighter.dispose();
    myHighLighters.remove(highlighter);
    myActionHighlighters.remove(highlighter);
  }

  private Color getLineSeparatorColorForType(TextAttributesKey type) {
    LOG.assertTrue(type == DiffColors.DIFF_DELETED || type == DiffColors.DIFF_MODIFIED || type == null);
    if (type == null || type == DiffColors.DIFF_MODIFIED) return Color.GRAY;
    return TextDiffType.DELETED.getTextBackground(getEditor());
  }

  private RangeHighlighter getLastHighlighter() {
    int size = myHighLighters.size();
    return size > 0 ? myHighLighters.get(size - 1) : null;
  }

  private void saveHighlighter(@NotNull RangeHighlighter marker) {
    myHighLighters.add(marker);
  }

  public Document getDocument() {
    return getEditor().getDocument();
  }

  public void addAction(final MergeOperations.Operation operation, int lineStartOffset) {
    RangeHighlighter highlighter = createAction(operation, lineStartOffset);
    if (highlighter != null) {
      myActionHighlighters.add(highlighter);
    }
  }

  private RangeHighlighter createAction(final MergeOperations.Operation operation, int lineStartOffset) {
    if (operation == null) return null;
    RangeHighlighter highlighter =
      getMarkupModel().addRangeHighlighter(lineStartOffset, lineStartOffset,
                                           HighlighterLayer.ADDITIONAL_SYNTAX,
                                           new TextAttributes(null, null, null, null, Font.PLAIN),
                                           HighlighterTargetArea.LINES_IN_RANGE);
    final MergeActionGroup.OperationAction action = new MergeActionGroup.OperationAction(operation);
    highlighter.setGutterIconRenderer(new GutterActionRenderer(action));
    return highlighter;
  }

  public void resetHighlighters() {
    removeHighlighters(myHighLighters);
    removeHighlighters(myActionHighlighters);
    for (RangeHighlighter highLighter : myExtraHighLighters) {
      highLighter.dispose();
    }
    myExtraHighLighters.clear();
  }

  private void removeHighlighters(Collection<RangeHighlighter> highlighters) {
    MarkupModel markupModel = getMarkupModel();
    if (markupModel != null) {
      for (RangeHighlighter highlighter : highlighters) {
        highlighter.dispose();
      }
    }
    highlighters.clear();
  }

  protected Project getProject() {
    return myProject;
  }

  protected void runRegisteredDisposables() {
    resetHighlighters();
    for (Disposable runnable : myDisposables) {
      Disposer.dispose(runnable);
    }
    myDisposables.clear();
  }

  public void addDisposable(@NotNull Disposable disposable) {
    Disposer.register(this, disposable);
    myDisposables.add(disposable);
  }

  public String getText() {
    return getDocument().getText();
  }

  protected final boolean isDisposed() { return myDisposed; }

  public final void dispose() {
    if (isDisposed()) return;
    onDisposed();
    myDisposed = true;
  }

  protected void onDisposed() {
  }

  public void removeActions() {
    removeHighlighters(myActionHighlighters);
  }
}
