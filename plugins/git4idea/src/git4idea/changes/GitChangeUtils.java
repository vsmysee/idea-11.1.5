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
package git4idea.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandler;
import git4idea.commands.GitSimpleHandler;
import git4idea.util.StringScanner;
import git4idea.history.browser.SHAHash;
import git4idea.history.wholeTree.AbstractHash;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Change related utilities
 */
public class GitChangeUtils {
  /**
   * the pattern for committed changelist assumed by {@link #parseChangeList(com.intellij.openapi.project.Project,com.intellij.openapi.vfs.VirtualFile, git4idea.util.StringScanner,boolean)}
   */
  public static final String COMMITTED_CHANGELIST_FORMAT = "%ct%n%H%n%P%n%an%x20%x3C%ae%x3E%n%cn%x20%x3C%ce%x3E%n%s%n%x03%n%b%n%x03";

  private static final Logger LOG = Logger.getInstance(GitChangeUtils.class);

  /**
   * A private constructor for utility class
   */
  private GitChangeUtils() {
  }

  /**
   * Parse changes from lines
   *
   * @param project the context project
   * @param root    the git root
   * @return a set of unmerged files
   * @throws VcsException if the input format does not matches expected format
   */
  public static List<VirtualFile> unmergedFiles(Project project, VirtualFile root) throws VcsException {
    HashSet<VirtualFile> unmerged = new HashSet<VirtualFile>();
    String rootPath = root.getPath();
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LS_FILES);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--unmerged");
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    for (StringScanner s = new StringScanner(h.run()); s.hasMoreData();) {
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      s.boundedToken('\t');
      final String relative = s.line();
      String path = rootPath + "/" + GitUtil.unescapePath(relative);
      VirtualFile file = lfs.refreshAndFindFileByPath(path);
      if (file != null) {
      // the file name is in the delete- or rename- conflict, so it is shown in the list of unmerged files,
      // but the file itself doesn't exist. In that case we just ignore the file.
        file.refresh(false, false);
        unmerged.add(file);
      }
    }
    if (unmerged.size() == 0) {
      return Collections.emptyList();
    }
    else {
      ArrayList<VirtualFile> rc = new ArrayList<VirtualFile>(unmerged.size());
      rc.addAll(unmerged);
      Collections.sort(rc, GitUtil.VIRTUAL_FILE_COMPARATOR);
      return rc;
    }
  }

  /**
   * Parse changes from lines
   *
   * @param project        the context project
   * @param vcsRoot        the git root
   * @param thisRevision   the current revision
   * @param parentRevision the parent revision for this change list
   * @param s              the lines to parse
   * @param changes        a list of changes to update
   * @param ignoreNames    a set of names ignored during collection of the changes
   * @throws VcsException if the input format does not matches expected format
   */
  public static void parseChanges(Project project,
                                  VirtualFile vcsRoot,
                                  @Nullable GitRevisionNumber thisRevision,
                                  GitRevisionNumber parentRevision,
                                  String s,
                                  Collection<Change> changes,
                                  final Set<String> ignoreNames) throws VcsException {
    StringScanner sc = new StringScanner(s);
    parseChanges(project, vcsRoot, thisRevision, parentRevision, sc, changes, ignoreNames);
    if (sc.hasMoreData()) {
      throw new IllegalStateException("Unknown file status: " + sc.line());
    }
  }

  public static Collection<String> parseDiffForPaths(final String rootPath, final StringScanner s) throws VcsException {
    final Collection<String> result = new ArrayList<String>();

    while (s.hasMoreData()) {
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      if ("CADUMR".indexOf(s.peek()) == -1) {
        // exit if there is no next character
        break;
      }
      assert 'M' != s.peek() : "Moves are not yet handled";
      String[] tokens = s.line().split("\t");
      String path = tokens[tokens.length - 1];
      path = rootPath + File.separator + GitUtil.unescapePath(path);
      path = FileUtil.toSystemDependentName(path);
      result.add(path);
    }
    return result;
  }

  /**
   * Parse changes from lines
   *
   * @param project        the context project
   * @param vcsRoot        the git root
   * @param thisRevision   the current revision
   * @param parentRevision the parent revision for this change list
   * @param s              the lines to parse
   * @param changes        a list of changes to update
   * @param ignoreNames    a set of names ignored during collection of the changes
   * @throws VcsException if the input format does not matches expected format
   */
  public static void parseChanges(Project project,
                                  VirtualFile vcsRoot,
                                  @Nullable GitRevisionNumber thisRevision,
                                  GitRevisionNumber parentRevision,
                                  StringScanner s,
                                  Collection<Change> changes,
                                  final Set<String> ignoreNames) throws VcsException {
    while (s.hasMoreData()) {
      FileStatus status = null;
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      if ("CADUMRT".indexOf(s.peek()) == -1) {
        // exit if there is no next character
        return;
      }
      String[] tokens = s.line().split("\t");
      final ContentRevision before;
      final ContentRevision after;
      final String path = tokens[tokens.length - 1];
      switch (tokens[0].charAt(0)) {
        case 'C':
        case 'A':
          before = null;
          status = FileStatus.ADDED;
          after = GitContentRevision.createRevision(vcsRoot, path, thisRevision, project, false, false, true);
          break;
        case 'U':
          status = FileStatus.MERGED_WITH_CONFLICTS;
        case 'M':
          if (status == null) {
            status = FileStatus.MODIFIED;
          }
          before = GitContentRevision.createRevision(vcsRoot, path, parentRevision, project, false, true, true);
          after = GitContentRevision.createRevision(vcsRoot, path, thisRevision, project, false, false, true);
          break;
        case 'D':
          status = FileStatus.DELETED;
          before = GitContentRevision.createRevision(vcsRoot, path, parentRevision, project, true, true, true);
          after = null;
          break;
        case 'R':
          status = FileStatus.MODIFIED;
          before = GitContentRevision.createRevision(vcsRoot, tokens[1], parentRevision, project, true, true, true);
          after = GitContentRevision.createRevision(vcsRoot, path, thisRevision, project, false, false, true);
          break;
        case 'T':
          status = FileStatus.MODIFIED;
          before = GitContentRevision.createRevision(vcsRoot, path, parentRevision, project, true, true, true);
          after = GitContentRevision.createRevisionForTypeChange(project, vcsRoot, path, thisRevision, true);
          break;
        default:
          throw new VcsException("Unknown file status: " + Arrays.asList(tokens));
      }
      if (ignoreNames == null || !ignoreNames.contains(path)) {
        changes.add(new Change(before, after, status));
      }
    }
  }

  /**
   * Load actual revision number with timestamp basing on revision number expression
   *
   * @param project        a project
   * @param vcsRoot        a repository root
   * @param revisionNumber a revision number expression
   * @return a resolved revision
   * @throws VcsException if there is a problem with running git
   */
  @NotNull
  public static GitRevisionNumber loadRevision(@NotNull Project project, @NotNull VirtualFile vcsRoot, @NonNls final String revisionNumber)
    throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, vcsRoot, GitCommand.REV_LIST);
    handler.addParameters("--timestamp", "--max-count=1", revisionNumber);
    handler.endOptions();
    handler.setNoSSH(true);
    handler.setSilent(true);
    String output = handler.run();
    StringTokenizer stk = new StringTokenizer(output, "\n\r \t", false);
    if (!stk.hasMoreTokens()) {
      GitSimpleHandler dh = new GitSimpleHandler(project, vcsRoot, GitCommand.LOG);
      dh.addParameters("-1", "HEAD");
      dh.setNoSSH(true);
      dh.setSilent(true);
      String out = dh.run();
      LOG.info("Diagnostic output from 'git log -1 HEAD': [" + out + "]");
      throw new VcsException(String.format("The string '%s' does not represent a revision number. Output: [%s]\n Root: %s",
                                           revisionNumber, output, vcsRoot));
    }
    Date timestamp = GitUtil.parseTimestampWithNFEReport(stk.nextToken(), handler, output);
    return new GitRevisionNumber(stk.nextToken(), timestamp);
  }

  /**
   * Check if the exception means that HEAD is missing for the current repository.
   *
   * @param e the exception to examine
   * @return true if the head is missing
   */
  public static boolean isHeadMissing(final VcsException e) {
    @NonNls final String errorText = "fatal: bad revision 'HEAD'\n";
    return e.getMessage().equals(errorText);
  }

  /**
   * Get list of changes. Because native Git non-linear revision tree structure is not
   * supported by the current IDEA interfaces some simplifications are made in the case
   * of the merge, so changes are reported as difference with the first revision
   * listed on the the merge that has at least some changes.
   *
   * @param project      the project file
   * @param root         the git root
   * @param revisionName the name of revision (might be tag)
   * @param skipDiffsForMerge
   * @return change list for the respective revision
   * @throws VcsException in case of problem with running git
   */
  public static GitCommittedChangeList getRevisionChanges(Project project, VirtualFile root, String revisionName, boolean skipDiffsForMerge) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.SHOW);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--name-status", "--no-abbrev", "-M", "--pretty=format:" + COMMITTED_CHANGELIST_FORMAT, "--encoding=UTF-8",
                    revisionName, "--");
    String output = h.run();
    StringScanner s = new StringScanner(output);
    return parseChangeList(project, root, s, skipDiffsForMerge, h);
  }

  @Nullable
  public static String getCommitAbbreviation(final Project project, final VirtualFile root, final SHAHash hash) {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--max-count=1", "--pretty=%h", "--encoding=UTF-8", "\"" + hash.getValue() + "\"", "--");
    try {
      final String output = h.run().trim();
      if (StringUtil.isEmptyOrSpaces(output)) return null;
      return output.trim();
    }
    catch (VcsException e) {
      return null;
    }
  }

  @Nullable
  public static SHAHash commitExists(final Project project, final VirtualFile root, final String anyReference,
                                     List<VirtualFile> paths, final String... parameters) {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters(parameters);
    h.addParameters("--max-count=1", "--pretty=%H", "--encoding=UTF-8", anyReference, "--");
    if (paths != null && ! paths.isEmpty()) {
      h.addRelativeFiles(paths);
    }
    try {
      final String output = h.run().trim();
      if (StringUtil.isEmptyOrSpaces(output)) return null;
      return new SHAHash(output);
    }
    catch (VcsException e) {
      return null;
    }
  }

  public static boolean isAnyLevelChild(final Project project, final VirtualFile root, final SHAHash parent,
                                        final String anyReferenceChild) {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.MERGE_BASE);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("\"" + parent.getValue() + "\"","\"" + anyReferenceChild + "\"",  "--");
    try {
      final String output = h.run().trim();
      if (StringUtil.isEmptyOrSpaces(output)) return false;
      return parent.getValue().equals(output.trim());
    }
    catch (VcsException e) {
      return false;
    }
  }

  @Nullable
  public static List<AbstractHash> commitExistsByComment(final Project project, final VirtualFile root, final String anyReference) {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    h.setNoSSH(true);
    h.setSilent(true);
    String escaped = StringUtil.escapeQuotes(anyReference);
    escaped = StringUtil.escapeSlashes(escaped);
    final String grepParam = "--grep=" + escaped;
    h.addParameters("--regexp-ignore-case", "--pretty=%h", "--all", "--encoding=UTF-8", grepParam, "--");
    try {
      final String output = h.run().trim();
      if (StringUtil.isEmptyOrSpaces(output)) return null;
      final String[] hashes = output.split("\n");
      final List<AbstractHash> result = new ArrayList<AbstractHash>();
      for (String hash : hashes) {
        result.add(AbstractHash.create(hash));
      }
      return result;
    }
    catch (VcsException e) {
      return null;
    }
  }

  /**
   * Parse changelist
   *
   * @param project the project
   * @param root    the git root
   * @param s       the scanner for log or show command output
   * @param skipDiffsForMerge
   * @param handler the handler that produced the output to parse. - for debugging purposes.
   * @return the parsed changelist
   * @throws VcsException if there is a problem with running git
   */
  public static GitCommittedChangeList parseChangeList(Project project, VirtualFile root, StringScanner s, boolean skipDiffsForMerge, GitHandler handler) throws VcsException {
    ArrayList<Change> changes = new ArrayList<Change>();
    // parse commit information
    final Date commitDate = GitUtil.parseTimestampWithNFEReport(s.line(), handler, s.getAllText());
    final String revisionNumber = s.line();
    final String parentsLine = s.line();
    final String[] parents = parentsLine.length() == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : parentsLine.split(" ");
    String authorName = s.line();
    String committerName = s.line();
    committerName = GitUtil.adjustAuthorName(authorName, committerName);
    String commentSubject = s.boundedToken('\u0003', true);
    s.nextLine();
    String commentBody = s.boundedToken('\u0003', true);
    // construct full comment
    String fullComment;
    if (commentSubject.length() == 0) {
      fullComment = commentBody;
    }
    else if (commentBody.length() == 0) {
      fullComment = commentSubject;
    }
    else {
      fullComment = commentSubject + "\n" + commentBody;
    }
    GitRevisionNumber thisRevision = new GitRevisionNumber(revisionNumber, commitDate);

    long number = longForSHAHash(revisionNumber);
    if (skipDiffsForMerge || (parents.length <= 1)) {
      final GitRevisionNumber parentRevision = parents.length > 0 ? loadRevision(project, root, parents[0]) : null;
      // This is the first or normal commit with the single parent.
      // Just parse changes in this commit as returned by the show command.
      parseChanges(project, root, thisRevision, parentRevision, s, changes, null);
    }
    else {
      // This is the merge commit. It has multiple parent commits.
      // Find the first commit with changes and report it as a change list.
      // If no changes are found (why to merge then?). Empty changelist is reported.

      for (String parent : parents) {
        final GitRevisionNumber parentRevision = loadRevision(project, root, parent);
        if (parentRevision == null) {
          // the repository was cloned with --depth parameter
          continue;
        }
        GitSimpleHandler diffHandler = new GitSimpleHandler(project, root, GitCommand.DIFF);
        diffHandler.setNoSSH(true);
        diffHandler.setSilent(true);
        diffHandler.addParameters("--name-status", "-M", parentRevision.getRev(), thisRevision.getRev());
        String diff = diffHandler.run();
        parseChanges(project, root, thisRevision, parentRevision, diff, changes, null);

        if (changes.size() > 0) {
          break;
        }
      }
    }
    return new GitCommittedChangeList(commentSubject + "(" + revisionNumber + ")", fullComment, committerName, number, commitDate,
                                       changes, revisionNumber);
  }

  public static long longForSHAHash(String revisionNumber) {
    return Long.parseLong(revisionNumber.substring(0, 15), 16) << 4 + Integer.parseInt(revisionNumber.substring(15, 16), 16);
  }

  @NotNull
  public static Collection<Change> getDiff(@NotNull Project project, @NotNull VirtualFile root,
                                           @Nullable String firstRevision, @NotNull String nextRevision,
                                           @Nullable Collection<FilePath> dirtyPaths) throws VcsException {
    Collection<Change> changes = new ArrayList<Change>();
    String range = firstRevision == null ? nextRevision : firstRevision + ".." + nextRevision;
    String output = getDiffOutput(project, root, range, dirtyPaths);
    GitRevisionNumber thisRevision = firstRevision == null ? null : loadRevision(project, root, firstRevision);
    parseChanges(project, root, thisRevision, loadRevision(project, root, nextRevision), output, changes, Collections.<String>emptySet());
    return changes;
  }

  /**
   * Calls {@code git diff} on the given range.
   * @param project
   * @param root
   * @param diffRange  range or just revision (will be compared with current working tree).
   * @param dirtyPaths limit the command by paths if needed or pass null.
   * @return output of the 'git diff' command.
   * @throws VcsException
   */
  @NotNull
  public static String getDiffOutput(@NotNull Project project, @NotNull VirtualFile root,
                                     @NotNull String diffRange, @Nullable Collection<FilePath> dirtyPaths) throws VcsException {
    GitSimpleHandler handler = getDiffHandler(project, root, diffRange, dirtyPaths);
    if (handler.isLargeCommandLine()) {
      // if there are too much files, just get all changes for the project
      handler = getDiffHandler(project, root, diffRange, null);
    }
    return handler.run();
  }

  @NotNull
  private static GitSimpleHandler getDiffHandler(@NotNull Project project, @NotNull VirtualFile root,
                                                 @NotNull String diffRange, @Nullable Collection<FilePath> dirtyPaths) {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.DIFF);
    handler.addParameters("--name-status", "--diff-filter=ADCMRUXT", "-M", diffRange);
    handler.setNoSSH(true);
    handler.setSilent(true);
    handler.setStdoutSuppressed(true);
    handler.endOptions();
    if (dirtyPaths != null) {
      handler.addRelativePaths(dirtyPaths);
    }
    return handler;
  }

}
