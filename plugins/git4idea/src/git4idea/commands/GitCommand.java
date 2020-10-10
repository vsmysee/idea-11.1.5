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
package git4idea.commands;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * The descriptor of git command. It contains policy information about myLocking and GUI thread policy.
 */
public class GitCommand {

  public static final GitCommand ADD = write("add");
  public static final GitCommand BLAME = read("blame");
  public static final GitCommand BRANCH = meta("branch");
  public static final GitCommand CHECKOUT = write("checkout");
  public static final GitCommand COMMIT = write("commit");
  public static final GitCommand CONFIG = meta("config");
  public static final GitCommand CHECK_ATTR = read("check-attr");
  public static final GitCommand CHERRY_PICK = write("cherry-pick");
  public static final GitCommand CLONE = write("clone");
  public static final GitCommand DESCRIBE = meta("describe");
  public static final GitCommand DIFF = read("diff");
  public static final GitCommand DIFF_INDEX = read("diff-index");
  public static final GitCommand FETCH = write("fetch");
  public static final GitCommand INIT = write("init");
  public static final GitCommand LOG = meta("log");
  public static final GitCommand LS_FILES = read("ls-files");
  public static final GitCommand LS_REMOTE = meta("ls-remote");
  public static final GitCommand MERGE = write("merge");
  public static final GitCommand MERGE_BASE = meta("merge-base");
  public static final GitCommand PULL = write("pull");
  public static final GitCommand PUSH = write("push");
  public static final GitCommand REBASE = writeSuspendable("rebase");
  public static final GitCommand REMOTE = meta("remote");
  public static final GitCommand RESET = write("reset");
  public static final GitCommand REV_LIST = meta("rev-list");
  public static final GitCommand RM = write("rm");
  public static final GitCommand SHOW = write("show");
  public static final GitCommand STASH = write("stash");
  public static final GitCommand STATUS = read("status");
  public static final GitCommand TAG = meta("tag");
  public static final GitCommand UPDATE_INDEX = write("update-index");
  public static final GitCommand VERSION = meta("version");
  public static final GitCommand GC = write("gc");

  /**
   * Name of environment variable that specifies editor for the git
   */
  public static final String GIT_EDITOR_ENV = "GIT_EDITOR";

  @NotNull @NonNls private final String myName; // command name passed to git
  @NotNull private final LockingPolicy myLocking; // Locking policy for the command
  @NotNull private final ThreadPolicy myThreading; // Thread policy for the command

  /**
   * The constructor
   *
   * @param name      the command myName
   * @param locking   the myLocking policy
   * @param threading the thread policy
   */
  private GitCommand(@NonNls @NotNull String name, @NotNull LockingPolicy locking, @NotNull ThreadPolicy threading) {
    this.myLocking = locking;
    this.myName = name;
    this.myThreading = threading;
  }

  /**
   * Create command descriptor that performs metadata operations only
   *
   * @param name the command myName
   * @return the created command object
   */
  private static GitCommand meta(String name) {
    return new GitCommand(name, LockingPolicy.META, ThreadPolicy.ANY);
  }

  /**
   * Create command descriptor that performs reads from index
   *
   * @param name the command myName
   * @return the create command objects
   */
  private static GitCommand read(String name) {
    return new GitCommand(name, LockingPolicy.READ, ThreadPolicy.BACKGROUND_ONLY);
  }

  /**
   * Create command descriptor that performs write operations
   *
   * @param name the command myName
   * @return the created command object
   */
  private static GitCommand write(String name) {
    return new GitCommand(name, LockingPolicy.WRITE, ThreadPolicy.BACKGROUND_ONLY);
  }

  /**
   * Create command descriptor that performs write operations
   *
   * @param name the command myName
   * @return the created command object
   */
  private static GitCommand writeSuspendable(String name) {
    return new GitCommand(name, LockingPolicy.WRITE_SUSPENDABLE, ThreadPolicy.BACKGROUND_ONLY);
  }

  /**
   * @return the command name
   */
  @NotNull
  public String name() {
    return myName;
  }

  /**
   * @return the locking policy for the command
   */
  @NotNull
  public LockingPolicy lockingPolicy() {
    return myLocking;
  }

  /**
   * @return the locking policy for the command
   */
  @NotNull
  public ThreadPolicy threadingPolicy() {
    return myThreading;
  }

  /**
   * The myLocking policy for the command
   */
  enum LockingPolicy {
    /**
     * Read lock should be acquired for the command
     */
    READ,
    /**
     * Write lock should be acquired for the command
     */
    WRITE,
    /**
     * Write lock should be acquired for the command, and it could be acquired in several intervals
     */
    WRITE_SUSPENDABLE,
    /**
     * Metadata read/write command
     */
    META,
  }

  /**
   * Thread policy for command
   */
  enum ThreadPolicy {
    /**
     * Any thread could be used
     */
    ANY,
    /**
     * Only background thread could be used
     */
    BACKGROUND_ONLY
  }
}
