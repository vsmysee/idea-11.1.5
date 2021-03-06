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
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class CoreJarHandler extends JarHandlerBase {

  private final CoreJarFileSystem myFileSystem;
  private final VirtualFile myRoot;

  public CoreJarHandler(CoreJarFileSystem fileSystem, String path) {
    super(path);
    myFileSystem = fileSystem;

    Map<EntryInfo, CoreJarVirtualFile> entries = new HashMap<EntryInfo, CoreJarVirtualFile>();

    for (EntryInfo info : getEntriesMap().values()) {
      getOrCreateFile(info, entries);
    }

    myRoot = getOrCreateFile(getEntryInfo(""), entries);
  }

  private CoreJarVirtualFile getOrCreateFile(EntryInfo info, Map<EntryInfo, CoreJarVirtualFile> entries) {
    CoreJarVirtualFile answer = entries.get(info);
    if (answer == null) {
      EntryInfo parentEntry = info.parent;
      answer = new CoreJarVirtualFile(this, info, parentEntry != null ? getOrCreateFile(parentEntry, entries) : null);
      entries.put(info, answer);
    }

    return answer;
  }

  @Nullable
  public VirtualFile findFileByPath(String pathInJar) {
    return myRoot != null ? myRoot.findFileByRelativePath(pathInJar) : null;
  }

  public CoreJarFileSystem getFileSystem() {
    return myFileSystem;
  }
}
