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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * IntelliJ code provides a lot of statical bindings to the interested pieces of data. For example we need to execute code
 * like below to get list of modules for the target project:
 * <pre>
 *   ModuleManager.getInstance(project).getModules()
 * </pre>
 * That means that it's not possible to test target classes in isolation if corresponding infrastructure is not set up.
 * However, we don't want to set it up if we execute a simple standalone test.
 * <p/>
 * This interface is intended to encapsulate access to the underlying IntelliJ functionality.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @author Kirill Likhodedov
 */
public interface PlatformFacade {

  @NotNull
  AbstractVcs getVcs(@NotNull Project project);

  @NotNull
  ProjectLevelVcsManager getVcsManager(@NotNull Project project);

  @NotNull
  Notificator getNotificator(@NotNull Project project);

  void showDialog(@NotNull DialogWrapper dialog);

  @NotNull
  ProjectRootManager getProjectRootManager(@NotNull Project project);

  /**
   * Invokes {@link com.intellij.openapi.application.Application#runReadAction(Computable)}.
   */
  <T> T runReadAction(@NotNull Computable<T> computable);

  void runReadAction(@NotNull Runnable runnable);

  @Nullable
  IdeaPluginDescriptor getPluginByClassName(@NotNull String name);

}
