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
package com.intellij.openapi.vfs;

public abstract class VirtualFileAdapter implements VirtualFileListener {
  @Override
  public void propertyChanged(VirtualFilePropertyEvent event){
  }

  @Override
  public void contentsChanged(VirtualFileEvent event){
  }

  @Override
  public void fileCreated(VirtualFileEvent event){
  }

  @Override
  public void fileDeleted(VirtualFileEvent event){
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent event){
  }

  @Override
  public void fileCopied(VirtualFileCopyEvent event) {
    fileCreated(event);
  }

  @Override
  public void beforePropertyChange(VirtualFilePropertyEvent event){
  }

  @Override
  public void beforeContentsChange(VirtualFileEvent event){
  }

  @Override
  public void beforeFileDeletion(VirtualFileEvent event){
  }

  @Override
  public void beforeFileMovement(VirtualFileMoveEvent event){
  }
}
