/*
 * Copyright 2007-2011 Bas Leijdekkers
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
package com.siyeh.ipp.psiutils;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class HighlightUtil {

  private HighlightUtil() {
  }

  public static void highlightElements(
    @NotNull final Collection<? extends PsiElement> elementCollection,
    @NotNull final String statusBarText) {
    if (elementCollection.isEmpty()) {
      return;
    }
    final Application application = ApplicationManager.getApplication();
    application.invokeLater(new Runnable() {
      public void run() {
        final PsiElement[] elements =
          PsiUtilBase.toPsiElementArray(elementCollection);
        final PsiElement firstElement = elements[0];
        if (!firstElement.isValid()) {
          return;
        }
        final Project project = firstElement.getProject();
        final FileEditorManager editorManager =
          FileEditorManager.getInstance(project);
        final EditorColorsManager editorColorsManager =
          EditorColorsManager.getInstance();
        final Editor editor = editorManager.getSelectedTextEditor();
        if (editor == null) {
          return;
        }
        final EditorColorsScheme globalScheme =
          editorColorsManager.getGlobalScheme();
        final TextAttributes textattributes =
          globalScheme.getAttributes(
            EditorColors.SEARCH_RESULT_ATTRIBUTES);
        final HighlightManager highlightManager =
          HighlightManager.getInstance(project);
        highlightManager.addOccurrenceHighlights(
          editor, elements, textattributes, true, null);
        final FindManager findmanager =
          FindManager.getInstance(project);
        FindModel findmodel = findmanager.getFindNextModel();
        if (findmodel == null) {
          findmodel = findmanager.getFindInFileModel();
        }
        findmodel.setSearchHighlighters(true);
        findmanager.setFindWasPerformed();
        findmanager.setFindNextModel(findmodel);
        application.invokeLater(new Runnable() {
          public void run() {
            final WindowManager windowManager =
              WindowManager.getInstance();
            final StatusBar statusBar =
              windowManager.getStatusBar(project);
            statusBar.setInfo(statusBarText);
          }
        });
      }
    });
  }

  public static void highlightElement(
    @NotNull PsiElement element, @NotNull final String statusBarText) {
    final List<PsiElement> elements = Collections.singletonList(element);
    highlightElements(elements, statusBarText);
  }
}