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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.highlighter.*;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BeforeAfter;
import com.intellij.util.Consumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

/**
* Created by IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 9/8/11
* Time: 1:19 PM
*/
public class PreparedFragmentedContent {
  private LineNumberConvertor oldConvertor;
  private LineNumberConvertor newConvertor;
  private StringBuilder sbOld;
  private StringBuilder sbNew;
  private List<TextRange> myBeforeFragments;
  private List<TextRange> myAfterFragments;
  private List<BeforeAfter<Integer>> myLineRanges;
  private boolean myOneSide;
  private boolean myIsAddition;

  private FragmentedEditorHighlighter myBeforeHighlighter;
  private FragmentedEditorHighlighter myAfterHighlighter;
  private List<Pair<TextRange, TextAttributes>> myBeforeTodoRanges;
  private List<Pair<TextRange, TextAttributes>> myAfterTodoRanges;
  private final Project myProject;
  private final FragmentedContent myFragmentedContent;
  private final String myFileName;
  private final FileType myFileType;
  private final VcsRevisionNumber myBeforeNumber;
  private final VcsRevisionNumber myAfterNumber;
  private VirtualFile myFile;
  private FilePath myFilePath;

  public PreparedFragmentedContent(final Project project, final FragmentedContent fragmentedContent, final String fileName,
                                   final FileType fileType,
                                   VcsRevisionNumber beforeNumber,
                                   VcsRevisionNumber afterNumber,
                                   FilePath path,
                                   VirtualFile file) {
    myFile = file;
    myProject = project;
    myFragmentedContent = fragmentedContent;
    myFileName = fileName;
    myFileType = fileType;
    myBeforeNumber = beforeNumber;
    myAfterNumber = afterNumber;
    myFilePath = path;
    oldConvertor = new LineNumberConvertor();
    newConvertor = new LineNumberConvertor();
    sbOld = new StringBuilder();
    sbNew = new StringBuilder();
    myBeforeFragments = new ArrayList<TextRange>(fragmentedContent.getSize());
    myAfterFragments = new ArrayList<TextRange>(fragmentedContent.getSize());
    myLineRanges = new ArrayList<BeforeAfter<Integer>>();
    fromFragmentedContent(fragmentedContent);
  }

  public void recalculate() {
    oldConvertor = new LineNumberConvertor();
    newConvertor = new LineNumberConvertor();
    sbOld = new StringBuilder();
    sbNew = new StringBuilder();
    myBeforeFragments = new ArrayList<TextRange>(myFragmentedContent.getSize());
    myAfterFragments = new ArrayList<TextRange>(myFragmentedContent.getSize());
    myLineRanges = new ArrayList<BeforeAfter<Integer>>();
    fromFragmentedContent(myFragmentedContent);
  }

  private void fromFragmentedContent(final FragmentedContent fragmentedContent) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        myOneSide = fragmentedContent.isOneSide();
        myIsAddition = fragmentedContent.isAddition();
        List<BeforeAfter<TextRange>> expandedRanges =
          expand(fragmentedContent.getRanges(), VcsConfiguration.getInstance(myProject).SHORT_DIFF_EXTRA_LINES,
                 fragmentedContent.getBefore(), fragmentedContent.getAfter());
        // add "artificial" empty lines

        // line starts
        BeforeAfter<Integer> lines = new BeforeAfter<Integer>(0, 0);
        for (BeforeAfter<TextRange> lineNumbers : expandedRanges) {
          if (lines.getBefore() > 0 || lines.getAfter() > 0) {
            oldConvertor.emptyLine(lines.getBefore());
            newConvertor.emptyLine(lines.getAfter());
            lines = new BeforeAfter<Integer>(lines.getBefore() + 1, lines.getAfter() + 1);
            sbOld.append('\n');
            sbNew.append('\n');
          }

          myLineRanges.add(lines);
          oldConvertor.put(lines.getBefore(), lineNumbers.getBefore().getStartOffset());
          newConvertor.put(lines.getAfter(), lineNumbers.getAfter().getStartOffset());

          final Document document = fragmentedContent.getBefore();
          if (sbOld.length() > 0) {
            sbOld.append('\n');
          }
          final TextRange beforeRange = new TextRange(document.getLineStartOffset(lineNumbers.getBefore().getStartOffset()),
                                                      document.getLineEndOffset(lineNumbers.getBefore().getEndOffset()));
          myBeforeFragments.add(beforeRange);
          sbOld.append(document.getText(beforeRange));

          final Document document1 = fragmentedContent.getAfter();
          if (sbNew.length() > 0) {
            sbNew.append('\n');
          }
          final TextRange afterRange = new TextRange(document1.getLineStartOffset(lineNumbers.getAfter().getStartOffset()),
                                                     document1.getLineEndOffset(lineNumbers.getAfter().getEndOffset()));
          myAfterFragments.add(afterRange);
          sbNew.append(document1.getText(afterRange));

          int before = lines.getBefore() + lineNumbers.getBefore().getEndOffset() - lineNumbers.getBefore().getStartOffset() + 1;
          int after = lines.getAfter() + lineNumbers.getAfter().getEndOffset() - lineNumbers.getAfter().getStartOffset() + 1;
          lines = new BeforeAfter<Integer>(before, after);
        }
        myLineRanges.add(new BeforeAfter<Integer>(lines.getBefore() == 0 ? 0 : lines.getBefore() - 1,
                                                  lines.getAfter() == 0 ? 0 : lines.getAfter() - 1));

        setHighlighters(fragmentedContent.getBefore(), fragmentedContent.getAfter(), expandedRanges);
        setTodoHighlighting(fragmentedContent.getBefore(), fragmentedContent.getAfter());
      }
    });
  }

  public LineNumberConvertor getOldConvertor() {
    return oldConvertor;
  }

  public LineNumberConvertor getNewConvertor() {
    return newConvertor;
  }

  public DiffContent createBeforeContent() {
    if (isAddition()) {
      return SimpleContent.createEmpty();
    }
    return new SimpleContent(getSbOld().toString());
  }

  public DiffContent createAfterContent() {
    if (isDeletion()) {
      return SimpleContent.createEmpty();
    }
    return new SimpleContent(getSbNew().toString());
  }

  public StringBuilder getSbOld() {
    return sbOld;
  }

  public StringBuilder getSbNew() {
    return sbNew;
  }

  public List<TextRange> getBeforeFragments() {
    return myBeforeFragments;
  }

  public List<TextRange> getAfterFragments() {
    return myAfterFragments;
  }

  public List<BeforeAfter<Integer>> getLineRanges() {
    return myLineRanges;
  }

  public boolean isOneSide() {
    return myOneSide;
  }

  public boolean isAddition() {
    return myOneSide && myIsAddition;
  }

  public boolean isDeletion() {
    return myOneSide && ! myIsAddition;
  }

  public FragmentedEditorHighlighter getBeforeHighlighter() {
    return myBeforeHighlighter;
  }

  public void setBeforeHighlighter(FragmentedEditorHighlighter beforeHighlighter) {
    myBeforeHighlighter = beforeHighlighter;
  }

  public FragmentedEditorHighlighter getAfterHighlighter() {
    return myAfterHighlighter;
  }

  public void setAfterHighlighter(FragmentedEditorHighlighter afterHighlighter) {
    myAfterHighlighter = afterHighlighter;
  }

  public boolean isEmpty() {
    return myLineRanges.isEmpty();
  }

  public void setAfterTodoRanges(List<Pair<TextRange, TextAttributes>> afterTodoRanges) {
    myAfterTodoRanges = afterTodoRanges;
  }

  public List<Pair<TextRange, TextAttributes>> getBeforeTodoRanges() {
    return myBeforeTodoRanges;
  }

  public List<Pair<TextRange, TextAttributes>> getAfterTodoRanges() {
    return myAfterTodoRanges;
  }

  public void setBeforeTodoRanges(List<Pair<TextRange, TextAttributes>> beforeTodoRanges) {
    myBeforeTodoRanges = beforeTodoRanges;
  }

  public static List<BeforeAfter<TextRange>> expand(List<BeforeAfter<TextRange>> myRanges, final int lines, final Document oldDocument,
                                                    final Document document) {
    if (myRanges == null || myRanges.isEmpty()) return Collections.emptyList();
    if (lines == -1) {
      final List<BeforeAfter<TextRange>> shiftedRanges = new ArrayList<BeforeAfter<TextRange>>(1);
      shiftedRanges.add(new BeforeAfter<TextRange>(new TextRange(0, oldDocument.getLineCount() - 1), new TextRange(0, document.getLineCount() - 1)));
      return shiftedRanges;
    }
    final List<BeforeAfter<TextRange>> shiftedRanges = new ArrayList<BeforeAfter<TextRange>>(myRanges.size());
    final int oldLineCount = oldDocument.getLineCount();
    final int lineCount = document.getLineCount();

    for (BeforeAfter<TextRange> range : myRanges) {
      final TextRange newBefore = expandRange(range.getBefore(), lines, oldLineCount);
      final TextRange newAfter = expandRange(range.getAfter(), lines, lineCount);
      shiftedRanges.add(new BeforeAfter<TextRange>(newBefore, newAfter));
    }

    // and zip
    final List<BeforeAfter<TextRange>> zippedRanges = new ArrayList<BeforeAfter<TextRange>>(myRanges.size());
    final ListIterator<BeforeAfter<TextRange>> iterator = shiftedRanges.listIterator();
    BeforeAfter<TextRange> previous = iterator.next();
    while (iterator.hasNext()) {
      final BeforeAfter<TextRange> current = iterator.next();
      if (neighbourOrIntersect(previous.getBefore(), current.getBefore()) ||
          neighbourOrIntersect(previous.getAfter(), current.getAfter())) {
        previous = new BeforeAfter<TextRange>(previous.getBefore().union(current.getBefore()),
                                              previous.getAfter().union(current.getAfter()));
      } else {
        zippedRanges.add(previous);
        previous = current;
      }
    }
    zippedRanges.add(previous);
    return zippedRanges;
  }

  private static boolean neighbourOrIntersect(final TextRange a, final TextRange b) {
    return a.getEndOffset() + 1 == b.getStartOffset() || a.intersects(b);
  }

  private static TextRange expandRange(final TextRange range, final int shift, final int size) {
    return new TextRange(Math.max(0, range.getStartOffset() - shift), Math.max(0, Math.min(size - 1, range.getEndOffset() + shift)));
  }

  private void setHighlighters(final Document oldDocument, final Document document,
                               List<BeforeAfter<TextRange>> ranges) {
    EditorHighlighterFactory editorHighlighterFactory = EditorHighlighterFactory.getInstance();
    final SyntaxHighlighter syntaxHighlighter = SyntaxHighlighter.PROVIDER.create(myFileType, myProject, null);
    final EditorHighlighter highlighter =
      editorHighlighterFactory.createEditorHighlighter(syntaxHighlighter, EditorColorsManager.getInstance().getGlobalScheme());

    highlighter.setEditor(new LightHighlighterClient(oldDocument, myProject));
    highlighter.setText(oldDocument.getText());
    HighlighterIterator iterator = highlighter.createIterator(ranges.get(0).getBefore().getStartOffset());
    FragmentedEditorHighlighter beforeHighlighter =
      new FragmentedEditorHighlighter(iterator, getBeforeFragments(), 1);
    setBeforeHighlighter(beforeHighlighter);

    final EditorHighlighter highlighter1 =
      editorHighlighterFactory.createEditorHighlighter(syntaxHighlighter, EditorColorsManager.getInstance().getGlobalScheme());
    highlighter1.setEditor(new LightHighlighterClient(document, myProject));
    highlighter1.setText(document.getText());
    HighlighterIterator iterator1 = highlighter1.createIterator(ranges.get(0).getAfter().getStartOffset());
    FragmentedEditorHighlighter afterHighlighter =
      new FragmentedEditorHighlighter(iterator1, getAfterFragments(), 1);
    setAfterHighlighter(afterHighlighter);
  }

  private void setTodoHighlighting(final Document oldDocument, final Document document) {
    final ContentRevisionCache cache = ProjectLevelVcsManager.getInstance(myProject).getContentRevisionCache();
    final List<Pair<TextRange,TextAttributes>> beforeTodoRanges = myBeforeNumber == null ? Collections.<Pair<TextRange,TextAttributes>>emptyList() :
      new TodoForBaseRevision(myProject, getBeforeFragments(), 1, myFileName, oldDocument.getText(), true, myFileType, new Getter<Object>() {
      @Override
      public Object get() {
        return cache.getCustom(myFilePath, myBeforeNumber);
      }
    }, new Consumer<Object>() {
      @Override
      public void consume(Object items) {
        cache.putCustom(myFilePath, myBeforeNumber, items);
      }
    }).execute();

    final List<Pair<TextRange, TextAttributes>> afterTodoRanges = new TodoForExistingFile(myProject, getAfterFragments(), 1,
      myFileName, document.getText(), false, myFileType, myFile).execute();
    setBeforeTodoRanges(beforeTodoRanges);
    setAfterTodoRanges(afterTodoRanges);
  }

  public VirtualFile getFile() {
    return myFile;
  }
}
