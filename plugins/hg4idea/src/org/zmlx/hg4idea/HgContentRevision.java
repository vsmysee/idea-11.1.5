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
package org.zmlx.hg4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgCatCommand;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.UnsupportedEncodingException;

public class HgContentRevision implements ContentRevision {

  private final Project myProject;
  private final HgFile myHgFile;
  @NotNull private final HgRevisionNumber myRevisionNumber;

  private FilePath filePath;
  private String content;

  public HgContentRevision(Project project, HgFile hgFile, @NotNull HgRevisionNumber revisionNumber) {
    myProject = project;
    myHgFile = hgFile;
    myRevisionNumber = revisionNumber;
  }

  @Nullable
  public String getContent() throws VcsException {
    if (StringUtils.isBlank(content)) {
      if (myRevisionNumber.isWorkingVersion()) {
        content = VcsUtil.getFileContent(myHgFile.getFile().getPath());
      } else {
        HgFile fileToCat = HgUtil.getFileNameInTargetRevision(myProject, myRevisionNumber, myHgFile);
        content = new HgCatCommand(myProject).execute(fileToCat, myRevisionNumber, getFile().getCharset());
      }
    }
    return content;
  }

  /**
   * A wrapper for getContent(), that just converts String to byte[]
   */
  @Nullable
  public byte[] getContentAsBytes() throws VcsException {
    final String content = getContent();
    if (content == null) {
      return null;
    }
    try {
      final VirtualFile vf = VcsUtil.getVirtualFile(myHgFile.getFile());
      if (vf == null) {
        return null;
      }
      return content.getBytes(vf.getCharset().name());
    } catch (UnsupportedEncodingException e) {
      throw new VcsException("Couldn't retrieve file content due to a UnsupportedEncodingException", e);
    }
  }

  @NotNull
  public FilePath getFile() {
    if (filePath == null) {
      filePath = myHgFile.toFilePath();
    }
    return filePath;
  }

  @NotNull
  public HgRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
      .append(myHgFile)
      .append(myRevisionNumber)
      .toHashCode();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    }
    if (!(object instanceof HgContentRevision)) {
      return false;
    }
    HgContentRevision that = (HgContentRevision) object;
    return new EqualsBuilder()
      .append(myHgFile, that.myHgFile)
      .append(myRevisionNumber, that.myRevisionNumber)
      .isEquals();
  }

}
