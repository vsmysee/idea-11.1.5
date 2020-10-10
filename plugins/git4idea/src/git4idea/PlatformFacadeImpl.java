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
package git4idea;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public class PlatformFacadeImpl implements PlatformFacade {

  @NotNull
  @Override
  public ProjectLevelVcsManager getVcsManager(@NotNull Project project) {
    return ProjectLevelVcsManager.getInstance(project);
  }

  @NotNull
  @Override
  public Notificator getNotificator(@NotNull Project project) {
    return Notificator.getInstance(project);
  }

  @Override
  public void showDialog(@NotNull DialogWrapper dialog) {
    dialog.show();
  }

  @NotNull
  @Override
  public ProjectRootManager getProjectRootManager(@NotNull Project project) {
    return ProjectRootManager.getInstance(project);
  }

  @Override
  public <T> T runReadAction(@NotNull Computable<T> computable) {
    return ApplicationManager.getApplication().runReadAction(computable);
  }

  @Override
  public void runReadAction(@NotNull Runnable runnable) {
    ApplicationManager.getApplication().runReadAction(runnable);
  }

  @Nullable
  @Override
  public IdeaPluginDescriptor getPluginByClassName(@NotNull String name) {
    return PluginManager.getPlugin(PluginManager.getPluginByClassName(name));
  }

  @NotNull
  @Override
  public AbstractVcs getVcs(@NotNull Project project) {
    return ProjectLevelVcsManager.getInstance(project).findVcsByName(GitVcs.NAME);
  }
}
