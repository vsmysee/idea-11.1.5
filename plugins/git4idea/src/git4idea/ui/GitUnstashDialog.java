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
package git4idea.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.Consumer;
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitVcs;
import git4idea.actions.GitShowAllSubmittedFilesAction;
import git4idea.commands.*;
import git4idea.config.GitVersionSpecialty;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.stash.GitStashUtils;
import git4idea.util.GitUIUtil;
import git4idea.validators.GitBranchNameValidator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The unstash dialog
 */
public class GitUnstashDialog extends DialogWrapper {
  /**
   * Git root selector
   */
  private JComboBox myGitRootComboBox;
  /**
   * The current branch label
   */
  private JLabel myCurrentBranch;
  /**
   * The view stash button
   */
  private JButton myViewButton;
  /**
   * The drop stash button
   */
  private JButton myDropButton;
  /**
   * The clear stashes button
   */
  private JButton myClearButton;
  /**
   * The pop stash checkbox
   */
  private JCheckBox myPopStashCheckBox;
  /**
   * The branch text field
   */
  private JTextField myBranchTextField;
  /**
   * The root panel of the dialog
   */
  private JPanel myPanel;
  /**
   * The stash list
   */
  private JList myStashList;
  /**
   * If this checkbox is selected, the index is reinstated as well as working tree
   */
  private JCheckBox myReinstateIndexCheckBox;
  /**
   * Set of branches for the current root
   */
  private final HashSet<String> myBranches = new HashSet<String>();

  /**
   * The project
   */
  private final Project myProject;
  private GitVcs myVcs;
  private static final Logger LOG = Logger.getInstance(GitUnstashDialog.class);

  /**
   * A constructor
   *
   * @param project     the project
   * @param roots       the list of the roots
   * @param defaultRoot the default root to select
   */
  public GitUnstashDialog(final Project project, final List<VirtualFile> roots, final VirtualFile defaultRoot) {
    super(project, true);
    myProject = project;
    myVcs = GitVcs.getInstance(project);
    setTitle(GitBundle.getString("unstash.title"));
    setOKButtonText(GitBundle.getString("unstash.button.apply"));
    GitUIUtil.setupRootChooser(project, roots, defaultRoot, myGitRootComboBox, myCurrentBranch);
    myStashList.setModel(new DefaultListModel());
    refreshStashList();
    myGitRootComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        refreshStashList();
        updateDialogState();
      }
    });
    myStashList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateDialogState();
      }
    });
    myBranchTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateDialogState();
      }
    });
    myPopStashCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateDialogState();
      }
    });
    myClearButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (Messages.YES == Messages.showYesNoDialog(GitUnstashDialog.this.getContentPane(),
                                                     GitBundle.message("git.unstash.clear.confirmation.message"),
                                                     GitBundle.message("git.unstash.clear.confirmation.title"), Messages.getWarningIcon())) {
          GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitCommand.STASH);
          h.setNoSSH(true);
          h.addParameters("clear");
          GitHandlerUtil.doSynchronously(h, GitBundle.getString("unstash.clearing.stashes"), h.printableCommandLine());
          refreshStashList();
          updateDialogState();
        }
      }
    });
    myDropButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final StashInfo stash = getSelectedStash();
        if (Messages.YES == Messages.showYesNoDialog(GitUnstashDialog.this.getContentPane(),
                                                     GitBundle.message("git.unstash.drop.confirmation.message", stash.getStash(), stash.getMessage()),
                                                     GitBundle.message("git.unstash.drop.confirmation.title", stash.getStash()), Messages.getQuestionIcon())) {
          final ModalityState current = ModalityState.current();
          ProgressManager.getInstance().run(new Task.Modal(myProject, "Removing stash " + stash.getStash(), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              final GitSimpleHandler h = dropHandler(stash.getStash());
              try {
                h.run();
                h.unsilence();
              }
              catch (final VcsException ex) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  @Override
                  public void run() {
                    GitUIUtil.showOperationError(myProject, ex, h.printableCommandLine());
                  }
                }, current);
              }
            }
          });
          refreshStashList();
          updateDialogState();
        }
      }

      private GitSimpleHandler dropHandler(String stash) {
        GitSimpleHandler h = new GitSimpleHandler(myProject, getGitRoot(), GitCommand.STASH);
        h.setNoSSH(true);
        h.addParameters("drop");
        addStashParameter(h, stash);
        return h;
      }
    });
    myViewButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final VirtualFile root = getGitRoot();
        String resolvedStash;
        String selectedStash = getSelectedStash().getStash();
        try {
          GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.REV_LIST);
          h.setNoSSH(true);
          h.setSilent(true);
          h.addParameters("--timestamp", "--max-count=1");
          addStashParameter(h, selectedStash);
          h.endOptions();
          final String output = h.run();
          resolvedStash = GitRevisionNumber.parseRevlistOutputAsRevisionNumber(h, output).asString();
        }
        catch (VcsException ex) {
          GitUIUtil.showOperationError(myProject, ex, "resolving revision");
          return;
        }
        GitShowAllSubmittedFilesAction.showSubmittedFiles(myProject, resolvedStash, root);
      }
    });
    init();
    updateDialogState();
  }

  /**
   * Adds {@code stash@{x}} parameter to the handler, quotes it if needed.
   */
  private void addStashParameter(@NotNull GitHandler handler, @NotNull String stash) {
    if (GitVersionSpecialty.NEEDS_QUOTES_IN_STASH_NAME.existsIn(myVcs.getVersion())) {
      handler.addParameters("\"" + stash + "\"");
      handler.dontEscapeQuotes();
    }
    else {
      handler.addParameters(stash);
    }
  }

  /**
   * Update state dialog depending on the current state of the fields
   */
  private void updateDialogState() {
    String branch = myBranchTextField.getText();
    if (branch.length() != 0) {
      setOKButtonText(GitBundle.getString("unstash.button.branch"));
      myPopStashCheckBox.setEnabled(false);
      myPopStashCheckBox.setSelected(true);
      myReinstateIndexCheckBox.setEnabled(false);
      myReinstateIndexCheckBox.setSelected(true);
      if (!GitBranchNameValidator.INSTANCE.checkInput(branch)) {
        setErrorText(GitBundle.getString("unstash.error.invalid.branch.name"));
        setOKActionEnabled(false);
        return;
      }
      if (myBranches.contains(branch)) {
        setErrorText(GitBundle.getString("unstash.error.branch.exists"));
        setOKActionEnabled(false);
        return;
      }
    }
    else {
      if (!myPopStashCheckBox.isEnabled()) {
        myPopStashCheckBox.setSelected(false);
      }
      myPopStashCheckBox.setEnabled(true);
      setOKButtonText(
        myPopStashCheckBox.isSelected() ? GitBundle.getString("unstash.button.pop") : GitBundle.getString("unstash.button.apply"));
      if (!myReinstateIndexCheckBox.isEnabled()) {
        myReinstateIndexCheckBox.setSelected(false);
      }
      myReinstateIndexCheckBox.setEnabled(true);
    }
    if (myStashList.getModel().getSize() == 0) {
      myViewButton.setEnabled(false);
      myDropButton.setEnabled(false);
      myClearButton.setEnabled(false);
      setErrorText(null);
      setOKActionEnabled(false);
      return;
    }
    else {
      myClearButton.setEnabled(true);
    }
    if (myStashList.getSelectedIndex() == -1) {
      myViewButton.setEnabled(false);
      myDropButton.setEnabled(false);
      setErrorText(null);
      setOKActionEnabled(false);
      return;
    }
    else {
      myViewButton.setEnabled(true);
      myDropButton.setEnabled(true);
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  /**
   * Refresh stash list
   */
  private void refreshStashList() {
    final DefaultListModel listModel = (DefaultListModel)myStashList.getModel();
    listModel.clear();
    GitStashUtils.loadStashStack(myProject, getGitRoot(), new Consumer<StashInfo>() {
      @Override
      public void consume(StashInfo stashInfo) {
        listModel.addElement(stashInfo);
      }
    });
    myBranches.clear();
    try {
      GitBranch.listAsStrings(myProject, getGitRoot(), false, true, myBranches, null);
    }
    catch (VcsException e) {
      // ignore error
    }
    myStashList.setSelectedIndex(0);
  }

  /**
   * @return the selected git root
   */
  private VirtualFile getGitRoot() {
    return (VirtualFile)myGitRootComboBox.getSelectedItem();
  }

  /**
   * @return unstash handler
   */
  private GitLineHandler handler() {
    GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitCommand.STASH);
    h.setNoSSH(true);
    String branch = myBranchTextField.getText();
    if (branch.length() == 0) {
      h.addParameters(myPopStashCheckBox.isSelected() ? "pop" : "apply");
      if (myReinstateIndexCheckBox.isSelected()) {
        h.addParameters("--index");
      }
    }
    else {
      h.addParameters("branch", branch);
    }
    String selectedStash = getSelectedStash().getStash();
    addStashParameter(h, selectedStash);
    return h;
  }

  /**
   * @return selected stash
   * @throws NullPointerException if no stash is selected
   */
  private StashInfo getSelectedStash() {
    return (StashInfo)myStashList.getSelectedValue();
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
    return "reference.VersionControl.Git.Unstash";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myStashList;
  }

  /**
   * Show unstash dialog and process its result
   *
   * @param project       the context project
   * @param gitRoots      the git roots
   * @param defaultRoot   the default git root
   * @param affectedRoots the affected roots
   */
  public static void showUnstashDialog(Project project,
                                       List<VirtualFile> gitRoots,
                                       VirtualFile defaultRoot,
                                       Set<VirtualFile> affectedRoots) {
    GitUnstashDialog d = new GitUnstashDialog(project, gitRoots, defaultRoot);
    d.show();
    if (!d.isOK()) {
      return;
    }
    VirtualFile root = d.getGitRoot();
    affectedRoots.add(root);
    GitLineHandler h = d.handler();
    final AtomicBoolean conflict = new AtomicBoolean();

    h.addLineListener(new GitLineHandlerAdapter() {
      public void onLineAvailable(String line, Key outputType) {
        if (line.contains("Merge conflict")) {
          conflict.set(true);
        }
      }
    });
    int rc = GitHandlerUtil.doSynchronously(h, GitBundle.getString("unstash.unstashing"), h.printableCommandLine(), false);
    root.refresh(true, true);

    if (conflict.get()) {
      boolean conflictsResolved = new UnstashConflictResolver(project, root, d.getSelectedStash()).merge();
      LOG.info("loadRoot " + root + ", conflictsResolved: " + conflictsResolved);
    } else if (rc != 0) {
      GitUIUtil.showOperationErrors(project, h.errors(), h.printableCommandLine());
    }
  }

  private static class UnstashConflictResolver extends GitConflictResolver {

    private final VirtualFile myRoot;
    private final StashInfo myStashInfo;

    public UnstashConflictResolver(Project project, VirtualFile root, StashInfo stashInfo) {
      super(project, Collections.singleton(root), makeParams(stashInfo));
      myRoot = root;
      myStashInfo = stashInfo;
    }
    
    private static Params makeParams(StashInfo stashInfo) {
      Params params = new Params();
      params.setErrorNotificationTitle("Unstashed with conflicts");
      params.setMergeDialogCustomizer(new UnstashMergeDialogCustomizer(stashInfo));
      return params;
    }

    @Override
    protected void notifyUnresolvedRemain() {
      GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification("Conflicts were not resolved during unstash",
                                                "Unstash is not complete, you have unresolved merges in your working tree<br/>" +
                                                "<a href='resolve'>Resolve</a> conflicts.",
                                                NotificationType.WARNING, new NotificationListener() {
          @Override
          public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              if (event.getDescription().equals("resolve")) {
                new UnstashConflictResolver(myProject, myRoot, myStashInfo).mergeNoProceed();
              }
            }
          }
      }).notify(myProject);
    }
  }

  private static class UnstashMergeDialogCustomizer extends MergeDialogCustomizer {

    private final StashInfo myStashInfo;

    public UnstashMergeDialogCustomizer(StashInfo stashInfo) {
      myStashInfo = stashInfo;
    }

    @Override
    public String getMultipleFileMergeDescription(Collection<VirtualFile> files) {
      return "<html>Conflicts during unstashing <code>" + myStashInfo.getStash() + "\"" + myStashInfo.getMessage() + "\"</code></html>";
    }

    @Override
    public String getLeftPanelTitle(VirtualFile file) {
      return "Local changes";
    }

    @Override
    public String getRightPanelTitle(VirtualFile file, VcsRevisionNumber lastRevisionNumber) {
      return "Changes from stash";
    }
  }
}
