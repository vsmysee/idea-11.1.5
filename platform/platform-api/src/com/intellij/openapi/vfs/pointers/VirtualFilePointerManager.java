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
package com.intellij.openapi.vfs.pointers;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class VirtualFilePointerManager implements Disposable {
  public static VirtualFilePointerManager getInstance() {
    return ApplicationManager.getApplication().getComponent(VirtualFilePointerManager.class);
  }

  /** @see #create(String, com.intellij.openapi.Disposable, VirtualFilePointerListener) */
  @Deprecated
  public abstract VirtualFilePointer create(String url, VirtualFilePointerListener listener);

  /** @see #create(com.intellij.openapi.vfs.VirtualFile, com.intellij.openapi.Disposable, VirtualFilePointerListener)  */
  @Deprecated
  public abstract VirtualFilePointer create(VirtualFile file, VirtualFilePointerListener listener);

  /** @see #duplicate(VirtualFilePointer, com.intellij.openapi.Disposable, VirtualFilePointerListener)  */
  @Deprecated
  public abstract VirtualFilePointer duplicate (VirtualFilePointer pointer, VirtualFilePointerListener listener);

  @Deprecated
  public abstract void kill(VirtualFilePointer pointer, VirtualFilePointerListener listener);

  /** @see #createContainer(com.intellij.openapi.Disposable)  */
  @Deprecated
  public abstract VirtualFilePointerContainer createContainer();

  @Deprecated
  public abstract VirtualFilePointerContainer createContainer(VirtualFilePointerFactory factory);

  @NotNull
  public abstract VirtualFilePointer create(@NotNull String url, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener);

  @NotNull
  public abstract VirtualFilePointer create(@NotNull VirtualFile file, @NotNull Disposable parent, @Nullable VirtualFilePointerListener listener);

  @NotNull
  public abstract VirtualFilePointer duplicate(@NotNull VirtualFilePointer pointer, @NotNull Disposable parent,
                                               @Nullable VirtualFilePointerListener listener);

  @NotNull
  public abstract VirtualFilePointerContainer createContainer(@NotNull Disposable parent);

  @NotNull
  public abstract VirtualFilePointerContainer createContainer(@NotNull Disposable parent, @Nullable VirtualFilePointerListener listener);
}
