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
package git4idea.stash;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.util.StringScanner;
import git4idea.config.GitConfigUtil;
import git4idea.util.GitUIUtil;
import git4idea.ui.StashInfo;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

import static com.intellij.notification.NotificationType.WARNING;

/**
 * The class contains utilities for creating and removing stashes.
 */
public class GitStashUtils {

  private static final Logger LOG = Logger.getInstance(GitStashUtils.class);

  private GitStashUtils() {
  }

  /**
   * Create stash for later use
   *
   * @param project the project to use
   * @param root    the root
   * @param message the message for the stash
   * @return true if the stash was created, false otherwise
   */
  public static boolean saveStash(@NotNull Project project, @NotNull VirtualFile root, final String message) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.STASH);
    handler.setNoSSH(true);
    handler.addParameters("save", message);
    String output = handler.run();
    return !output.startsWith("No local changes to save");
  }

  public static void loadStashStack(@NotNull Project project, @NotNull VirtualFile root, Consumer<StashInfo> consumer) {
    loadStashStack(project, root, Charset.forName(GitConfigUtil.getLogEncoding(project, root)), consumer);
  }

  public static void loadStashStack(@NotNull Project project, @NotNull VirtualFile root, final Charset charset,
                                    final Consumer<StashInfo> consumer) {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.STASH);
    h.setSilent(true);
    h.setNoSSH(true);
    h.addParameters("list");
    String out;
    try {
      h.setCharset(charset);
      out = h.run();
    }
    catch (VcsException e) {
      GitUIUtil.showOperationError(project, e, h.printableCommandLine());
      return;
    }
    for (StringScanner s = new StringScanner(out); s.hasMoreData();) {
      consumer.consume(new StashInfo(s.boundedToken(':'), s.boundedToken(':'), s.line().trim()));
    }
  }

  // drops stash (after completing conflicting merge during unstashing), shows a warning in case of error
  public static void dropStash(Project project, VirtualFile root) {
    final GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.STASH);
    handler.setNoSSH(true);
    handler.addParameters("drop");
    String output = null;
    try {
      output = handler.run();
    } catch (VcsException e) {
      LOG.info("dropStash " + output, e);
      GitUIUtil.notifyMessage(project, "Couldn't drop stash",
                              "Couldn't drop stash after resolving conflicts.<br/>Please drop stash manually.",
                              WARNING, false, handler.errors());
    }
  }

}
