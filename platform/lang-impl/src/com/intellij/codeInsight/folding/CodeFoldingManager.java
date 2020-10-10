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

package com.intellij.codeInsight.folding;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.fileEditor.impl.text.CodeFoldingState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CodeFoldingManager {
  public static CodeFoldingManager getInstance(Project project){
    return project.getComponent(CodeFoldingManager.class);
  }

  public abstract void updateFoldRegions(@NotNull Editor editor);

  public abstract void forceDefaultState(@NotNull Editor editor);

  @Nullable
  public abstract Runnable updateFoldRegionsAsync(@NotNull Editor editor, boolean firstTime);

  @Nullable
  public abstract FoldRegion findFoldRegion(@NotNull Editor editor, int startOffset, int endOffset);
  public abstract FoldRegion[] getFoldRegionsAtOffset(@NotNull Editor editor, int offset);

  public abstract CodeFoldingState saveFoldingState(@NotNull Editor editor);
  public abstract void restoreFoldingState(@NotNull Editor editor, @NotNull CodeFoldingState state);
  
  public abstract void writeFoldingState(@NotNull CodeFoldingState state, @NotNull Element element) throws WriteExternalException;
  public abstract CodeFoldingState readFoldingState(@NotNull Element element, @NotNull Document document);

  public abstract void releaseFoldings(Editor editor);
  public abstract void buildInitialFoldings(Editor editor);

  /**
   * Asks do not perform automatic expansion of fold region that is located on the caret line during the next fold regions update.
   * <p/>
   * This method is necessary because fold regions that are located on caret line are automatically expanded to prevent problem
   * from IDEA-64687. However, we don't want such an expansion when we, for example, move method up or down.
   * 
   * @param editor    target editor
   */
  public abstract void allowFoldingOnCaretLine(@NotNull Editor editor);
}
