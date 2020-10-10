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
package git4idea.repo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.Processor;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitBranch;
import git4idea.branch.GitBranchesCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads information about the Git repository from Git service files located in the {@code .git} folder.
 * NB: works with {@link java.io.File}, i.e. reads from disk. Consider using caching.
 * Throws a {@link GitRepoStateException} in the case of incorrect Git file format.
 * @author Kirill Likhodedov
 */
class GitRepositoryReader {

  private static final Logger LOG = Logger.getInstance(GitRepositoryReader.class);

  private static Pattern BRANCH_PATTERN          = Pattern.compile("ref: refs/heads/(\\S+)"); // branch reference in .git/HEAD
  // this format shouldn't appear, but we don't want to fail because of a space
  private static Pattern BRANCH_WEAK_PATTERN     = Pattern.compile(" *(ref:)? */?refs/heads/(\\S+)");
  private static Pattern COMMIT_PATTERN          = Pattern.compile("[0-9a-fA-F]+"); // commit hash
  private static Pattern PACKED_REFS_BRANCH_LINE = Pattern.compile("([0-9a-fA-F]+) (\\S+)"); // branch reference in .git/packed-refs
  private static Pattern PACKED_REFS_TAGREF_LINE = Pattern.compile("\\^[0-9a-fA-F]+"); // tag reference in .git/packed-refs

  private static final String REFS_HEADS_PREFIX = "refs/heads/";
  private static final String REFS_REMOTES_PREFIX = "refs/remotes/";
  private static final int    IO_RETRIES        = 3; // number of retries before fail if an IOException happens during file read.

  private final File          myGitDir;         // .git/
  private final File          myHeadFile;       // .git/HEAD
  private final File          myRefsHeadsDir;   // .git/refs/heads/
  private final File          myRefsRemotesDir; // .git/refs/remotes/
  private final File          myPackedRefsFile; // .git/packed-refs

  GitRepositoryReader(@NotNull File gitDir) {
    myGitDir = gitDir;
    assertFileExists(myGitDir, ".git directory not found in " + gitDir);
    myHeadFile = new File(myGitDir, "HEAD");
    assertFileExists(myHeadFile, ".git/HEAD file not found in " + gitDir);
    myRefsHeadsDir = new File(new File(myGitDir, "refs"), "heads");
    myRefsRemotesDir = new File(new File(myGitDir, "refs"), "remotes");
    myPackedRefsFile = new File(myGitDir, "packed-refs");
  }

  @NotNull
  GitRepository.State readState() {
    if (isMergeInProgress()) {
      return GitRepository.State.MERGING;
    }
    if (isRebaseInProgress()) {
      return GitRepository.State.REBASING;
    }
    Head head = readHead();
    if (!head.isBranch) {
      return GitRepository.State.DETACHED;
    }
    return GitRepository.State.NORMAL;
  }

  /**
   * Finds current revision value.
   * @return The current revision hash, or <b>{@code null}</b> if current revision is unknown - it is the initial repository state.
   */
  @Nullable
  String readCurrentRevision() {
    final Head head = readHead();
    if (!head.isBranch) { // .git/HEAD is a commit
      return head.ref;
    }

    // look in /refs/heads/<branch name>
    File branchFile = null;
    for (Map.Entry<String, File> entry : readLocalBranches().entrySet()) {
      if (entry.getKey().equals(head.ref)) {
        branchFile = entry.getValue();
      }
    }
    if (branchFile != null) {
      return readBranchFile(branchFile);
    }

    // finally look in packed-refs
    return findBranchRevisionInPackedRefs(head.ref);
  }

  /**
   * If the repository is on branch, returns the current branch
   * If the repository is being rebased, returns the branch being rebased.
   * In other cases of the detached HEAD returns {@code null}.
   */
  @Nullable
  GitBranch readCurrentBranch() {
    Head head = readHead();
    if (head.isBranch) {
      String branchName = head.ref;
      String hash = readCurrentRevision();  // TODO make this faster, because we know the branch name
      return new GitBranch(branchName, hash == null ? "" : hash, true, false);
    }
    if (isRebaseInProgress()) {
      GitBranch branch = readRebaseBranch("rebase-apply");
      if (branch == null) {
        branch = readRebaseBranch("rebase-merge");
      }
      return branch;
    }
    return null;
  }

  /**
   * Reads {@code .git/rebase-apply/head-name} or {@code .git/rebase-merge/head-name} to find out the branch which is currently being rebased,
   * and returns the {@link GitBranch} for the branch name written there, or null if these files don't exist.
   */
  @Nullable
  private GitBranch readRebaseBranch(String rebaseDirName) {
    File rebaseDir = new File(myGitDir, rebaseDirName);
    if (!rebaseDir.exists()) {
      return null;
    }
    final File headName = new File(rebaseDir, "head-name");
    if (!headName.exists()) {
      return null;
    }
    String branchName = tryLoadFile(headName, calcEncoding(headName)).trim();
    if (branchName.startsWith(REFS_HEADS_PREFIX)) {
      branchName = branchName.substring(REFS_HEADS_PREFIX.length());
    }
    return new GitBranch(branchName, true, false);
  }
  
  private boolean isMergeInProgress() {
    File mergeHead = new File(myGitDir, "MERGE_HEAD");
    return mergeHead.exists();
  }

  private boolean isRebaseInProgress() {
    File f = new File(myGitDir, "rebase-apply");
    if (f.exists()) {
      return true;
    }
    f = new File(myGitDir, "rebase-merge");
    return f.exists();
  }

  /**
   * Reads the {@code .git/packed-refs} file and tries to find the revision hash for the given reference (branch actually).
   * @param ref short name of the reference to find. For example, {@code master}.
   * @return commit hash, or {@code null} if the given ref wasn't found in {@code packed-refs}
   */
  @Nullable
  private String findBranchRevisionInPackedRefs(final String ref) {
    if (!myPackedRefsFile.exists()) {
      return null;
    }

    return tryOrThrow(new Callable<String>() {
      @Override
      public String call() throws Exception {
        BufferedReader reader = null;
        try {
          reader = new BufferedReader(new FileReader(myPackedRefsFile));
          String line;
          while ((line = reader.readLine()) != null) {
            final AtomicReference<String> hashRef = new AtomicReference<String>();
            parsePackedRefsLine(line, new PackedRefsLineResultHandler() {
              @Override public void handleResult(String hash, String branchName) {
                if (hash == null || branchName == null) {
                  return;
                }
                if (branchName.endsWith(ref)) {
                  hashRef.set(hash);
                }
              }
            });
            
            if (hashRef.get() != null) {
              return hashRef.get();
            }
          }
          return null;
        }
        finally {
          if (reader != null) {
            reader.close();
          }
        }
      }
    }, myPackedRefsFile);
  }

  /**
   * @return the list of local branches in this Git repository.
   *         key is the branch name, value is the file.
   */
  private Map<String, File> readLocalBranches() {
    final Map<String, File> branches = new HashMap<String, File>();
    if (!myRefsHeadsDir.exists()) {
      return branches;
    }
    FileUtil.processFilesRecursively(myRefsHeadsDir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (!file.isDirectory()) {
          String relativePath = FileUtil.getRelativePath(myRefsHeadsDir, file);
          if (relativePath != null) {
            branches.put(FileUtil.toSystemIndependentName(relativePath), file);
          }
        }
        return true;
      }
    });
    return branches;
  }

  /**
   * @return all branches in this repository. local/remote/active information is stored in branch objects themselves. 
   */
  GitBranchesCollection readBranches() {
    Set<GitBranch> localBranches = readUnpackedLocalBranches();
    Set<GitBranch> remoteBranches = readUnpackedRemoteBranches();
    GitBranchesCollection packedBranches = readPackedBranches();
    localBranches.addAll(packedBranches.getLocalBranches());
    remoteBranches.addAll(packedBranches.getRemoteBranches());
    
    // note that even the active branch may be packed. So at first we collect branches, then we find the active.
    GitBranch currentBranch = readCurrentBranch();
    markActiveBranch(localBranches, currentBranch);

    return new GitBranchesCollection(localBranches, remoteBranches);
  }
  
  /**
   * Sets the 'active' flag to the current branch if it is contained in the specified collection.
   * @param branches      branches to be walked through.
   * @param currentBranch current branch.
   */
  private static void markActiveBranch(@NotNull Set<GitBranch> branches, @Nullable GitBranch currentBranch) {
    if (currentBranch == null) {
      return;
    }
    for (GitBranch branch : branches) {
      if (branch.getName().equals(currentBranch.getName())) {
        branch.setActive(true);
      }
    }
  }

  /**
   * @return list of branches from refs/heads. active branch is not marked as active - the caller should do this.
   */
  @NotNull
  private Set<GitBranch> readUnpackedLocalBranches() {
    Set<GitBranch> branches = new HashSet<GitBranch>();
    for (Map.Entry<String, File> entry : readLocalBranches().entrySet()) {
      String branchName = entry.getKey();
      File branchFile = entry.getValue();
      String hash = loadHashFromBranchFile(branchFile);
      branches.add(new GitBranch(branchName, hash == null ? "" : hash, false, false));
    }
    return branches;
  }
  
  @Nullable
  private static String loadHashFromBranchFile(@NotNull File branchFile) {
    try {
      return tryLoadFile(branchFile, null);
    }
    catch (GitRepoStateException e) {  // notify about error but don't break the process
      LOG.error("Couldn't read " + branchFile, e);
    }
    return null;
  }

  /**
   * @return list of branches from refs/remotes.
   */
  private Set<GitBranch> readUnpackedRemoteBranches() {
    final Set<GitBranch> branches = new HashSet<GitBranch>();
    if (!myRefsRemotesDir.exists()) {
      return branches;
    }
    FileUtil.processFilesRecursively(myRefsRemotesDir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (!file.isDirectory()) {
          final String relativePath = FileUtil.getRelativePath(myRefsRemotesDir, file);
          if (relativePath != null) {
            String branchName = FileUtil.toSystemIndependentName(relativePath);
            String hash = loadHashFromBranchFile(file);
            branches.add(new GitBranch(branchName, hash == null ? "": hash, false, true));
          }
        }
        return true;
      }
    });
    return branches;
  }

  /**
   * @return list of local and remote branches from packed-refs. Active branch is not marked as active.
   */
  @NotNull
  private GitBranchesCollection readPackedBranches() {
    final Set<GitBranch> localBranches = new HashSet<GitBranch>();
    final Set<GitBranch> remoteBranches = new HashSet<GitBranch>();
    if (!myPackedRefsFile.exists()) {
      return GitBranchesCollection.EMPTY;
    }
    final String content = tryLoadFile(myPackedRefsFile, calcEncoding(myPackedRefsFile));
    
    for (String line : content.split("\n")) {
      parsePackedRefsLine(line, new PackedRefsLineResultHandler() {
        @Override public void handleResult(@Nullable String hash, @Nullable String branchName) {
          if (hash == null || branchName == null) {
            return;
          }
          if (branchName.startsWith(REFS_HEADS_PREFIX)) {
            localBranches.add(new GitBranch(branchName.substring(REFS_HEADS_PREFIX.length()), hash, false, false));
          } else if (branchName.startsWith(REFS_REMOTES_PREFIX)) {
            remoteBranches.add(new GitBranch(branchName.substring(REFS_REMOTES_PREFIX.length()), hash, false, true));
          }
        }
      });
    }
    return new GitBranchesCollection(localBranches, remoteBranches);
  }

    
  private static String readBranchFile(File branchFile) {
    String rev = tryLoadFile(branchFile, null); // we expect just hash in branch file, no need to check encoding
    return rev.trim();
  }

  private static void assertFileExists(File file, String message) {
    if (!file.exists()) {
      throw new GitRepoStateException(message);
    }
  }

  private Head readHead() {
    String headContent = tryLoadFile(myHeadFile, calcEncoding(myHeadFile));
    headContent = headContent.trim(); // remove possible leading and trailing spaces to clearly match regexps

    Matcher matcher = BRANCH_PATTERN.matcher(headContent);
    if (matcher.matches()) {
      return new Head(true, matcher.group(1));
    }

    if (COMMIT_PATTERN.matcher(headContent).matches()) {
      return new Head(false, headContent);
    }
    matcher = BRANCH_WEAK_PATTERN.matcher(headContent);
    if (matcher.matches()) {
      LOG.info(".git/HEAD has not standard format: [" + headContent + "]. We've parsed branch [" + matcher.group(1) + "]");
      return new Head(true, matcher.group(1));
    }
    throw new GitRepoStateException("Invalid format of the .git/HEAD file: \n" + headContent);
  }

  /**
   * Loads the file content.
   * Tries 3 times, then a {@link GitRepoStateException} is thrown.
   * @param file      File to read.
   * @param encoding  Encoding of the file, or null for using "UTF-8". Encoding is important for non-latin branch names.
   * @return file content.
   */
  @NotNull
  private static String tryLoadFile(@NotNull final File file, @Nullable final Charset encoding) {
    return tryOrThrow(new Callable<String>() {
      @Override
      public String call() throws Exception {
        return FileUtil.loadFile(file, encoding == null ? "UTF-8" : encoding.name());
      }
    }, file);
  }

  /**
   * Tries to execute the given action.
   * If an IOException happens, tries again up to 3 times, and then throws a {@link GitRepoStateException}.
   * If an other exception happens, rethrows it as a {@link GitRepoStateException}.
   * In the case of success returns the result of the task execution.
   */
  private static String tryOrThrow(Callable<String> actionToTry, File fileToLoad) {
    IOException cause = null;
    for (int i = 0; i < IO_RETRIES; i++) {
      try {
        return actionToTry.call();
      } catch (IOException e) {
        LOG.info("IOException while loading " + fileToLoad, e);
        cause = e;
      } catch (Exception e) {    // this shouldn't happen since only IOExceptions are thrown in clients.
        throw new GitRepoStateException("Couldn't load file " + fileToLoad, e);
      }
    }
    throw new GitRepoStateException("Couldn't load file " + fileToLoad, cause);
  }

  /**
   * Parses a line from the .git/packed-refs file.
   * Passes the parsed hash-branch pair to the resultHandler.
   * Comments, tags and incorrectly formatted lines are ignored, and (null, null) is passed to the handler then.
   * Using a special handler may seem to be an overhead, but it is to avoid code duplication in two methods that parse packed-refs.
   */
  private static void parsePackedRefsLine(String line, PackedRefsLineResultHandler resultHandler) {
    line = line.trim();
    if (line.startsWith("#")) { // ignoring comments
      resultHandler.handleResult(null, null);
      return;
    }
    if (PACKED_REFS_TAGREF_LINE.matcher(line).matches()) { // ignoring the hash which an annotated tag above points to
      resultHandler.handleResult(null, null);
      return;
    }
    Matcher matcher = PACKED_REFS_BRANCH_LINE.matcher(line);
    if (matcher.matches()) {
      String hash = matcher.group(1);
      String branch = matcher.group(2);
      resultHandler.handleResult(hash, branch);
    } else {
      LOG.info("Ignoring invalid packed-refs line: [" + line + "]");
      resultHandler.handleResult(null, null);
      return;
    }
    resultHandler.handleResult(null, null);
  }

  private interface PackedRefsLineResultHandler {
    void handleResult(@Nullable String hash, @Nullable String branchName);
  }

  /**
   * @return File encoding or <code>null</code> if the encoding is unknown.
   */
  @Nullable
  private static Charset calcEncoding(File file) {
    VirtualFile vf = VcsUtil.getVirtualFile(file);
    return EncodingManager.getInstance().getEncoding(vf, false);
  }

  /**
   * Container to hold two information items: current .git/HEAD value and is Git on branch.
   */
  private static class Head {
    private final String ref;
    private final boolean isBranch;

    Head(boolean branch, String ref) {
      isBranch = branch;
      this.ref = ref;
    }
  }
  

}
