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

package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.codeInsight.highlighting.NontrivialBraceMatcher;
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class TypedHandler extends TypedActionHandlerBase {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.TypedHandler");

  private static final Map<FileType,QuoteHandler> quoteHandlers = new HashMap<FileType, QuoteHandler>();

  private static final Map<Class<? extends Language>, QuoteHandler> ourBaseLanguageQuoteHandlers = new HashMap<Class<? extends Language>, QuoteHandler>();

  public TypedHandler(TypedActionHandler originalHandler){
    super(originalHandler);
  }

  @Nullable
  public static QuoteHandler getQuoteHandler(@NotNull PsiFile file, Editor editor) {
    FileType fileType = getFileType(file, editor);
    QuoteHandler quoteHandler = getQuoteHandlerForType(fileType);
    if (quoteHandler == null) {
      FileType fileFileType = file.getFileType();
      if (fileFileType != fileType) {
        quoteHandler = getQuoteHandlerForType(fileFileType);
      }
    }
    if (quoteHandler == null) {
      final Language baseLanguage = file.getViewProvider().getBaseLanguage();
      for (Map.Entry<Class<? extends Language>, QuoteHandler> entry : ourBaseLanguageQuoteHandlers.entrySet()) {
        if (entry.getKey().isInstance(baseLanguage)) {
          return entry.getValue();
        }
      }
    }
    return quoteHandler;
  }

  private static FileType getFileType(PsiFile file, Editor editor) {
    FileType fileType = file.getFileType();
    Language language = PsiUtilBase.getLanguageInEditor(editor, file.getProject());
    if (language != null && language != PlainTextLanguage.INSTANCE) {
      LanguageFileType associatedFileType = language.getAssociatedFileType();
      if (associatedFileType != null) fileType = associatedFileType;
    }
    return fileType;
  }

  public static void registerBaseLanguageQuoteHandler(Class<? extends Language> languageClass, QuoteHandler quoteHandler) {
    ourBaseLanguageQuoteHandlers.put(languageClass, quoteHandler);
  }

  public static QuoteHandler getQuoteHandlerForType(final FileType fileType) {
    if (!quoteHandlers.containsKey(fileType)) {
      QuoteHandler handler = null;
      final QuoteHandlerEP[] handlerEPs = Extensions.getExtensions(QuoteHandlerEP.EP_NAME);
      for(QuoteHandlerEP ep: handlerEPs) {
        if (ep.fileType.equals(fileType.getName())) {
          handler = ep.getHandler();
          break;
        }
      }
      quoteHandlers.put(fileType, handler);
    }
    return quoteHandlers.get(fileType);
  }

  /** @see QuoteHandlerEP */
  @Deprecated
  public static void registerQuoteHandler(FileType fileType, QuoteHandler quoteHandler) {
    quoteHandlers.put(fileType, quoteHandler);
  }

  public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null || editor.isColumnMode()){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, charTyped, dataContext);
      }
      return;
    }

    PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);

    if (file == null){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, charTyped, dataContext);
      }
      return;
    }

    if (editor.isViewer()) return;

    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
       return;
    }

    Editor injectedEditor = injectedEditorIfCharTypedIsSignificant(charTyped, editor, file);
    if (injectedEditor != editor) {
      file = PsiDocumentManager.getInstance(project).getPsiFile(injectedEditor.getDocument());
      editor = injectedEditor;
    }

    final TypedHandlerDelegate[] delegates = Extensions.getExtensions(TypedHandlerDelegate.EP_NAME);
    AutoPopupController autoPopupController = AutoPopupController.getInstance(project);

    boolean handled = false;
    for(TypedHandlerDelegate delegate: delegates) {
      final TypedHandlerDelegate.Result result = delegate.checkAutoPopup(charTyped, project, editor, file);
      handled = result == TypedHandlerDelegate.Result.STOP;
      if (result != TypedHandlerDelegate.Result.CONTINUE) {
        break;
      }
    }

    if (!handled) {
      if (charTyped == '.') {
        autoPopupController.autoPopupMemberLookup(editor, null);
      }

      if ((charTyped == '(' || charTyped == ',') && !isInsideStringLiteral(editor, file)) {
        autoPopupController.autoPopupParameterInfo(editor, null);
      }
    }

    if (!editor.isInsertMode()){
      myOriginalHandler.execute(editor, charTyped, dataContext);
      return;
    }

    if (editor.getSelectionModel().hasSelection()){
      EditorModificationUtil.deleteSelectedText(editor);
    }

    FileType fileType = getFileType(file, editor);

    for(TypedHandlerDelegate delegate: delegates) {
      final TypedHandlerDelegate.Result result = delegate.beforeCharTyped(charTyped, project, editor, file, fileType);
      if (result == TypedHandlerDelegate.Result.STOP) {
        return;
      }
      if (result == TypedHandlerDelegate.Result.DEFAULT) {
        break;
      }
    }

    if (!editor.getSelectionModel().hasBlockSelection()) {
      if (')' == charTyped || ']' == charTyped || '}' == charTyped) {
        if (StdFileTypes.PLAIN_TEXT != fileType) {
          if (handleRParen(editor, fileType, charTyped)) return;
        }
      }
      else if ('"' == charTyped || '\'' == charTyped || '`' == charTyped) {
        if (handleQuote(editor, charTyped, dataContext, file)) return;
      }
    }

    long modificationStampBeforeTyping = editor.getDocument().getModificationStamp();
    myOriginalHandler.execute(editor, charTyped, dataContext);
    AutoHardWrapHandler.getInstance().wrapLineIfNecessary(editor, dataContext, modificationStampBeforeTyping);

    if (('(' == charTyped || '[' == charTyped || '{' == charTyped) &&
        CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
        !editor.getSelectionModel().hasBlockSelection() && fileType != StdFileTypes.PLAIN_TEXT) {
      handleAfterLParen(editor, fileType, charTyped);
    }
    else if ('}' == charTyped) {
      indentClosingBrace(project, editor);
    }

    for(TypedHandlerDelegate delegate: delegates) {
      final TypedHandlerDelegate.Result result = delegate.charTyped(charTyped, project, editor, file);
      if (result == TypedHandlerDelegate.Result.STOP) {
        return;
      }
      if (result == TypedHandlerDelegate.Result.DEFAULT) {
        break;
      }
    }
    if ('{' == charTyped) {
      indentOpenedBrace(project, editor);
    }
  }

  private static boolean isInsideStringLiteral(final Editor editor, final PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    final ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(element.getLanguage());
    if (definition != null) {
      final TokenSet stringLiteralElements = definition.getStringLiteralElements();
      final ASTNode node = element.getNode();
      if (node == null) return false;
      final IElementType elementType = node.getElementType();
      if (stringLiteralElements.contains(elementType)) {
        return true;
      }
      PsiElement parent = element.getParent();
      if (parent != null) {
        ASTNode parentNode = parent.getNode();
        if (parentNode != null && stringLiteralElements.contains(parentNode.getElementType())) {
          return true;
        }
      }
    }
    return false;
  }

  static Editor injectedEditorIfCharTypedIsSignificant(final char charTyped, Editor editor, PsiFile oldFile) {
    int offset = editor.getCaretModel().getOffset();
    // even for uncommitted document try to retrieve injected fragment that has been there recently
    // we are assuming here that when user is (even furiously) typing, injected language would not change
    // and thus we can use its lexer to insert closing braces etc
    for (DocumentWindow documentWindow : InjectedLanguageUtil.getCachedInjectedDocuments(oldFile)) {
      if (documentWindow.isValid() && documentWindow.containsRange(offset, offset)) {
        PsiFile injectedFile = PsiDocumentManager.getInstance(oldFile.getProject()).getPsiFile(documentWindow);
        final Editor injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, injectedFile);
        // IDEA-52375 fix: last quote sign should be handled by outer language quote handler
        final CharSequence charsSequence = editor.getDocument().getCharsSequence();
        if (injectedEditor.getCaretModel().getOffset() == injectedEditor.getDocument().getTextLength() &&
            offset < charsSequence.length() && charTyped == charsSequence.charAt(offset)) {
          return editor;
        }
        return injectedEditor;
      }
    }

    return editor;
  }

  private static void handleAfterLParen(Editor editor, FileType fileType, char lparenChar){
    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    boolean atEndOfDocument = offset == editor.getDocument().getTextLength();

    if (!atEndOfDocument) iterator.retreat();
    if (iterator.atEnd()) return;
    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
    if (iterator.atEnd()) return;
    IElementType braceTokenType = iterator.getTokenType();
    final CharSequence fileText = editor.getDocument().getCharsSequence();
    if (!braceMatcher.isLBraceToken(iterator, fileText, fileType)) return;

    if (!iterator.atEnd()) {
      iterator.advance();

      if (!iterator.atEnd()) {
        if (!BraceMatchingUtil.isPairedBracesAllowedBeforeTypeInFileType(braceTokenType, iterator.getTokenType(), fileType)) {
          return;
        }
        if (BraceMatchingUtil.isLBraceToken(iterator, fileText, fileType)) {
          return;
        }
      }

      iterator.retreat();
    }

    int lparenOffset = BraceMatchingUtil.findLeftmostLParen(iterator, braceTokenType, fileText,fileType);
    if (lparenOffset < 0) lparenOffset = 0;

    iterator = ((EditorEx)editor).getHighlighter().createIterator(lparenOffset);
    boolean matched = BraceMatchingUtil.matchBrace(fileText, fileType, iterator, true, true);

    if (!matched) {
      String text;
      if (lparenChar == '(') {
        text = ")";
      }
      else if (lparenChar == '[') {
        text = "]";
      }
      else if (lparenChar == '<') {
        text = ">";
      }
      else if (lparenChar == '{') {
        text = "}";
      }
      else {
        throw new AssertionError("Unknown char "+lparenChar);
      }
      editor.getDocument().insertString(offset, text);
    }
  }

  public static boolean handleRParen(Editor editor, FileType fileType, char charTyped) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();

    if (offset == editor.getDocument().getTextLength()) return false;

    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()) return false;

    if (iterator.getEnd() - iterator.getStart() != 1 || editor.getDocument().getCharsSequence().charAt(iterator.getStart()) != charTyped) {
      return false;
    }

    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
    CharSequence text = editor.getDocument().getCharsSequence();
    if (!braceMatcher.isRBraceToken(iterator, text, fileType)) {
      return false;
    }

    IElementType tokenType = iterator.getTokenType();
    
    iterator.retreat();

    IElementType lparenTokenType = braceMatcher.getOppositeBraceTokenType(tokenType);
    int lparenthOffset = BraceMatchingUtil.findLeftmostLParen(
      iterator,
      lparenTokenType,
      text,
      fileType
    );

    if (lparenthOffset < 0) {
      if (braceMatcher instanceof NontrivialBraceMatcher) {
        for(IElementType t:((NontrivialBraceMatcher)braceMatcher).getOppositeBraceTokenTypes(tokenType)) {
          if (t == lparenTokenType) continue;
          lparenthOffset = BraceMatchingUtil.findLeftmostLParen(
            iterator,
            t, text,
            fileType
          );
          if (lparenthOffset >= 0) break;
        }
      }
      if (lparenthOffset < 0) return false;
    }

    iterator = ((EditorEx) editor).getHighlighter().createIterator(lparenthOffset);
    boolean matched = BraceMatchingUtil.matchBrace(text, fileType, iterator, true, true);

    if (!matched) return false;

    editor.getCaretModel().moveToOffset(offset + 1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return true;
  }

  private boolean handleQuote(Editor editor, char quote, DataContext dataContext, PsiFile file) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) return false;
    final QuoteHandler quoteHandler = getQuoteHandler(file, editor);
    if (quoteHandler == null) return false;

    int offset = editor.getCaretModel().getOffset();

    final Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    int length = document.getTextLength();
    if (isTypingEscapeQuote(editor, quoteHandler, offset)) return false;

    if (offset < length && chars.charAt(offset) == quote){
      if (isClosingQuote(editor, quoteHandler, offset)){
        editor.getCaretModel().moveToOffset(offset + 1);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        return true;
      }
    }

    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);

    if (!iterator.atEnd()){
      IElementType tokenType = iterator.getTokenType();
      if (quoteHandler instanceof JavaLikeQuoteHandler) {
        try {
          if (!((JavaLikeQuoteHandler)quoteHandler).isAppropriateElementTypeForLiteral(tokenType)) return false;
        }
        catch (AbstractMethodError incompatiblePluginErrorThatDoesNotInterestUs) {
          // ignore
        }
      }
    }

    myOriginalHandler.execute(editor, quote, dataContext);
    offset = editor.getCaretModel().getOffset();

    if (isOpeningQuote(editor, quoteHandler, offset - 1) &&
        hasNonClosedLiterals(editor, quoteHandler, offset - 1)) {
      if (offset == document.getTextLength() ||
          !Character.isUnicodeIdentifierPart(document.getCharsSequence().charAt(offset))) { //any better heuristic or an API?
        document.insertString(offset, String.valueOf(quote));
      }
    }

    return true;
  }

  private static boolean isClosingQuote(Editor editor, QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isClosingQuote(iterator,offset);
  }

  private static boolean isOpeningQuote(Editor editor, QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isOpeningQuote(iterator, offset);
  }

  private static boolean hasNonClosedLiterals(Editor editor, QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()) {
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.hasNonClosedLiteral(editor, iterator, offset);
  }

  private static boolean isTypingEscapeQuote(Editor editor, QuoteHandler quoteHandler, int offset){
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    int offset1 = CharArrayUtil.shiftBackward(chars, offset - 1, "\\");
    int slashCount = offset - 1 - offset1;
    return slashCount % 2 != 0 && isInsideLiteral(editor, quoteHandler, offset);
  }

  private static boolean isInsideLiteral(Editor editor, QuoteHandler quoteHandler, int offset){
    if (offset == 0) return false;

    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset - 1);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isInsideLiteral(iterator);
  }

  private static void indentClosingBrace(@NotNull Project project, @NotNull Editor editor){
    indentBrace(project, editor, '}');
  }

  static void indentOpenedBrace(@NotNull Project project, @NotNull Editor editor){
    indentBrace(project, editor, '{');
  }

  private static void indentBrace(@NotNull final Project project, @NotNull final Editor editor, final char braceChar) {
    final int offset = editor.getCaretModel().getOffset() - 1;
    final Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    if (offset < 0 || chars.charAt(offset) != braceChar) return;

    int spaceStart = CharArrayUtil.shiftBackward(chars, offset - 1, " \t");
    if (spaceStart < 0 || chars.charAt(spaceStart) == '\n' || chars.charAt(spaceStart) == '\r'){
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      documentManager.commitDocument(document);

      final PsiFile file = documentManager.getPsiFile(document);
      if (file == null || !file.isWritable()) return;
      PsiElement element = file.findElementAt(offset);
      if (element == null) return;

      EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
      HighlighterIterator iterator = highlighter.createIterator(offset);

      BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(file.getFileType(), iterator);
      if (element.getNode() != null && braceMatcher.isStructuralBrace(iterator, chars, file.getFileType())) {
        final Runnable action = new Runnable() {
          public void run(){
            try{
              int newOffset = CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);
              editor.getCaretModel().moveToOffset(newOffset + 1);
              editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
              editor.getSelectionModel().removeSelection();
            }
            catch(IncorrectOperationException e){
              LOG.error(e);
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }
  }

}

