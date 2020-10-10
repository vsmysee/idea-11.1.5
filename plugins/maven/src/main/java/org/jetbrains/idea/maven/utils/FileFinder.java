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
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.model.MavenConstants;

import java.util.List;

public class FileFinder {
  public static List<VirtualFile> findPomFiles(VirtualFile[] roots,
                                               boolean lookForNested,
                                               MavenProgressIndicator indicator,
                                               List<VirtualFile> result) throws MavenProcessCanceledException {
    for (VirtualFile f : roots) {
      indicator.checkCanceled();

      try {
        indicator.setText2(f.getPath());

        if (f.isDirectory()) {
          if (lookForNested) {
            f.refresh(false, false);
            findPomFiles(f.getChildren(), lookForNested, indicator, result);
          }
        }
        else {
          if (f.getName().equalsIgnoreCase(MavenConstants.POM_XML)) {
            result.add(f);
          }
        }
      }
      catch (InvalidVirtualFileAccessException e) {
        // we are accessing VFS without read action here so such exception may occasionally occur
        MavenLog.LOG.info(e);
      }
    }

    return result;
  }
}
