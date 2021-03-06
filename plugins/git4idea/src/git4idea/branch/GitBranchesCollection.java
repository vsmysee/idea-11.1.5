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
package git4idea.branch;

import git4idea.GitBranch;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * <p>
 *   Storage for local, remote and current branches.
 *   The reason of creating this special collection is that
 *   in the terms of performance, they are detected by {@link git4idea.repo.GitRepositoryReader} at once;
 *   and also usually both sets of branches are needed by components, but are treated differently,
 *   so it is more convenient to have them separated, but in a single container.
 * </p> 
 * 
 * @author Kirill Likhodedov
 */
public final class GitBranchesCollection {
  
  public static final GitBranchesCollection EMPTY = new GitBranchesCollection(Collections.<GitBranch>emptyList(), Collections.<GitBranch>emptyList());

  private final Collection<GitBranch> myLocalBranches;
  private final Collection<GitBranch> myRemoteBranches;

  public GitBranchesCollection(@NotNull Collection<GitBranch> localBranches, @NotNull Collection<GitBranch> remoteBranches) {
    myRemoteBranches = remoteBranches;
    myLocalBranches = localBranches;
  }

  /**
   * Copy constructor. Sets inside are also copied.
   */
  public GitBranchesCollection(@NotNull GitBranchesCollection branches) {
    this(branches.getLocalBranches(), branches.getRemoteBranches());
  }

  /**
   * @return the copy of local branches set.
   */
  @NotNull
  public Collection<GitBranch> getLocalBranches() {
    return new HashSet<GitBranch>(myLocalBranches);
  }

  /**
   * @return the copy of remote branches set.
   */
  @NotNull
  public Collection<GitBranch> getRemoteBranches() {
    return new HashSet<GitBranch>(myRemoteBranches);
  }

}
