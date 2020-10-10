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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.jetbrains.annotations.NotNull;

public final class HgChange {

  private HgFile beforeFile;
  private HgFile afterFile;
  private HgFileStatusEnum status;

  public HgChange(@NotNull HgFile hgFile, @NotNull HgFileStatusEnum status) {
    this.beforeFile = hgFile;
    this.afterFile = hgFile;
    this.status = status;
  }

  @NotNull
  public HgFile beforeFile() {
    return beforeFile;
  }

  @NotNull
  public HgFile afterFile() {
    return afterFile;
  }

  @NotNull
  public HgFileStatusEnum getStatus() {
    return status;
  }

  public void setBeforeFile(@NotNull HgFile beforeFile) {
    this.beforeFile = beforeFile;
  }

  public void setAfterFile(@NotNull HgFile afterFile) {
    this.afterFile = afterFile;
  }

  public void setStatus(@NotNull HgFileStatusEnum status) {
    this.status = status;
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    }
    if (!(object instanceof HgChange)) {
      return false;
    }
    HgChange that = (HgChange) object;
    return new EqualsBuilder()
      .append(beforeFile, that.beforeFile)
      .append(afterFile, that.afterFile)
      .append(status, that.status)
      .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
      .append(beforeFile)
      .append(afterFile)
      .append(status)
      .toHashCode();
  }

  @Override
  public String toString() {
    return String.format("HgChange#%s %s => %s", status, beforeFile, afterFile);
  }
}
