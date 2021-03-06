/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.psi.formatter;

import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiToDocumentSynchronizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FormattingDocumentModelImpl implements FormattingDocumentModel {

  private final WhiteSpaceFormattingStrategy myWhiteSpaceStrategy;
  //private final CharBuffer myBuffer = CharBuffer.allocate(1);
  private final Document myDocument;
  private final PsiFile myFile;

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.FormattingDocumentModelImpl");

  public FormattingDocumentModelImpl(final Document document, PsiFile file) {
    myDocument = document;
    myFile = file;
    if (file != null) {
      Language language = file.getLanguage();
      myWhiteSpaceStrategy = WhiteSpaceFormattingStrategyFactory.getStrategy(language);
    }
    else {
      myWhiteSpaceStrategy = WhiteSpaceFormattingStrategyFactory.getStrategy();
    }
  }

  public static FormattingDocumentModelImpl createOn(PsiFile file) {
    Document document = getDocumentToBeUsedFor(file);
    if (document != null) {
      if (PsiDocumentManager.getInstance(file.getProject()).isUncommited(document)) {
        LOG.error("Document is uncommitted");
      }
      if (!document.getText().equals(file.getText())) {
        LOG.error("Document and psi file texts should be equal: file " + file);
      }
      return new FormattingDocumentModelImpl(document, file);
    }
    else {
      return new FormattingDocumentModelImpl(new DocumentImpl(file.getText()), file);
    }

  }

  @Nullable
  public static Document getDocumentToBeUsedFor(final PsiFile file) {
    final Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return null;
    if (PsiDocumentManager.getInstance(project).isUncommited(document)) return null;
    PsiToDocumentSynchronizer synchronizer = ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(file.getProject())).getSynchronizer();
    if (synchronizer.isDocumentAffectedByTransactions(document)) return null;

    return document;
  }

  @Override
  public int getLineNumber(int offset) {
    LOG.assertTrue (offset <= myDocument.getTextLength());
    return myDocument.getLineNumber(offset);
  }

  @Override
  public int getLineStartOffset(int line) {
    return myDocument.getLineStartOffset(line);
  }

  @Override
  public CharSequence getText(final TextRange textRange) {
    if (textRange.getStartOffset() < 0 || textRange.getEndOffset() > myDocument.getTextLength()) {
      LOG.error(String.format(
        "Please submit a ticket to the tracker and attach current source file to it!%nInvalid processing detected: given text "
        + "range (%s) targets non-existing regions (the boundaries are [0; %d)). File's language: %s",
        textRange, myDocument.getTextLength(), myFile.getLanguage()
      ));
    }
    return myDocument.getCharsSequence().subSequence(textRange.getStartOffset(), textRange.getEndOffset());
  }

  @Override
  public int getTextLength() {
    return myDocument.getTextLength();
  }

  @Override
  public Document getDocument() {
    return myDocument;
  }

  public PsiFile getFile() {
    return myFile;
  }

  @Override
  public boolean containsWhiteSpaceSymbolsOnly(int startOffset, int endOffset) {
    return myWhiteSpaceStrategy.check(myDocument.getCharsSequence(), startOffset, endOffset) >= endOffset;
  }

  @NotNull
  @Override
  public CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText, int startOffset, int endOffset,
                                                  boolean changedViaPsi)
  {
    if (!changedViaPsi) {
        return myWhiteSpaceStrategy.adjustWhiteSpaceIfNecessary(whiteSpaceText, myDocument.getCharsSequence(), startOffset, endOffset); 
    }
    
    final PsiElement element = myFile.findElementAt(startOffset);
    if (element == null) {
      return whiteSpaceText;
    }
    else {
      return myWhiteSpaceStrategy.adjustWhiteSpaceIfNecessary(whiteSpaceText, element, startOffset, endOffset);
    }
  }

  //@Override
  //public boolean isWhiteSpaceSymbol(char symbol) {
  //  myBuffer.put(0, symbol);
  //  return myWhiteSpaceStrategy.check(myBuffer, 0, 1) > 0;
  //}

  public static boolean canUseDocumentModel(@NotNull Document document,@NotNull PsiFile file) {
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
    return !psiDocumentManager.isUncommited(document) &&
           !psiDocumentManager.isDocumentBlockedByPsi(document) &&
           file.getText().equals(document.getText());
  }
}
