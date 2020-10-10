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
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@linkplain PathChooserDialog} (to remove in IDEA 12)
 */
@SuppressWarnings("UnusedDeclaration")
public interface MacFileChooserDialog extends FileChooserDialog {
  DataKey<Boolean> NATIVE_MAC_FILE_CHOOSER_ENABLED = DataKey.create("native.mac.file.chooser.enabled");
  DataKey<Boolean> NATIVE_MAC_FILE_CHOOSER_SHOW_HIDDEN_FILES_ENABLED = PathChooserDialog.NATIVE_MAC_CHOOSER_SHOW_HIDDEN_FILES;

  void chooseWithSheet(@Nullable VirtualFile toSelect, @Nullable Project project, @NotNull final MacFileChooserCallback callback);

  interface MacFileChooserCallback {
    void onChosen(@NotNull VirtualFile[] files);
  }
}
