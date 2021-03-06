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
package com.intellij.execution.process;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.concurrent.Future;

public class OSProcessHandler extends BaseOSProcessHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.OSProcessHandler");

  public OSProcessHandler(@NotNull final Process process, @Nullable final String commandLine, final Charset defaultCharset) {
    super(process, commandLine, defaultCharset);
  }

  public OSProcessHandler(@NotNull final Process process, @Nullable final String commandLine) {
    super(process, commandLine, EncodingManager.getInstance().getDefaultCharset());
  }

  @Override
  protected Future<?> executeOnPooledThread(Runnable task) {
    final Application application = ApplicationManager.getApplication();

    if (application != null) {
      return application.executeOnPooledThread(task);
    }

    return super.executeOnPooledThread(task);
  }

  protected boolean shouldDestroyProcessRecursively(){
    // Override this method if you want to kill process recursively (whole process try) by default
    // such behaviour is better than default java one, which doesn't kill children processes
    return false;
  }

  protected void doDestroyProcess() {
    // Override this method if you want to customize default destroy behaviour, e.g.
    // if you want use some soft-kill.
    final Process process = getProcess();
    if (shouldDestroyProcessRecursively()) {
      killProcessTree(process);
    }
    else {
      process.destroy();
    }
  }

  /**
   * Kill whole process tree.
   * @param process Process
   * @return True if process tree has been successfully killed.
   */
  protected boolean killProcessTree(final Process process) {
    LOG.debug("killing process tree");
    final boolean destroyed = OSProcessManager.getInstance().killProcessTree(process);
    if (!destroyed) {
      LOG.warn("Cannot kill process tree. Trying to destroy process using Java API. Cmdline:\n" + myCommandLine);
      process.destroy();
    }
    return destroyed;
  }
}
