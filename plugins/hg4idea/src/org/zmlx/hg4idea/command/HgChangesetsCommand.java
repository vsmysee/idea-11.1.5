// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.command;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgChangesetUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for all commands that need to parse changeset information.
 */
public abstract class HgChangesetsCommand {

  private static final Logger LOG = Logger.getInstance(HgChangesetsCommand.class.getName());

  protected final Project project;
  protected final String command;

  public HgChangesetsCommand(Project project, String command) {
    this.project = project;
    this.command = command;
  }

  public List<HgRevisionNumber> execute(VirtualFile repo) {
    return getRevisions(repo);
  }

  protected List<HgRevisionNumber> getRevisions(VirtualFile repo) {
    List<String> args = new ArrayList<String>(Arrays.asList(
      "--template",
      HgChangesetUtil.makeTemplate("{rev}", "{node|short}", "{author}", "{desc|firstline}"),
      "--quiet"
    ));

    addArguments(args);

    HgCommandResult result = executeCommand(repo, args);

    if (result == null) {
      return Collections.emptyList();
    }

    String output = result.getRawOutput();
    if (StringUtils.isEmpty(output)) {
      return Collections.emptyList();
    }
    
    String[] changesets = output.split(HgChangesetUtil.CHANGESET_SEPARATOR);
    List<HgRevisionNumber> revisions = new ArrayList<HgRevisionNumber>(changesets.length);
    
    for(String changeset: changesets) {
      String[] parts = StringUtils.split(changeset, HgChangesetUtil.ITEM_SEPARATOR);
      if (parts.length == 4) {
        revisions.add(HgRevisionNumber.getInstance(parts[0], parts[1], parts[2], parts[3]));
      } else {
        LOG.warn("Could not parse changeset [" + changeset + "]");
      }
    }
    
    return revisions;
  }

  @Nullable
  protected HgCommandResult executeCommand(VirtualFile repo, List<String> args) {
    final HgCommandExecutor executor = new HgCommandExecutor(project);
    executor.setSilent(isSilentCommand());
    return executor.executeInCurrentThread(repo, command, args);
  }

  protected boolean isSilentCommand() {
    return false;
  }

  protected abstract void addArguments(List<String> args);
}