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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.idea.maven.dom.MavenDomUtil;

public class MavenProblemFileHighlighter implements Condition<VirtualFile> {
  private final Project myProject;

  public MavenProblemFileHighlighter(Project project) {
    myProject = project;
  }

  public boolean value(final VirtualFile file) {
    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      return psiFile != null && MavenDomUtil.isMavenFile(psiFile);
    }
    finally {
      accessToken.finish();
    }
  }
}
