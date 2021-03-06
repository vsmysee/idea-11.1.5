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
package com.intellij.testFramework;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static org.junit.Assert.assertTrue;

public class TempFiles {
  private final Collection<File> myFilesToDelete;

  public TempFiles(Collection<File> filesToDelete) {
    myFilesToDelete = filesToDelete;
  }

  @Nullable
  public VirtualFile createVFile(String prefix) {
    return getVFileByFile(createTempFile(prefix));
  }

  @Nullable
  public VirtualFile createVFile(String prefix, String postfix) {
    return getVFileByFile(createTempFile(prefix, postfix));
  }

  public File createTempFile(String prefix) {
    return createTempFile(prefix, "_Temp_File_");
  }

  public File createTempFile(String prefix, String postfix) {
    try {
      File tempFile = FileUtil.createTempFile(prefix, postfix);
      tempFileCreated(tempFile);
      getVFileByFile(tempFile);
      return tempFile;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void tempFileCreated(File tempFile) {
    myFilesToDelete.add(tempFile);
    tempFile.deleteOnExit();
  }

  @Nullable
  public static VirtualFile getVFileByFile(File tempFile) {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
  }

  public File createTempDir() {
    return createTempDir("dir");
  }

  private File createTempDir(String prefix) {
    try {
      File dir = FileUtil.createTempDirectory(prefix, "test");
      tempFileCreated(dir);
      getVFileByFile(dir);
      return dir;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public VirtualFile createTempVDir() {
    return createTempVDir("dir");
  }

  @Nullable
  public VirtualFile createTempVDir(String prefix) {
    return getVFileByFile(createTempDir(prefix));
  }

  public String createTempPath() {
    File tempFile = createTempFile("xxx");
    String absolutePath = tempFile.getAbsolutePath();
    assertTrue(absolutePath, tempFile.delete());
    return absolutePath;
  }

  public void deleteAll() {
    for (File file : myFilesToDelete) {
      FileUtil.delete(file);
    }
  }

  public VirtualFile createVFile(VirtualFile parentDir, String name, String text) {
    try {
      final VirtualFile virtualFile = parentDir.createChildData(this, name);
      VfsUtil.saveText(virtualFile, text + "\n");
      return virtualFile;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
