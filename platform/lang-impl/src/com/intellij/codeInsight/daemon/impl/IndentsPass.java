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

/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.IntStack;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class IndentsPass extends TextEditorHighlightingPass implements DumbAware {
  private static final Key<List<RangeHighlighter>> INDENT_HIGHLIGHTERS_IN_EDITOR_KEY = Key.create("INDENT_HIGHLIGHTERS_IN_EDITOR_KEY");
  private static final Key<Long> LAST_TIME_INDENTS_BUILT = Key.create("LAST_TIME_INDENTS_BUILT");

  private final EditorEx myEditor;
  private final PsiFile myFile;
  public static final Comparator<TextRange> RANGE_COMPARATOR = new Comparator<TextRange>() {
    @Override
    public int compare(TextRange o1, TextRange o2) {
      if (o1.getStartOffset() == o2.getStartOffset()) {
        return o1.getEndOffset() - o2.getEndOffset();
      }

      return o1.getStartOffset() - o2.getStartOffset();
    }
  };

  private static final CustomHighlighterRenderer RENDERER = new CustomHighlighterRenderer() {
    @Override
    @SuppressWarnings({"AssignmentToForLoopParameter"})
    public void paint(@NotNull Editor editor,
                      @NotNull RangeHighlighter highlighter,
                      @NotNull Graphics g) {
      int startOffset = highlighter.getStartOffset();
      final Document doc = highlighter.getDocument();
      if (startOffset >= doc.getTextLength()) return;

      final int endOffset = highlighter.getEndOffset();
      final int endLine = doc.getLineNumber(endOffset);

      int off;
      int startLine = doc.getLineNumber(startOffset);
      IndentGuideDescriptor descriptor = editor.getIndentsModel().getDescriptor(startLine, endLine);

      final CharSequence chars = doc.getCharsSequence();
      do {
        int pos = doc.getLineStartOffset(startLine);
        off = CharArrayUtil.shiftForward(chars, pos, " \t");
        startLine--;
      }
      while (startLine > 1 && off < doc.getTextLength() && chars.charAt(off) == '\n');

      final VisualPosition startPosition = editor.offsetToVisualPosition(off);
      int indentColumn = startPosition.column;
      
      // It's considered that indent guide can cross not only white space but comments, javadocs etc. Hence, there is a possible
      // case that the first indent guide line is, say, single-line comment where comment symbols ('//') are located at the first
      // visual column. We need to calculate correct indent guide column then.
      int lineShift = 1;
      if (indentColumn <= 0 && descriptor != null) {
        indentColumn = descriptor.indentLevel;
        lineShift = 0;
      }
      if (indentColumn <= 0) return;

      final FoldingModel foldingModel = editor.getFoldingModel();
      if (foldingModel.isOffsetCollapsed(off)) return;

      final FoldRegion headerRegion = foldingModel.getCollapsedRegionAtOffset(doc.getLineEndOffset(doc.getLineNumber(off)));
      final FoldRegion tailRegion = foldingModel.getCollapsedRegionAtOffset(doc.getLineStartOffset(doc.getLineNumber(endOffset)));

      if (tailRegion != null && tailRegion == headerRegion) return;

      final boolean selected;
      final IndentGuideDescriptor guide = editor.getIndentsModel().getCaretIndentGuide();
      if (guide != null) {
        final CaretModel caretModel = editor.getCaretModel();
        final int caretOffset = caretModel.getOffset();
        selected =
          caretOffset >= off && caretOffset < endOffset && caretModel.getLogicalPosition().column == indentColumn;
      }
      else {
        selected = false;
      }

      Point start = editor.visualPositionToXY(new VisualPosition(startPosition.line + lineShift, indentColumn));
      final VisualPosition endPosition = editor.offsetToVisualPosition(endOffset);
      Point end = editor.visualPositionToXY(new VisualPosition(endPosition.line, endPosition.column));
      int maxY = end.y;

      Rectangle clip = g.getClipBounds();
      if (clip != null) {
        if (clip.y >= end.y || clip.y + clip.height <= start.y) {
          return;
        }
        maxY = Math.min(maxY, clip.y + clip.height);
      }

      final EditorColorsScheme scheme = editor.getColorsScheme();
      g.setColor(selected ? scheme.getColor(EditorColors.SELECTED_INDENT_GUIDE_COLOR) : scheme.getColor(EditorColors.INDENT_GUIDE_COLOR));
      
      // There is a possible case that indent line intersects soft wrap-introduced text. Example:
      //     this is a long line <soft-wrap>
      // that| is soft-wrapped
      //     |
      //     | <- vertical indent
      //
      // Also it's possible that no additional intersections are added because of soft wrap:
      //     this is a long line <soft-wrap>
      //     |   that is soft-wrapped
      //     |
      //     | <- vertical indent   
      // We want to use the following approach then:
      //     1. Show only active indent if it crosses soft wrap-introduced text;
      //     2. Show indent as is if it doesn't intersect with soft wrap-introduced text;
      if (selected) {
        g.drawLine(start.x + 2, start.y, start.x + 2, maxY);
      }
      else {
        int y = start.y;
        int newY = start.y;
        SoftWrapModel softWrapModel = editor.getSoftWrapModel();
        int lineHeight = editor.getLineHeight();
        for (int i = Math.max(0, startLine + lineShift); i < endLine && newY < maxY; i++) {
          List<? extends SoftWrap> softWraps = softWrapModel.getSoftWrapsForLine(i);
          int logicalLineHeight = softWraps.size() * lineHeight;
          if (i > startLine + lineShift) {
            logicalLineHeight += lineHeight; // We assume that initial 'y' value points just below the target line.
          }
          if (!softWraps.isEmpty() && softWraps.get(0).getIndentInColumns() < indentColumn) {
            if (y < newY || i > startLine + lineShift) { // There is a possible case that soft wrap is located on indent start line.
              g.drawLine(start.x + 2, y, start.x + 2, newY + lineHeight);
            }
            newY += logicalLineHeight;
            y = newY;
          }
          else {
            newY += logicalLineHeight;
          }

          FoldRegion foldRegion = foldingModel.getCollapsedRegionAtOffset(doc.getLineEndOffset(i));
          if (foldRegion != null && foldRegion.getEndOffset() < doc.getTextLength()) {
            i = doc.getLineNumber(foldRegion.getEndOffset());
          }
        }
        
        if (y < maxY) {
          g.drawLine(start.x + 2, y, start.x + 2, maxY);
        }
      }
    }
  };
  private volatile List<TextRange> myRanges;
  private volatile List<IndentGuideDescriptor> myDescriptors;

  public IndentsPass(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    super(project, editor.getDocument(), false);
    myEditor = (EditorEx)editor;
    myFile = file;
  }

  @Override
  public void doCollectInformation(ProgressIndicator progress) {
    final Long stamp = myEditor.getUserData(LAST_TIME_INDENTS_BUILT);
    if (stamp != null && stamp.longValue() == nowStamp()) return;

    myDescriptors = buildDescriptors();

    ArrayList<TextRange> ranges = new ArrayList<TextRange>();
    for (IndentGuideDescriptor descriptor : myDescriptors) {
      ProgressManager.checkCanceled();
      int endOffset = descriptor.endLine < myDocument.getLineCount() ? myDocument.getLineStartOffset(descriptor.endLine) : myDocument.getTextLength();
      ranges.add(new TextRange(myDocument.getLineStartOffset(descriptor.startLine), endOffset));
    }

    Collections.sort(ranges, RANGE_COMPARATOR);
    myRanges = ranges;
  }

  private long nowStamp() {
    if (!myEditor.getSettings().isIndentGuidesShown()) return -1;
    return myDocument.getModificationStamp();
  }

  @Override
  public void doApplyInformationToEditor() {
    final Long stamp = myEditor.getUserData(LAST_TIME_INDENTS_BUILT);
    if (stamp != null && stamp.longValue() == nowStamp()) return;

    List<RangeHighlighter> oldHighlighters = myEditor.getUserData(INDENT_HIGHLIGHTERS_IN_EDITOR_KEY);
    List<RangeHighlighter> newHighlighters = new ArrayList<RangeHighlighter>();
    MarkupModel mm = myEditor.getMarkupModel();

    int curRange = 0;

    if (oldHighlighters != null) {
      int curHighlight = 0;
      while (curRange < myRanges.size() && curHighlight < oldHighlighters.size()) {
        TextRange range = myRanges.get(curRange);
        RangeHighlighter highlighter = oldHighlighters.get(curHighlight);

        int cmp = compare(range, highlighter);
        if (cmp < 0) {
          newHighlighters.add(createHighlighter(mm, range));
          curRange++;
        }
        else if (cmp > 0) {
          highlighter.dispose();
          curHighlight++;
        }
        else {
          newHighlighters.add(highlighter);
          curHighlight++;
          curRange++;
        }
      }

      for (; curHighlight < oldHighlighters.size(); curHighlight++) {
        RangeHighlighter highlighter = oldHighlighters.get(curHighlight);
        highlighter.dispose();
      }
    }

    for (; curRange < myRanges.size(); curRange++) {
      newHighlighters.add(createHighlighter(mm, myRanges.get(curRange)));
    }

    myEditor.putUserData(INDENT_HIGHLIGHTERS_IN_EDITOR_KEY, newHighlighters);
    myEditor.putUserData(LAST_TIME_INDENTS_BUILT, nowStamp());
    myEditor.getIndentsModel().assumeIndents(myDescriptors);
  }

  private List<IndentGuideDescriptor> buildDescriptors() {
    if (!myEditor.getSettings().isIndentGuidesShown()) return Collections.emptyList();

    int[] lineIndents = calcIndents();

    List<IndentGuideDescriptor> descriptors = new ArrayList<IndentGuideDescriptor>();

    IntStack lines = new IntStack();
    IntStack indents = new IntStack();

    lines.push(0);
    indents.push(0);
    final CharSequence chars = myDocument.getCharsSequence();
    for (int line = 1; line < lineIndents.length; line++) {
      ProgressManager.checkCanceled();
      int curIndent = lineIndents[line];

      while (!indents.empty() && curIndent <= indents.peek()) {
        ProgressManager.checkCanceled();
        final int level = indents.pop();
        int startLine = lines.pop();
        descriptors.add(createDescriptor(level, startLine, line, chars));
      }

      int prevLine = line - 1;
      int prevIndent = lineIndents[prevLine];

      if (curIndent - prevIndent > 1) {
        lines.push(prevLine);
        indents.push(prevIndent);
      }
    }

    while (!indents.empty()) {
      ProgressManager.checkCanceled();
      final int level = indents.pop();
      if (level > 0) {
        int startLine = lines.pop();
        descriptors.add(createDescriptor(level, startLine, myDocument.getLineCount(), chars));
      }
    }
    return descriptors;
  }

  private IndentGuideDescriptor createDescriptor(int level, int startLine, int endLine, CharSequence chars) {
    while (startLine > 0 && isBlankLine(startLine, chars)) startLine--;
    return new IndentGuideDescriptor(level, startLine, endLine);
  }

  private boolean isBlankLine(int line, CharSequence chars) {
    int startOffset = myDocument.getLineStartOffset(line);
    return CharArrayUtil.shiftForward(chars, startOffset, " \t") >= myDocument.getLineEndOffset(line);
  }

  private int[] calcIndents() {
    final Document doc = myDocument;
    CharSequence chars = doc.getCharsSequence();
    int[] lineIndents = new int[doc.getLineCount()];
    TokenSet comments = LanguageParserDefinitions.INSTANCE.forLanguage(myFile.getLanguage()).getCommentTokens();

    int prevColumn = -1;
    final EditorHighlighter highlighter = myEditor.getHighlighter();
    final FileType fileType = myFile.getFileType();

    for (int line = 0; line < lineIndents.length; line++) {
      ProgressManager.checkCanceled();
      int lineStart = doc.getLineStartOffset(line);
      int lineEnd = doc.getLineEndOffset(line);

      int nonWhitespaceOffset = CharArrayUtil.shiftForward(chars, lineStart, " \t");
      if (nonWhitespaceOffset < lineEnd) {
        final int column = myEditor.calcColumnNumber(nonWhitespaceOffset, line);
        if (column < prevColumn) {
          final HighlighterIterator it = highlighter.createIterator(nonWhitespaceOffset);
          if (comments.contains(it.getTokenType())) {
            lineIndents[line] = -1;
            continue;
          }
        }

        lineIndents[line] = column;
        prevColumn = column;
      }
      else {
        lineIndents[line] = -1;
      }
    }

    int topIndent = 0;
    for (int line = 0; line < lineIndents.length; line++) {
      ProgressManager.checkCanceled();
      if (lineIndents[line] >= 0) {
        topIndent = lineIndents[line];
      }
      else {
        int startLine = line;
        while (line < lineIndents.length && lineIndents[line] < 0) {
          //noinspection AssignmentToForLoopParameter
          line++;
        }

        int bottomIndent = line < lineIndents.length ? lineIndents[line] : topIndent;

        int indent = Math.min(topIndent, bottomIndent);
        if (bottomIndent < topIndent) {
          int nonWhitespaceOffset = CharArrayUtil.shiftForward(chars, doc.getLineStartOffset(line), " \t");
          HighlighterIterator iterator = highlighter.createIterator(nonWhitespaceOffset);
          if (BraceMatchingUtil.isRBraceToken(iterator, chars, fileType)) {
            indent = topIndent;
          }
        }

        for (int blankLine = startLine; blankLine < line; blankLine++) {
          assert lineIndents[blankLine] == -1;
          lineIndents[blankLine] = Math.min(topIndent, indent);
        }

        //noinspection AssignmentToForLoopParameter
        line--; // will be incremented back at the end of the loop;
      }
    }

    return lineIndents;
  }

  private static RangeHighlighter createHighlighter(MarkupModel mm, TextRange range) {
    final RangeHighlighter highlighter =
      mm.addRangeHighlighter(range.getStartOffset(), range.getEndOffset(), 0, null, HighlighterTargetArea.EXACT_RANGE);
    highlighter.setCustomRenderer(RENDERER);
    return highlighter;
  }

  private static int compare(TextRange r, RangeHighlighter h) {
    int answer = r.getStartOffset() - h.getStartOffset();
    return answer != 0 ? answer : r.getEndOffset() - h.getEndOffset();
  }
}
