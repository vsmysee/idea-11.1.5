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
package git4idea.merge;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ArrayUtil;
import git4idea.GitDeprecatedRemote;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitSimpleHandler;
import git4idea.i18n.GitBundle;
import git4idea.jgit.GitHttpAdapter;
import git4idea.repo.GitRepository;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Git pull dialog
 */
public class GitPullDialog extends DialogWrapper {
  /**
   * root panel
   */
  private JPanel myPanel;
  /**
   * The selected git root
   */
  private JComboBox myGitRoot;
  /**
   * Current branch label
   */
  private JLabel myCurrentBranch;
  /**
   * The merge strategy
   */
  private JComboBox myStrategy;
  /**
   * No commit option
   */
  private JCheckBox myNoCommitCheckBox;
  /**
   * Squash commit option
   */
  private JCheckBox mySquashCommitCheckBox;
  /**
   * No fast forward option
   */
  private JCheckBox myNoFastForwardCheckBox;
  /**
   * Add log info to commit option
   */
  private JCheckBox myAddLogInformationCheckBox;
  /**
   * Selected remote option
   */
  private JComboBox myRemote;
  /**
   * Get branches button
   */
  private JButton myGetBranchesButton;
  /**
   * The branch chooser
   */
  private ElementsChooser<String> myBranchChooser;
  /**
   * The context project
   */
  private final Project myProject;

  /**
   * A constructor
   *
   * @param project     a project to select
   * @param roots       a git repository roots for the project
   * @param defaultRoot a guessed default root
   */
  public GitPullDialog(Project project, List<VirtualFile> roots, VirtualFile defaultRoot) {
    super(project, true);
    setTitle(GitBundle.getString("pull.title"));
    myProject = project;
    GitUIUtil.setupRootChooser(myProject, roots, defaultRoot, myGitRoot, myCurrentBranch);
    myGitRoot.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateRemotes();
      }
    });
    setOKButtonText(GitBundle.getString("pull.button"));
    updateRemotes();
    setupBranches();
    setupGetBranches();
    final ElementsChooser.ElementsMarkListener<String> listener = new ElementsChooser.ElementsMarkListener<String>() {
      public void elementMarkChanged(final String element, final boolean isMarked) {
        validateDialog();
      }
    };
    myBranchChooser.addElementsMarkListener(listener);
    listener.elementMarkChanged(null, true);
    GitUIUtil.imply(mySquashCommitCheckBox, true, myNoCommitCheckBox, true);
    GitUIUtil.imply(mySquashCommitCheckBox, true, myAddLogInformationCheckBox, false);
    GitUIUtil.exclusive(mySquashCommitCheckBox, true, myNoFastForwardCheckBox, true);
    GitMergeUtil.setupStrategies(myBranchChooser, myStrategy);
    init();
  }

  /**
   * Setup branch updating
   */
  private void setupBranches() {
    ((JTextField)myRemote.getEditor().getEditorComponent()).getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateBranches();
      }
    });
    updateBranches();
  }

  /**
   * Validate dialog and enable buttons
   */
  private void validateDialog() {
    if (getRemote().trim().length() == 0) {
      setOKActionEnabled(false);
      return;
    }
    setOKActionEnabled(myBranchChooser.getMarkedElements().size() != 0);
  }

  /**
   * Setup get branches button
   */
  private void setupGetBranches() {
    final JTextField textField = (JTextField)myRemote.getEditor().getEditorComponent();
    final DocumentAdapter listener = new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        validateDialog();
        myGetBranchesButton.setEnabled(textField.getText().trim().length() != 0);
      }
    };
    textField.getDocument().addDocumentListener(listener);
    listener.changedUpdate(null);
    myGetBranchesButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myBranchChooser.removeAllElements();
        Collection<String> remoteBranches = getRemoteBranches((GitDeprecatedRemote)myRemote.getSelectedItem());
        for (String branch : remoteBranches) {
          myBranchChooser.addElement(branch, false);
        }
      }
    });
  }

  @NotNull
  private Collection<String> getRemoteBranches(@NotNull GitDeprecatedRemote remote) {
    if (GitHttpAdapter.shouldUseJGit(remote.fetchUrl())) {
      GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(gitRoot());
      if (repository == null) {
        return Collections.emptyList();
      }
      return GitHttpAdapter.lsRemote(repository, remote.name(), remote.fetchUrl());
    }
    return lsRemoteNatively(remote);
  }

  @NotNull
  private Collection<String> lsRemoteNatively(@NotNull GitDeprecatedRemote remote) {
    GitSimpleHandler h = new GitSimpleHandler(myProject, gitRoot(), GitCommand.LS_REMOTE);
    h.addParameters("--heads", remote.toString());
    String output = GitHandlerUtil.doSynchronously(h, GitBundle.getString("pull.getting.remote.branches"), h.printableCommandLine());
    if (output == null) {
      return Collections.emptyList();
    }

    Collection<String> remoteBranches = new ArrayList<String>();
    for (String line : output.split("\n")) {
      if (line.length() == 0) {
        continue;
      }
      int pos = line.lastIndexOf('/');
      if (pos == -1) {
        pos = line.lastIndexOf('\t');
      }
      remoteBranches.add(line.substring(pos + 1));
    }
    return remoteBranches;
  }

  /**
   * @return a pull handler configured according to dialog options
   */
  public GitLineHandler pullOrMergeHandler(boolean pull) {
    GitLineHandler h = new GitLineHandler(myProject, gitRoot(), pull ? GitCommand.PULL : GitCommand.MERGE);
    // ignore merge failure for the pull
    h.ignoreErrorCode(1);
    h.addParameters("--no-stat");
    if (myNoCommitCheckBox.isSelected()) {
      h.addParameters("--no-commit");
    }
    else {
      if (myAddLogInformationCheckBox.isSelected()) {
        h.addParameters("--log");
      }
    }
    if (mySquashCommitCheckBox.isSelected()) {
      h.addParameters("--squash");
    }
    if (myNoFastForwardCheckBox.isSelected()) {
      h.addParameters("--no-ff");
    }
    String strategy = (String)myStrategy.getSelectedItem();
    if (!GitMergeUtil.DEFAULT_STRATEGY.equals(strategy)) {
      h.addParameters("--strategy", strategy);
    }
    h.addParameters("-v");
    if (pull) {
      h.addProgressParameter();
    }

    final List<String> markedBranches = myBranchChooser.getMarkedElements();
    if (pull) {
      h.addParameters(getRemote());
      h.addParameters(ArrayUtil.toStringArray(markedBranches));
    } else {
      for (String branch : markedBranches) {
        h.addParameters(getRemote() + "/" + branch);
      }
    }
    return h;
  }

  /**
   * Update branches
   */
  private void updateBranches() {
    try {
      String item = getRemote();
      myBranchChooser.removeAllElements();
      GitDeprecatedRemote r = null;
      final int count = myRemote.getItemCount();
      for (int i = 0; i < count; i++) {
        GitDeprecatedRemote candidate = (GitDeprecatedRemote)myRemote.getItemAt(i);
        if (candidate.name().equals(item)) {
          r = candidate;
          break;
        }
      }
      if (r == null) {
        return;
      }
      GitDeprecatedRemote.Info ri = r.localInfo(myProject, gitRoot());
      String toSelect = ri.getRemoteForLocal(currentBranch());
      for (String trackedBranch : ri.trackedBranches()) {
        myBranchChooser.addElement(trackedBranch, trackedBranch.equals(toSelect));
      }
    }
    catch (VcsException e) {
      GitVcs.getInstance(myProject).showErrors(Collections.singletonList(e), GitBundle.getString("pull.retrieving.remotes"));
    }
    finally {
      validateDialog();
    }
  }

  /**
   * @return current local branch for the git or null
   */
  @Nullable
  private String currentBranch() {
    String text = myCurrentBranch.getText();
    return text.equals(GitUIUtil.NO_CURRENT_BRANCH) ? null : text;
  }

  /**
   * Update remotes for the git root
   */
  private void updateRemotes() {
    GitUIUtil.setupRemotes(myProject, gitRoot(), currentBranch(), myRemote, true);
  }

  /**
   * @return a currently selected git root
   */
  public VirtualFile gitRoot() {
    return (VirtualFile)myGitRoot.getSelectedItem();
  }


  /**
   * Create branch chooser
   */
  private void createUIComponents() {
    myBranchChooser = new ElementsChooser<String>(true);
  }

  /**
   * {@inheritDoc}
   */
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.Pull";
  }

  /**
   * @return remote key
   */
  public String getRemote() {
    return ((JTextField)myRemote.getEditor().getEditorComponent()).getText();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myBranchChooser.getComponent();
  }
}
