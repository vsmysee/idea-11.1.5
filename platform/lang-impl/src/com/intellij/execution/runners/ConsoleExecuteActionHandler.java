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
package com.intellij.execution.runners;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.process.ConsoleHistoryModel;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author traff
 */
public class ConsoleExecuteActionHandler {
  private final ProcessHandler myProcessHandler;
  private final boolean myPreserveMarkup;
  private boolean myAddCurrentToHistory = true;
  private ConsoleHistoryModel myConsoleHistoryModel;

  public ConsoleExecuteActionHandler(ProcessHandler processHandler, boolean preserveMarkup) {
    myProcessHandler = processHandler;
    myConsoleHistoryModel = new ConsoleHistoryModel();
    myPreserveMarkup = preserveMarkup;
  }

  public void setConsoleHistoryModel(ConsoleHistoryModel consoleHistoryModel) {
    myConsoleHistoryModel = consoleHistoryModel;
  }

  public ConsoleHistoryModel getConsoleHistoryModel() {
    return myConsoleHistoryModel;
  }

  public void runExecuteAction(LanguageConsoleImpl languageConsole) {

    // Process input and add to history
    final Document document = languageConsole.getCurrentEditor().getDocument();
    final String text = document.getText();
    final TextRange range = new TextRange(0, document.getTextLength());

    languageConsole.getCurrentEditor().getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    if (myAddCurrentToHistory) {
      languageConsole.addCurrentToHistory(range, false, myPreserveMarkup);
    }

    languageConsole.setInputText("");

    final UndoManager manager = languageConsole.getProject() == null ? UndoManager.getGlobalInstance() : UndoManager.getInstance(
      languageConsole.getProject());

    ((UndoManagerImpl)manager).invalidateActionsFor(DocumentReferenceManager.getInstance().create(document));

    myConsoleHistoryModel.addToHistory(text);
    // Send to interpreter / server

    processLine(text);
  }

  public void processLine(String line) {
    sendText(line + "\n");
  }

  public void sendText(String line) {
    //final Charset charset = myProcessHandler.getCharset();
    final OutputStream outputStream = myProcessHandler.getProcessInput();
    try {
      //byte[] bytes = (line + "\n").getBytes(charset.name());
      byte[] bytes = line.getBytes();
      outputStream.write(bytes);
      outputStream.flush();
    }
    catch (IOException e) {
      // ignore
    }
  }

  public void setAddCurrentToHistory(boolean addCurrentToHistory) {
    myAddCurrentToHistory = addCurrentToHistory;
  }

  public void finishExecution() {
  }

  public String getEmptyExecuteAction() {
    return "Console.Execute";
  }
}
