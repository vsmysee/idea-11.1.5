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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DocumentImpl extends UserDataHolderBase implements DocumentEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.DocumentImpl");

  private final CopyOnWriteArrayList<DocumentListener> myDocumentListeners = ContainerUtil.createEmptyCOWList();
  private final RangeMarkerTree<RangeMarkerEx> myRangeMarkers = new RangeMarkerTree<RangeMarkerEx>(this);
  private final List<RangeMarker> myGuardedBlocks = new ArrayList<RangeMarker>();
  private ReadonlyFragmentModificationHandler myReadonlyFragmentModificationHandler;

  private final LineSet myLineSet = new LineSet();
  private final CharArray myText;

  private boolean myIsReadOnly = false;
  private boolean isStripTrailingSpacesEnabled = true;
  private volatile long myModificationStamp;
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  private DocumentListener[] myCachedDocumentListeners;
  private final List<EditReadOnlyListener> myReadOnlyListeners = ContainerUtil.createEmptyCOWList();

  private int myCheckGuardedBlocks = 0;
  private boolean myGuardsSuppressed = false;
  private boolean myEventsHandling = false;
  private final boolean myAssertThreading;
  private volatile boolean myDoingBulkUpdate = false;
  private volatile boolean myAcceptSlashR = false;
  private boolean myChangeInProgress;

  public DocumentImpl(@NotNull String text) {
    this(text, false);
  }
  public DocumentImpl(@NotNull CharSequence chars) {
    this(chars, false);
  }

  public DocumentImpl(@NotNull CharSequence chars, boolean forUseInNonAWTThread) {
    assertValidSeparators(chars);
    myText = new MyCharArray(CharArrayUtil.fromSequence(chars), chars.length());
    myLineSet.documentCreated(this);
    setCyclicBufferSize(0);
    setModificationStamp(LocalTimeCounter.currentTime());
    myAssertThreading = !forUseInNonAWTThread;
  }

  public boolean setAcceptSlashR(boolean accept) {
    try {
      return myAcceptSlashR;
    }
    finally {
      myAcceptSlashR = accept;
    }
  }

  public char[] getRawChars() {
    return myText.getChars();
  }

  @Override
  @NotNull
  public char[] getChars() {
    return ArrayUtil.realloc(CharArrayUtil.fromSequence(getCharsSequence()), myText.length());
  }

  @Override
  public void setStripTrailingSpacesEnabled(boolean isEnabled) {
    isStripTrailingSpacesEnabled = isEnabled;
  }
  
  @TestOnly
  public boolean stripTrailingSpaces() {
    return stripTrailingSpaces(null, false, false, -1, -1);
  }

  /**
   * @return true if stripping was completed successfully, false if the document prevented stripping by e.g. caret being in the way
   */
  public boolean stripTrailingSpaces(@Nullable final Project project,
                                     boolean inChangedLinesOnly,
                                     boolean virtualSpaceEnabled,
                                     int caretLine,
                                     int caretOffset) {
    if (!isStripTrailingSpacesEnabled) {
      return true;
    }

    boolean markAsNeedsStrippingLater = false;
    CharSequence text = myText.getCharArray();
    RangeMarker caretMarker = caretOffset < 0 || caretOffset > getTextLength() ? null : createRangeMarker(caretOffset, caretOffset);
    try {
      for (int line = 0; line < myLineSet.getLineCount(); line++) {
        if (inChangedLinesOnly && !myLineSet.isModified(line)) continue;
        int whiteSpaceStart = -1;
        final int lineEnd = myLineSet.getLineEnd(line) - myLineSet.getSeparatorLength(line);
        int lineStart = myLineSet.getLineStart(line);
        for (int offset = lineEnd - 1; offset >= lineStart; offset--) {
          char c = text.charAt(offset);
          if (c != ' ' && c != '\t') {
            break;
          }
          whiteSpaceStart = offset;
        }
        if (whiteSpaceStart == -1) continue;
        if (!virtualSpaceEnabled && caretLine == line && caretMarker != null &&
            caretMarker.getStartOffset() >= 0 && whiteSpaceStart < caretMarker.getStartOffset()) {
          // mark this as a document that needs stripping later
          // otherwise the caret would jump madly
          markAsNeedsStrippingLater = true;
        }
        else {
          final int finalStart = whiteSpaceStart;
          ApplicationManager.getApplication().runWriteAction(new DocumentRunnable(this, project) {
            @Override
            public void run() {
              CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
                @Override
                public void run() {
                  deleteString(finalStart, lineEnd);
                }
              });
            }
          });
          text = myText.getCharArray();
        }
      }
    }
    finally {
      if (caretMarker != null) caretMarker.dispose();
    }
    return markAsNeedsStrippingLater;
  }

  @Override
  public void setReadOnly(boolean isReadOnly) {
    if (myIsReadOnly != isReadOnly) {
      myIsReadOnly = isReadOnly;
      myPropertyChangeSupport.firePropertyChange(Document.PROP_WRITABLE, !isReadOnly, isReadOnly);
    }
  }

  public ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler() {
    return myReadonlyFragmentModificationHandler;
  }

  public void setReadonlyFragmentModificationHandler(final ReadonlyFragmentModificationHandler readonlyFragmentModificationHandler) {
    myReadonlyFragmentModificationHandler = readonlyFragmentModificationHandler;
  }

  @Override
  public boolean isWritable() {
    return !myIsReadOnly;
  }

  @Override
  public boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    return myRangeMarkers.removeInterval(rangeMarker);
  }

  public void addRangeMarker(@NotNull RangeMarkerEx rangeMarker, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    myRangeMarkers.addInterval(rangeMarker, start, end, greedyToLeft, greedyToRight, layer);
  }

  @TestOnly
  public int getRangeMarkersSize() {
    return myRangeMarkers.size();
  }
  @TestOnly
  public int getRangeMarkersNodeSize() {
    return myRangeMarkers.nodeSize();
  }

  @Override
  @NotNull
  public RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    LOG.assertTrue(startOffset <= endOffset, "Should be startOffset <= endOffset");
    RangeMarker block = createRangeMarker(startOffset, endOffset, true);
    myGuardedBlocks.add(block);
    return block;
  }

  @Override
  public void removeGuardedBlock(@NotNull RangeMarker block) {
    myGuardedBlocks.remove(block);
  }

  @Override
  @NotNull
  public List<RangeMarker> getGuardedBlocks() {
    return myGuardedBlocks;
  }

  @Override
  @SuppressWarnings({"ForLoopReplaceableByForEach"}) // Way too many garbage is produced otherwise in AbstractList.iterator()
  public RangeMarker getOffsetGuard(int offset) {
    for (int i = 0; i < myGuardedBlocks.size(); i++) {
      RangeMarker block = myGuardedBlocks.get(i);
      if (offsetInRange(offset, block.getStartOffset(), block.getEndOffset())) return block;
    }

    return null;
  }

  @Override
  public RangeMarker getRangeGuard(int start, int end) {
    for (RangeMarker block : myGuardedBlocks) {
      if (rangesIntersect(start, true, block.getStartOffset(), block.isGreedyToLeft(), end, true, block.getEndOffset(), block.isGreedyToRight())) {
        return block;
      }
    }

    return null;
  }

  @Override
  public void startGuardedBlockChecking() {
    myCheckGuardedBlocks++;
  }

  @Override
  public void stopGuardedBlockChecking() {
    LOG.assertTrue(myCheckGuardedBlocks > 0, "Unpaired start/stopGuardedBlockChecking");
    myCheckGuardedBlocks--;
  }

  private static boolean offsetInRange(int offset, int start, int end) {
    return start <= offset && offset < end;
  }

  private static boolean rangesIntersect(int start0, boolean leftInclusive0,
                                         int start1, boolean leftInclusive1,
                                         int end0, boolean rightInclusive0,
                                         int end1, boolean rightInclusive1) {
    if (start0 > start1 || start0 == start1 && !leftInclusive0) {
      return rangesIntersect(start1, leftInclusive1, start0, leftInclusive0, end1, rightInclusive1, end0, rightInclusive0);
    }
    if (end0 == start1) return leftInclusive1 && rightInclusive0;
    return end0 > start1;
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(int startOffset, int endOffset) {
    return createRangeMarker(startOffset, endOffset, false);
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    if (!(0 <= startOffset && startOffset <= endOffset && endOffset <= getTextLength())) {
      LOG.error("Incorrect offsets: startOffset=" + startOffset + ", endOffset=" + endOffset + ", text length=" + getTextLength());
    }
    return surviveOnExternalChange
           ? new PersistentRangeMarker(this, startOffset, endOffset,true)
           : new RangeMarkerImpl(this, startOffset, endOffset,true);
  }

  @Override
  public long getModificationStamp() {
    return myModificationStamp;
  }

  @Override
  public void setModificationStamp(long modificationStamp) {
    myModificationStamp = modificationStamp;
  }

  @Override
  public void replaceText(@NotNull CharSequence chars, long newModificationStamp) {
    replaceString(0, getTextLength(), chars, newModificationStamp, true); //TODO: optimization!!!
    clearLineModificationFlags();
  }

  @Override
  public int getListenersCount() {
    return myDocumentListeners.size();
  }

  @Override
  public void insertString(int offset, @NotNull CharSequence s) {
    if (offset < 0) throw new IndexOutOfBoundsException("Wrong offset: " + offset);
    if (offset > getTextLength()) {
      throw new IndexOutOfBoundsException(
        "Wrong offset: " + offset + "; documentLength: " + getTextLength()+ "; " + s.subSequence(Math.max(0, s.length() - 20), s.length())
      );
    }
    assertWriteAccess();
    assertValidSeparators(s);
    assertNotNestedModification();

    if (!isWritable()) throw new ReadOnlyModificationException(this);
    if (s.length() == 0) return;

    RangeMarker marker = getRangeGuard(offset, offset);
    if (marker != null) {
      throwGuardedFragment(marker, offset, null, s.toString());
    }

    myText.insert(s, offset);
  }

  @Override
  public void deleteString(int startOffset, int endOffset) {
    assertBounds(startOffset, endOffset);

    assertWriteAccess();
    if (!isWritable()) throw new ReadOnlyModificationException(this);
    if (startOffset == endOffset) return;
    assertNotNestedModification();

    CharSequence sToDelete = myText.substring(startOffset, endOffset);

    RangeMarker marker = getRangeGuard(startOffset, endOffset);
    if (marker != null) {
      throwGuardedFragment(marker, startOffset, sToDelete.toString(), null);
    }

    myText.remove(startOffset, endOffset, sToDelete);
  }

  @Override
  public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) {
    replaceString(startOffset, endOffset, s, LocalTimeCounter.currentTime(), startOffset == 0 && endOffset == getTextLength());
  }

  private void replaceString(int startOffset, int endOffset, CharSequence s, final long newModificationStamp, boolean wholeTextReplaced) {
    assertBounds(startOffset, endOffset);

    assertWriteAccess();
    assertValidSeparators(s);

    if (!isWritable()) {
      throw new ReadOnlyModificationException(this);
    }
    assertNotNestedModification();

    final int newStringLength = s.length();
    final CharSequence chars = getCharsSequence();
    int newStartInString = 0;
    int newEndInString = newStringLength;
    while (newStartInString < newStringLength &&
           startOffset < endOffset &&
           s.charAt(newStartInString) == chars.charAt(startOffset)) {
      startOffset++;
      newStartInString++;
    }

    while (endOffset > startOffset &&
           newEndInString > newStartInString &&
           s.charAt(newEndInString - 1) == chars.charAt(endOffset - 1)) {
      newEndInString--;
      endOffset--;
    }

    s = s.subSequence(newStartInString, newEndInString);
    CharSequence sToDelete = myText.substring(startOffset, endOffset);
    RangeMarker guard = getRangeGuard(startOffset, endOffset);
    if (guard != null) {
      throwGuardedFragment(guard, startOffset, sToDelete.toString(), s.toString());
    }

    myText.replace(startOffset, endOffset, sToDelete, s, newModificationStamp, wholeTextReplaced);
  }

  private void assertBounds(final int startOffset, final int endOffset) {
    if (startOffset < 0 || startOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong startOffset: " + startOffset+"; documentLength: "+getTextLength());
    }
    if (endOffset < 0 || endOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong endOffset: " + endOffset+"; documentLength: "+getTextLength());
    }
    if (endOffset < startOffset) {
      throw new IllegalArgumentException("endOffset < startOffset: " + endOffset + " < " + startOffset+"; documentLength: "+getTextLength());
    }
  }

  private void assertWriteAccess() {
    if (myAssertThreading) {
      final Application application = ApplicationManager.getApplication();
      if (application != null) {
        application.assertWriteAccessAllowed();
      }
    }
  }

  private void assertValidSeparators(@NotNull CharSequence s) {
    if (myAcceptSlashR) return;
    StringUtil.assertValidSeparators(s);
  }

  /**
   * All document change actions follows the algorithm below:
   * <pre>
   * <ol>
   *   <li>
   *     All {@link #addDocumentListener(DocumentListener) registered listeners} are notified
   *     {@link DocumentListener#beforeDocumentChange(DocumentEvent) before the change};
   *   </li>
   *   <li>The change is performed </li>
   *   <li>
   *     All {@link #addDocumentListener(DocumentListener) registered listeners} are notified
   *     {@link DocumentListener#documentChanged(DocumentEvent) after the change};
   *   </li>
   * </ol>
   * </pre>
   * <p/>
   * There is a possible case that <code>'before change'</code> notification produces new change. We have a problem then - imagine
   * that initial change was <code>'replace particular range at document end'</code> and <code>'nested change'</code> was to
   * <code>'remove text at document end'</code>. That means that when initial change will be actually performed, the document may be
   * not long enough to contain target range.
   * <p/>
   * Current method allows to check if document change is a <code>'nested call'</code>.
   *
   * @throws IllegalStateException  if this method is called during a <code>'nested document modification'</code>
   */
  private void assertNotNestedModification() throws IllegalStateException {
    if (myChangeInProgress) {
      throw new IllegalStateException("Detected nested request for document modification from 'before change' callback!");
    }
  }

  private void throwGuardedFragment(RangeMarker guard, int offset, String oldString, String newString) {
    if (myCheckGuardedBlocks > 0 && !myGuardsSuppressed) {
      DocumentEvent event = new DocumentEventImpl(this, offset, oldString, newString, myModificationStamp, false);
      throw new ReadOnlyFragmentModificationException(event, guard);
    }
  }

  @Override
  public void suppressGuardedExceptions() {
    myGuardsSuppressed = true;
  }

  @Override
  public void unSuppressGuardedExceptions() {
    myGuardsSuppressed = false;
  }

  @Override
  public boolean isInEventsHandling() {
    return myEventsHandling;
  }

  @Override
  public void clearLineModificationFlags() {
    myLineSet.clearModificationFlags();
  }

  public void clearLineModificationFlagsExcept(int caretLine) {
    boolean wasModified = caretLine != -1 && myLineSet.isModified(caretLine);
    clearLineModificationFlags();
    if (wasModified) {
      myLineSet.setModified(caretLine);
    }
  }

  @NotNull
  private DocumentEvent beforeChangedUpdate(int offset, CharSequence oldString, CharSequence newString, boolean wholeTextReplaced) {
    myChangeInProgress = true;
    try {
      return doBeforeChangedUpdate(offset, oldString, newString, wholeTextReplaced);
    }
    finally {
      myChangeInProgress = false;
    }
  }

  @NotNull
  private DocumentEvent doBeforeChangedUpdate(int offset, CharSequence oldString, CharSequence newString, boolean wholeTextReplaced) {
    FileDocumentManager manager = FileDocumentManager.getInstance();
    if (manager != null) {
      VirtualFile file = manager.getFile(this);
      if (file != null && !file.isValid()) {
        LOG.error("File of this document has been deleted.");
      }
    }

    DocumentEvent event = new DocumentEventImpl(this, offset, oldString, newString, myModificationStamp, wholeTextReplaced);

    if (!ShutDownTracker.isShutdownHookRunning()) {
      DocumentListener[] listeners = getCachedListeners();
      for (int i = listeners.length - 1; i >= 0; i--) {
        try {
          listeners[i].beforeDocumentChange(event);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }

    myEventsHandling = true;
    return event;
  }

  private void changedUpdate(DocumentEvent event, long newModificationStamp) {
    try {
      if (LOG.isDebugEnabled()) LOG.debug(event.toString());

      myLineSet.changedUpdate(event);
      setModificationStamp(newModificationStamp);

      if (!ShutDownTracker.isShutdownHookRunning()) {
        DocumentListener[] listeners = getCachedListeners();
        for (DocumentListener listener : listeners) {
          try {
            listener.documentChanged(event);
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }
      }
    }
    finally{
      myEventsHandling = false;
    }
  }

  @Override
  public String getText() {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return myText.toString();
      }
    });
  }

  @NotNull
  @Override
  public String getText(@NotNull final TextRange range) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return myText.substring(range.getStartOffset(), range.getEndOffset()).toString();
      }
    });
  }

  @Override
  public int getTextLength() {
    return myText.length();
  }

  /**
   This method should be used very carefully - only to read the array, and to be sure, that nobody changes
   text, while this array is processed.
   Really it is used only to optimize paint in Editor.
   [Valentin] 25.04.2001: More really, it is used in 61 places in 29 files across the project :-)))
   */
  CharSequence getCharsNoThreadCheck() {
    return getCharsSequence();
  }

  @Override
  @NotNull
  public CharSequence getCharsSequence() {
    return myText.getCharArray();
  }


  @Override
  public void addDocumentListener(@NotNull DocumentListener listener) {
    myCachedDocumentListeners = null;
    boolean added = myDocumentListeners.addIfAbsent(listener);
    LOG.assertTrue(added, listener);
  }

  @Override
  public void addDocumentListener(@NotNull final DocumentListener listener, @NotNull Disposable parentDisposable) {
    addDocumentListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeDocumentListener(listener);
      }
    });
  }

  @Override
  public void removeDocumentListener(@NotNull DocumentListener listener) {
    myCachedDocumentListeners = null;
    boolean success = myDocumentListeners.remove(listener);
    if (!success) {
      LOG.error(String.format("Can't remove given document listener (%s). Registered listeners: %s", listener, myDocumentListeners));
    }
  }

  @Override
  public int getLineNumber(final int offset) {
    return myLineSet.findLineIndex(offset);
  }

  @Override
  @NotNull
  public LineIterator createLineIterator() {
    return myLineSet.createIterator();
  }

  @Override
  public final int getLineStartOffset(final int line) {
    if (line == 0) return 0; // otherwise it crashed for zero-length document
    return myLineSet.getLineStart(line);
  }

  @Override
  public final int getLineEndOffset(int line) {
    if (getTextLength() == 0 && line == 0) return 0;
    int result = myLineSet.getLineEnd(line) - getLineSeparatorLength(line);
    assert result >= 0;
    return result;
  }

  @Override
  public final int getLineSeparatorLength(int line) {
    int separatorLength = myLineSet.getSeparatorLength(line);
    assert separatorLength >= 0;
    return separatorLength;
  }

  @Override
  public final int getLineCount() {
    int lineCount = myLineSet.getLineCount();
    assert lineCount >= 0;
    return lineCount;
  }

  @NotNull
  private DocumentListener[] getCachedListeners() {
    DocumentListener[] cachedListeners = myCachedDocumentListeners;
    if (cachedListeners == null) {
      DocumentListener[] listeners = myDocumentListeners.toArray(new DocumentListener[myDocumentListeners.size()]);
      Arrays.sort(listeners, PrioritizedDocumentListener.COMPARATOR);
      myCachedDocumentListeners = cachedListeners = listeners;
    }

    return cachedListeners;
  }

  @Override
  public void fireReadOnlyModificationAttempt() {
    for (EditReadOnlyListener listener : myReadOnlyListeners) {
      listener.readOnlyModificationAttempt(this);
    }
  }

  @Override
  public void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
    myReadOnlyListeners.add(listener);
  }

  @Override
  public void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
    myReadOnlyListeners.remove(listener);
  }


  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  @Override
  public void setCyclicBufferSize(int bufferSize) {
    myText.setBufferSize(bufferSize);
  }

  @Override
  public void setText(@NotNull final CharSequence text) {
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        replaceString(0, getTextLength(), text, LocalTimeCounter.currentTime(), true);
      }
    };
    if (CommandProcessor.getInstance().isUndoTransparentActionInProgress()) {
      runnable.run();
    }
    else {
      CommandProcessor.getInstance().executeCommand(null, runnable, "", DocCommandGroupId.noneGroupId(this));
    }

    clearLineModificationFlags();
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(@NotNull final TextRange textRange) {
    return createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
  }

  @Override
  public final boolean isInBulkUpdate() {
    return myDoingBulkUpdate;
  }

  @Override
  public final void setInBulkUpdate(boolean value) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myDoingBulkUpdate = value;
    myText.setDeferredChangeMode(value);
    if (value) {
      getPublisher().updateStarted(this);
    }
    else {
      getPublisher().updateFinished(this);
    }
  }

  private static class DocumentBulkUpdateListenerHolder {
    private static final DocumentBulkUpdateListener ourBulkChangePublisher =
        ApplicationManager.getApplication().getMessageBus().syncPublisher(DocumentBulkUpdateListener.TOPIC);
  }

  private static DocumentBulkUpdateListener getPublisher() {
    return DocumentBulkUpdateListenerHolder.ourBulkChangePublisher;
  }

  @Override
  public boolean processRangeMarkers(@NotNull Processor<RangeMarker> processor) {
    return myRangeMarkers.process(processor);
  }

  @Override
  public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<RangeMarker> processor) {
    return myRangeMarkers.processOverlappingWith(start, end, processor);
  }

  @NotNull
  public String dumpState() {
    StringBuilder result = new StringBuilder();
    result.append("deferred mode: ").append(myText.isDeferredChangeMode() ? "on" : "off");
    result.append(", intervals:\n");
    for (int line = 0; line < getLineCount(); line++) {
      result.append(line).append(": ").append(getLineStartOffset(line)).append("-")
        .append(getLineEndOffset(line)).append(", ");
    }
    if (result.length() > 0) {
      result.setLength(result.length() - 1);
    }
    return result.toString();
  }
  
  private class MyCharArray extends CharArray {
    private MyCharArray(@NotNull char[] chars, int length) {
      super(0, chars, length);
    }

    @Override
    @NotNull
    protected DocumentEvent beforeChangedUpdate(int offset,
                                                CharSequence oldString,
                                                CharSequence newString,
                                                boolean wholeTextReplaced) {
      return DocumentImpl.this.beforeChangedUpdate(offset, oldString, newString, wholeTextReplaced);
    }

    @Override
    protected void afterChangedUpdate(@NotNull DocumentEvent event, long newModificationStamp) {
      ((DocumentImpl)event.getDocument()).changedUpdate(event, newModificationStamp);
    }

    @Override
    protected void assertWriteAccess() {
      DocumentImpl.this.assertWriteAccess();
    }

    @Override
    protected void assertReadAccess() {
      if (myAssertThreading) {
        final Application application = ApplicationManager.getApplication();
        if (application != null) {
          application.assertReadAccessAllowed();
        }
      }
    }
  }
}

