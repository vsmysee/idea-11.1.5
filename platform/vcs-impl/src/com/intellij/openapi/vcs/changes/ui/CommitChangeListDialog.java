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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.checkin.*;
import com.intellij.openapi.vcs.impl.CheckinHandlersManager;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SplitterWithSecondHideable;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.OnOffListener;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class CommitChangeListDialog extends DialogWrapper implements CheckinProjectPanel, TypeSafeDataProvider {
  private final static String outCommitHelpId = "reference.dialogs.vcs.commit";
  private final CommitContext myCommitContext;
  private final CommitMessage myCommitMessageArea;
  private Splitter mySplitter;
  private final JPanel myAdditionalOptionsPanel;

  private final ChangesBrowser myBrowser;
  private final ChangesBrowserExtender myBrowserExtender;

  private CommitLegendPanel myLegend;
  private final ShortDiffDetails myDiffDetails;

  private final List<RefreshableOnComponent> myAdditionalComponents = new ArrayList<RefreshableOnComponent>();
  private final List<CheckinHandler> myHandlers = new ArrayList<CheckinHandler>();
  private final String myActionName;
  private final Project myProject;
  private final List<CommitExecutor> myExecutors;
  private final Alarm myOKButtonUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private String myLastKnownComment = "";
  private final boolean myAllOfDefaultChangeListChangesIncluded;
  @NonNls private static final String SPLITTER_PROPORTION_OPTION = "CommitChangeListDialog.SPLITTER_PROPORTION_";
  private final Action[] myExecutorActions;
  private final boolean myShowVcsCommit;
  private final Map<AbstractVcs, JPanel> myPerVcsOptionsPanels = new HashMap<AbstractVcs, JPanel>();

  @Nullable
  private final AbstractVcs myVcs;
  private final boolean myIsAlien;
  private boolean myDisposed = false;
  private final JLabel myWarningLabel;

  private final Map<String, CheckinChangeListSpecificComponent> myCheckinChangeListSpecificComponents;

  private final Map<String, String> myListComments;
  private String myLastSelectedListName;
  private ChangeInfoCalculator myChangesInfoCalculator;

  private final PseudoMap<Object, Object> myAdditionalData;
  private String myHelpId;
  
  private SplitterWithSecondHideable myDetailsSplitter;
  private static final String DETAILS_SPLITTER_PROPORTION_OPTION = "CommitChangeListDialog.DETAILS_SPLITTER_PROPORTION_OPTION_";
  private static final String DETAILS_SHOW_OPTION = "CommitChangeListDialog.DETAILS_SHOW_OPTION_";
  private JPanel myDetailsPanel;
  private final FileAndDocumentListenersForShortDiff myListenersForShortDiff;
  private String myOkActionText;
  private final ZipperUpdater myZipperUpdater;
  private final Runnable myRefreshDetails;
  private CommitAction myCommitAction;

  private static class MyUpdateButtonsRunnable implements Runnable {
    private CommitChangeListDialog myDialog;

    private MyUpdateButtonsRunnable(final CommitChangeListDialog dialog) {
      myDialog = dialog;
    }

    public void cancel() {
      myDialog = null;
    }

    public void run() {
      if (myDialog != null) {
        myDialog.updateButtons();
        myDialog.updateLegend();
      }
    }

    public void restart(final CommitChangeListDialog dialog) {
      myDialog = dialog;
      run();
    }
  }

  private final MyUpdateButtonsRunnable myUpdateButtonsRunnable = new MyUpdateButtonsRunnable(this);

  private static boolean commit(final Project project, final List<Change> changes, final LocalChangeList initialSelection,
                             final List<CommitExecutor> executors, final boolean showVcsCommit, final String comment) {
    final AbstractVcs[] allActiveVcss = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    final List<VcsCheckinHandlerFactory> factoryList =
      CheckinHandlersManager.getInstance().getMatchingVcsFactories(Arrays.<AbstractVcs>asList(allActiveVcss));
    for (BaseCheckinHandlerFactory factory : factoryList) {
      final BeforeCheckinDialogHandler handler = factory.createSystemReadyHandler(project);
      if (handler != null && !handler.beforeCommitDialogShownCallback(Collections.unmodifiableList(executors), showVcsCommit)) {
        return false;
      }
    }

    final ChangeListManager manager = ChangeListManager.getInstance(project);
    final LocalChangeList defaultList = manager.getDefaultChangeList();
    final ArrayList<LocalChangeList> changeLists = new ArrayList<LocalChangeList>(manager.getChangeListsCopy());
    CommitChangeListDialog dialog =
      new CommitChangeListDialog(project, changes, initialSelection, executors, showVcsCommit, defaultList, changeLists, null, false,
                                 comment);
    dialog.show();
    return dialog.isOK();
  }

  public static void commitPaths(final Project project, Collection<FilePath> paths, final LocalChangeList initialSelection,
                                 @Nullable final CommitExecutor executor, final String comment) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);
    final Collection<Change> changes = new HashSet<Change>();
    for (FilePath path : paths) {
      changes.addAll(manager.getChangesIn(path));
    }

    commitChanges(project, changes, initialSelection, executor, comment);
  }

  public static boolean commitChanges(final Project project, final Collection<Change> changes, final LocalChangeList initialSelection,
                                   final CommitExecutor executor, final String comment) {
    if (executor == null) {
      return commitChanges(project, changes, initialSelection, collectExecutors(project, changes), true, comment);
    }
    else {
      return commitChanges(project, changes, initialSelection, Collections.singletonList(executor), false, comment);
    }
  }

  public static List<CommitExecutor> collectExecutors(Project project, Collection<Change> changes) {
    List<CommitExecutor> result = new ArrayList<CommitExecutor>();
    final ChangeListManager manager = ChangeListManager.getInstance(project);
    final List<AbstractVcs> vcses = getAffectedVcses(project, changes);
    for (AbstractVcs vcs : vcses) {
      result.addAll(vcs.getCommitExecutors());
    }
    result.addAll(manager.getRegisteredExecutors());
    return result;
  }

  public static boolean commitChanges(final Project project, final Collection<Change> changes, final LocalChangeList initialSelection,
                                   final List<CommitExecutor> executors, final boolean showVcsCommit, final String comment) {
    if (changes.isEmpty()) {
      Messages.showInfoMessage(project, VcsBundle.message("commit.dialog.no.changes.detected.text") ,
                                 VcsBundle.message("commit.dialog.no.changes.detected.title"));
      return false;
    }

    return commit(project, new ArrayList<Change>(changes), initialSelection, executors, showVcsCommit, comment);
  }

  public static void commitAlienChanges(final Project project, final List<Change> changes, final AbstractVcs vcs,
                                        final String changelistName, final String comment) {
    final LocalChangeList lcl = new AlienLocalChangeList(changes, changelistName);
    new CommitChangeListDialog(project, changes, null, null, true, AlienLocalChangeList.DEFAULT_ALIEN, Collections.singletonList(lcl), vcs,
                               true, comment).show();
  }

  private CommitChangeListDialog(final Project project,
                                 final List<Change> changes,
                                 final LocalChangeList initialSelection,
                                 final List<CommitExecutor> executors,
                                 final boolean showVcsCommit, final LocalChangeList defaultChangeList,
                                 final List<LocalChangeList> changeLists, final AbstractVcs singleVcs, final boolean isAlien,
                                 final String comment) {
    super(project, true);
    myCommitContext = new CommitContext();
    myProject = project;
    myExecutors = executors;
    myShowVcsCommit = showVcsCommit;
    myVcs = singleVcs;
    myListComments = new HashMap<String, String>();
    myAdditionalData = new PseudoMap<Object, Object>();
    myDiffDetails = new ShortDiffDetails(myProject, new Getter<Change[]>() {
      @Override
      public Change[] get() {
        final List<Change> selectedChanges = myBrowser.getViewer().getSelectedChanges();
        return selectedChanges.toArray(new Change[selectedChanges.size()]);
      }
    }, VcsChangeDetailsManager.getInstance(myProject));

    if (!myShowVcsCommit && ((myExecutors == null) || myExecutors.size() == 0)) {
      throw new IllegalArgumentException("nothing found to execute commit with");
    }

    myAllOfDefaultChangeListChangesIncluded = new HashSet<Change>(changes).containsAll(new HashSet<Change>(defaultChangeList.getChanges()));

    myIsAlien = isAlien;
    if (isAlien) {
      AlienChangeListBrowser browser = new AlienChangeListBrowser(project, changeLists, changes, initialSelection, true, true, singleVcs);
      myBrowser = browser;
      myBrowserExtender = browser;
    } else {
      MultipleChangeListBrowser browser = new MultipleChangeListBrowser(project, changeLists, changes, initialSelection, true, true,
                                                                        new Runnable() {
                                                                          public void run() {
                                                                            updateWarning();
                                                                          }
                                                                        },
        new Runnable() {
          public void run() {
            for (CheckinHandler handler : myHandlers) {
              handler.includedChangesChanged();
            }
          }
        }) {
        @Override
        protected void afterDiffRefresh() {
          myBrowser.rebuildList();
          myBrowser.setDataIsDirty(false);
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              IdeFocusManager.findInstance().requestFocus(myBrowser.getViewer().getPreferredFocusedComponent(), true);
            }
          });
        }
      };
      myBrowser = browser;
      myBrowser.setAlwayExpandList(false);
      myBrowserExtender = browser.getExtender();
    }
    myDiffDetails.setParent(myBrowser);
    myZipperUpdater = new ZipperUpdater(30, Alarm.ThreadToUse.SWING_THREAD, getDisposable());
    myRefreshDetails = new Runnable() {
      @Override
      public void run() {
        myDiffDetails.refresh();
      }
    };
    myListenersForShortDiff = new FileAndDocumentListenersForShortDiff(myDiffDetails) {
      @Override
      protected void updateDetails() {
        myZipperUpdater.queue(myRefreshDetails);
      }
      @Override
      protected boolean updateSynchronously() {
        return false;
      }
    };
    myListenersForShortDiff.on();

    myBrowser.getViewer().addSelectionListener(new Runnable() {
      @Override
      public void run() {
        myZipperUpdater.queue(myRefreshDetails);
      }
    });
    
    myBrowserExtender.addToolbarActions(this);

    myBrowserExtender.addSelectedListChangeListener(new SelectedListChangeListener() {
      public void selectedListChanged() {
        updateOnListSelection();
      }
    });
    myBrowser.setDiffExtendUIFactory(new ShowDiffAction.DiffExtendUIFactory() {
      public List<? extends AnAction> createActions(final Change change) {
        return myBrowser.createDiffActions(change);
      }

      @Nullable
      public JComponent createBottomComponent() {
        return new DiffCommitMessageEditor(CommitChangeListDialog.this);
      }
    });

    myCommitMessageArea = new CommitMessage(project);

    if (!VcsConfiguration.getInstance(project).CLEAR_INITIAL_COMMIT_MESSAGE) {
      setComment(project, initialSelection, comment);
    }

    myActionName = VcsBundle.message("commit.dialog.title");

    myAdditionalOptionsPanel = new JPanel();

    myAdditionalOptionsPanel.setLayout(new BorderLayout());
    Box optionsBox = Box.createVerticalBox();

    boolean hasVcsOptions = false;
    Box vcsCommitOptions = Box.createVerticalBox();
    final List<AbstractVcs> vcses = new ArrayList<AbstractVcs>(getAffectedVcses());
    Collections.sort(vcses, new Comparator<AbstractVcs>() {
      @Override
      public int compare(AbstractVcs o1, AbstractVcs o2) {
        return o1.getKeyInstanceMethod().getName().compareToIgnoreCase(o2.getKeyInstanceMethod().getName());
      }
    });
    myCheckinChangeListSpecificComponents = new HashMap<String, CheckinChangeListSpecificComponent>();
    for (AbstractVcs vcs : vcses) {
      final CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
      if (checkinEnvironment != null) {
        final RefreshableOnComponent options = checkinEnvironment.createAdditionalOptionsPanel(this, myAdditionalData);
        if (options != null) {
          JPanel vcsOptions = new JPanel(new BorderLayout());
          vcsOptions.add(options.getComponent(), BorderLayout.CENTER);
          vcsOptions.setBorder(IdeBorderFactory.createTitledBorder(vcs.getDisplayName(), true));
          vcsCommitOptions.add(vcsOptions);
          myPerVcsOptionsPanels.put(vcs, vcsOptions);
          myAdditionalComponents.add(options);
          if (options instanceof CheckinChangeListSpecificComponent) {
            myCheckinChangeListSpecificComponents.put(vcs.getName(), (CheckinChangeListSpecificComponent) options);
          }
          hasVcsOptions = true;
        }
      }
    }

    if (hasVcsOptions) {
      vcsCommitOptions.add(Box.createVerticalGlue());
      optionsBox.add(vcsCommitOptions);
    }

    boolean beforeVisible = false;
    boolean afterVisible = false;
    Box beforeBox = Box.createVerticalBox();
    Box afterBox = Box.createVerticalBox();
    final List<BaseCheckinHandlerFactory> handlerFactories = CheckinHandlersManager.getInstance().getRegisteredCheckinHandlerFactories(
      ProjectLevelVcsManager.getInstance(project).getAllActiveVcss());
    for (BaseCheckinHandlerFactory factory : handlerFactories) {
      final CheckinHandler handler = factory.createHandler(this, myCommitContext);
      if (CheckinHandler.DUMMY.equals(handler)) continue;

      myHandlers.add(handler);
      final RefreshableOnComponent beforePanel = handler.getBeforeCheckinConfigurationPanel();
      if (beforePanel != null) {
        beforeBox.add(beforePanel.getComponent());
        beforeVisible = true;
        myAdditionalComponents.add(beforePanel);
      }

      final RefreshableOnComponent afterPanel = handler.getAfterCheckinConfigurationPanel(getDisposable());
      if (afterPanel != null) {
        afterBox.add(afterPanel.getComponent());
        afterVisible = true;
        myAdditionalComponents.add(afterPanel);
      }
    }

    final String actionName = getCommitActionName();
    final String borderTitleName = actionName.replace("_", "").replace("&", "");
    if (beforeVisible) {
      beforeBox.add(Box.createVerticalGlue());
      JPanel beforePanel = new JPanel(new BorderLayout());
      beforePanel.add(beforeBox);
      beforePanel.setBorder(IdeBorderFactory.createTitledBorder(
        VcsBundle.message("border.standard.checkin.options.group", borderTitleName), true));
      optionsBox.add(beforePanel);
    }

    if (afterVisible) {
      afterBox.add(Box.createVerticalGlue());
      JPanel afterPanel = new JPanel(new BorderLayout());
      afterPanel.add(afterBox);
      afterPanel.setBorder(IdeBorderFactory.createTitledBorder(
        VcsBundle.message("border.standard.after.checkin.options.group", borderTitleName), true));
      optionsBox.add(afterPanel);
    }

    if (hasVcsOptions || beforeVisible || afterVisible) {
      optionsBox.add(Box.createVerticalGlue());
      myAdditionalOptionsPanel.add(optionsBox, BorderLayout.NORTH);
    }

    myOkActionText = actionName;

    if (myShowVcsCommit) {
      setTitle(myActionName);
    }
    else {
      setTitle(trimEllipsis(myExecutors.get(0).getActionText()));
    }

    restoreState();

    if (myExecutors != null) {
      myExecutorActions = new Action[myExecutors.size()];

      for (int i = 0; i < myExecutors.size(); i++) {
        final CommitExecutor commitExecutor = myExecutors.get(i);
        myExecutorActions[i] = new CommitExecutorAction(commitExecutor, i == 0 && !myShowVcsCommit);
      }
    } else {
      myExecutorActions = null;
    }

    myWarningLabel = new JLabel();
    myWarningLabel.setUI(new MultiLineLabelUI());
    myWarningLabel.setForeground(Color.red);

    updateWarning();

    init();
    updateButtons();
    updateVcsOptionsVisibility();
    
    updateOnListSelection();
    myCommitMessageArea.requestFocusInMessage();
    
    for (EditChangelistSupport support : Extensions.getExtensions(EditChangelistSupport.EP_NAME, project)) {
      support.installSearch(myCommitMessageArea.getEditorField(), myCommitMessageArea.getEditorField());
    }

    showDetailsIfSaved();
  }

  private void setComment(Project project, LocalChangeList initialSelection, String comment) {
    if (comment != null) {
      setCommitMessage(comment);
      myLastKnownComment = comment;
      myLastSelectedListName = initialSelection == null ? myBrowser.getSelectedChangeList().getName() : initialSelection.getName();
    } else {
      updateComment();

      if (StringUtil.isEmptyOrSpaces(myCommitMessageArea.getComment())) {
        setCommitMessage(VcsConfiguration.getInstance(project).LAST_COMMIT_MESSAGE);
        final String messageFromVcs = getInitialMessageFromVcs();
        if (messageFromVcs != null) {
          myCommitMessageArea.setText(messageFromVcs);
        }
      }
    }
  }

  private void showDetailsIfSaved() {
    String value = PropertiesComponent.getInstance().getValue(DETAILS_SHOW_OPTION);
    if (value != null) {
      Boolean asBoolean = Boolean.valueOf(value);
      if (Boolean.TRUE.equals(asBoolean)) {
        myDetailsSplitter.initOn();
      }
    }
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        myZipperUpdater.queue(myRefreshDetails);
      }
    });
  }

  private void updateOnListSelection() {
    updateComment();
    updateVcsOptionsVisibility();
    for (CheckinChangeListSpecificComponent component : myCheckinChangeListSpecificComponents.values()) {
      component.onChangeListSelected((LocalChangeList) myBrowser.getSelectedChangeList());
    }
  }

  private void updateWarning() {
    // check for null since can be called from constructor before field initialization
    if (myWarningLabel != null) {
      myWarningLabel.setVisible(false);
      final VcsException updateException = ((ChangeListManagerImpl)ChangeListManager.getInstance(myProject)).getUpdateException();
      if (updateException != null) {
        final String[] messages = updateException.getMessages();
        if (messages != null || messages.length > 0) {
          final String message = messages[0];
          myWarningLabel.setText("Warning: not all local changes may be shown due to an error: " + message);
          myWarningLabel.setVisible(true);
        }
      }
    }
  }

  private void updateVcsOptionsVisibility() {
    final List<AbstractVcs> affectedVcses = getAffectedVcses(myProject, myBrowser.getSelectedChangeList().getChanges());
    for(Map.Entry<AbstractVcs, JPanel> entry: myPerVcsOptionsPanels.entrySet()) {
      entry.getValue().setVisible(affectedVcses.contains(entry.getKey()));
    }
  }

  @Override
  protected String getHelpId() {
    return myHelpId;
  }

  private class CommitAction extends AbstractAction implements OptionAction {

    private Action[] myOptions = new Action[0];

    private CommitAction() {
      super(myOkActionText);
      putValue(DEFAULT_ACTION, Boolean.TRUE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      doOKAction();
    }

    @NotNull
    @Override
    public Action[] getOptions() {
      return myOptions;
    }

    public void setOptions(Action[] actions) {
      myOptions = actions;
    }
  }

  protected void doOKAction() {
    if (!saveDialogState()) return;
    saveComments(true);
    final DefaultListCleaner defaultListCleaner = new DefaultListCleaner();

    final Runnable callCommit = new Runnable() {
      public void run() {
        try {
          runBeforeCommitHandlers(new Runnable() {
              public void run() {
                CommitChangeListDialog.super.doOKAction();
                doCommit();
              }
            }, null);

          defaultListCleaner.clean();
        }
        catch (InputException ex) {
          ex.show();
        }
      }
    };
    if (myBrowser.isDataIsDirty()) {
      ensureDataIsActual(callCommit);
    } else {
      callCommit.run();
    }
  }

  @Override
  protected Action getOKAction() {
    return new CommitAction();
  }

  protected Action[] createActions() {
    final List<Action> actions = new ArrayList<Action>();

    myCommitAction = null;
    if (myShowVcsCommit) {
      myCommitAction = new CommitAction();
      actions.add(myCommitAction);
      myHelpId = outCommitHelpId;
    }
    if (myExecutors != null) {
      if (myCommitAction != null) {
        myCommitAction.setOptions(myExecutorActions);
      } else {
        actions.addAll(Arrays.asList(myExecutorActions));
      }
      for (CommitExecutor executor : myExecutors) {
        if (myHelpId != null) break;
        if (executor instanceof CommitExecutorWithHelp) {
          myHelpId = ((CommitExecutorWithHelp) executor).getHelpId();
        }
      }
    }
    actions.add(getCancelAction());
    if (myHelpId != null) {
      actions.add(getHelpAction());
    }

    return actions.toArray(new Action[actions.size()]);
  }

  private void execute(final CommitExecutor commitExecutor) {
    if (!saveDialogState()) return;
    saveComments(true);
    final CommitSession session = commitExecutor.createCommitSession();
    if (session instanceof CommitSessionContextAware) {
      ((CommitSessionContextAware)session).setContext(myCommitContext);
    }
    if (session == CommitSession.VCS_COMMIT) {
      doOKAction();
      return;
    }
    boolean isOK = true;
    if (SessionDialog.createConfigurationUI(session, getIncludedChanges(), getCommitMessage())!= null) {
      DialogWrapper sessionDialog = new SessionDialog(commitExecutor.getActionText(),
                                                      getProject(),
                                                      session,
                                                      getIncludedChanges(),
                                                      getCommitMessage());
      sessionDialog.show();
      isOK = sessionDialog.isOK();
    }
    if (isOK) {
      final DefaultListCleaner defaultListCleaner = new DefaultListCleaner();
      runBeforeCommitHandlers(new Runnable() {
        public void run() {
          try {
            final boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
              public void run() {
                session.execute(getIncludedChanges(), getCommitMessage());
              }
            }, commitExecutor.getActionText(), true, getProject());

            if (completed) {
              for (CheckinHandler handler : myHandlers) {
                handler.checkinSuccessful();
              }

              defaultListCleaner.clean();
              close(OK_EXIT_CODE);
            }
            else {
              session.executionCanceled();
            }
          }
          catch (Throwable e) {
            Messages.showErrorDialog(VcsBundle.message("error.executing.commit", commitExecutor.getActionText(), e.getLocalizedMessage()),
                                     commitExecutor.getActionText());

            for (CheckinHandler handler : myHandlers) {
              handler.checkinFailed(Arrays.asList(new VcsException(e)));
            }
          }
        }
      }, commitExecutor);


    }
    else {
      session.executionCanceled();
    }
  }

  @Nullable
  private String getInitialMessageFromVcs() {
    final List<Change> list = getIncludedChanges();
    final Ref<String> result = new Ref<String>();
    ChangesUtil.processChangesByVcs(myProject, list, new ChangesUtil.PerVcsProcessor<Change>() {
      public void process(final AbstractVcs vcs, final List<Change> items) {
        if (result.isNull()) {
          CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
          if (checkinEnvironment != null) {
            final Collection<FilePath> paths = ChangesUtil.getPaths(items);
            String defaultMessage = checkinEnvironment.getDefaultMessageFor(paths.toArray(new FilePath[paths.size()]));
            if (defaultMessage != null) {
              result.set(defaultMessage);
            }
          }
        }
      }
    });
    return result.get();
  }

  private void saveCommentIntoChangeList() {
    if (myLastSelectedListName != null) {
      final String actualCommentText = myCommitMessageArea.getComment();
      final String saved = myListComments.get(myLastSelectedListName);
      if (! Comparing.equal(saved, actualCommentText)) {
        myListComments.put(myLastSelectedListName, actualCommentText);
      }
    }
  }

  private boolean isDefaultList(final LocalChangeList list) {
    return VcsBundle.message("changes.default.changlist.name").equals(list.getName());
  }

  private void updateComment() {
    if (VcsConfiguration.getInstance(getProject()).CLEAR_INITIAL_COMMIT_MESSAGE) return;
    final LocalChangeList list = (LocalChangeList) myBrowser.getSelectedChangeList();
    if (list == null || (list.getName().equals(myLastSelectedListName))) {
      return;
    } else if (myLastSelectedListName != null) {
      saveCommentIntoChangeList();
    }
    myLastSelectedListName = list.getName();

    String listComment = list.getComment();
    if (StringUtil.isEmptyOrSpaces(listComment)) {
      final String listTitle = list.getName();
      if (! isDefaultList(list)) {
        listComment = listTitle;
      }
      else {
        // use last know comment; it is already stored in list
        listComment = myLastKnownComment;
      }
    }

    myCommitMessageArea.setText(listComment);
  }


  @Override
  public void dispose() {
    myDisposed = true;
    myBrowser.dispose();
    Disposer.dispose(myCommitMessageArea);
    Disposer.dispose(myOKButtonUpdateAlarm);
    myUpdateButtonsRunnable.cancel();
    myListenersForShortDiff.off();
    super.dispose();
    Disposer.dispose(myDiffDetails);
    PropertiesComponent.getInstance().setValue(SPLITTER_PROPORTION_OPTION, String.valueOf(mySplitter.getProportion()));
    float usedProportion = myDetailsSplitter.getUsedProportion();
    if (usedProportion > 0) {
      PropertiesComponent.getInstance().setValue(DETAILS_SPLITTER_PROPORTION_OPTION, String.valueOf(usedProportion));
    }
    PropertiesComponent.getInstance().setValue(DETAILS_SHOW_OPTION, String.valueOf(myDetailsSplitter.isOn()));
  }

  public String getCommitActionName() {
    String name = null;
    for (AbstractVcs vcs : getAffectedVcses()) {
      final CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
      if (name == null && checkinEnvironment != null) {
        name = checkinEnvironment.getCheckinOperationName();
      }
      else {
        name = VcsBundle.message("commit.dialog.default.commit.operation.name");
      }
    }
    return name != null ? name : VcsBundle.message("commit.dialog.default.commit.operation.name");
  }

  @Override
  public boolean isCheckCommitMessageSpelling() {
    VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);
    return configuration == null || configuration.CHECK_COMMIT_MESSAGE_SPELLING;
  }

  @Override
  public void setCheckCommitMessageSpelling(boolean checkSpelling) {
    VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);
    if (configuration != null) {
      configuration.CHECK_COMMIT_MESSAGE_SPELLING = checkSpelling;
    }
    myCommitMessageArea.setCheckSpelling(checkSpelling); 
  }

  private boolean checkComment() {
    if (VcsConfiguration.getInstance(myProject).FORCE_NON_EMPTY_COMMENT && (getCommitMessage().length() == 0)) {
      int requestForCheckin = Messages.showYesNoDialog(VcsBundle.message("confirmation.text.check.in.with.empty.comment"),
                                                       VcsBundle.message("confirmation.title.check.in.with.empty.comment"),
                                                       Messages.getWarningIcon());
      return requestForCheckin == OK_EXIT_CODE;
    }
    else {
      return true;
    }
  }

  private void stopUpdate() {
    myDisposed = true;
    myUpdateButtonsRunnable.cancel();
  }

  private void restartUpdate() {
    myDisposed = false;
    myUpdateButtonsRunnable.restart(this);
  }
  
  private void runBeforeCommitHandlers(final Runnable okAction, final CommitExecutor executor) {
    Runnable proceedRunnable = new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();

        for (CheckinHandler handler : myHandlers) {
          if (!(handler.acceptExecutor(executor))) continue;
          final CheckinHandler.ReturnResult result = handler.beforeCheckin(executor, myAdditionalData);
          if (result == CheckinHandler.ReturnResult.COMMIT) continue;
          if (result == CheckinHandler.ReturnResult.CANCEL) {
            restartUpdate();
            return;
          }

          if (result == CheckinHandler.ReturnResult.CLOSE_WINDOW) {
            final ChangeList changeList = myBrowser.getSelectedChangeList();
            CommitHelper.moveToFailedList(changeList,
                             getCommitMessage(),
                             getIncludedChanges(),
                             VcsBundle.message("commit.dialog.rejected.commit.template", changeList.getName()),
                             myProject);
            doCancelAction();
            return;
          }
        }

        okAction.run();
      }
    };

    stopUpdate();
    Runnable runnable = proceedRunnable;
    for(final CheckinHandler handler: myHandlers) {
      if (handler instanceof CheckinMetaHandler) {
        final Runnable previousRunnable = runnable;
        runnable = new Runnable() {
          @Override
          public void run() {
            ((CheckinMetaHandler)handler).runCheckinHandlers(previousRunnable);
          }
        };
      }
    }
    runnable.run();
  }

  private boolean saveDialogState() {
    if (!checkComment()) {
      return false;
    }

    saveCommentIntoChangeList();
    VcsConfiguration.getInstance(myProject).saveCommitMessage(getCommitMessage());
    try {
      saveState();
    }
    catch(InputException ex) {
      ex.show();
      return false;
    }
    return true;
  }

  private class DefaultListCleaner {
    private final boolean myToClean;

    private DefaultListCleaner() {
      final int selectedSize = getIncludedChanges().size();
      final ChangeList selectedList = myBrowser.getSelectedChangeList();
      final int totalSize = selectedList.getChanges().size();
      myToClean = (totalSize == selectedSize) && (isDefaultList((LocalChangeList) selectedList));
    }

    void clean() {
      if (myToClean) {
        final ChangeListManager clManager = ChangeListManager.getInstance(myProject);
        clManager.editComment(myLastSelectedListName, "");
      }
    }
  }

  private void saveComments(final boolean isOk) {
    final ChangeListManager clManager = ChangeListManager.getInstance(myProject);
    if (isOk) {
      final int selectedSize = getIncludedChanges().size();
      final ChangeList selectedList = myBrowser.getSelectedChangeList();
      final int totalSize = selectedList.getChanges().size();
      if (totalSize > selectedSize) {
        myListComments.remove(myLastSelectedListName);
      }
    }
    for (Map.Entry<String, String> entry : myListComments.entrySet()) {
      final String name = entry.getKey();
      final String value = entry.getValue();
      clManager.editComment(name, value);
    }
  }

  @Override
  public void doCancelAction() {
    for (CheckinChangeListSpecificComponent component : myCheckinChangeListSpecificComponents.values()) {
      component.saveState();
    }
    saveCommentIntoChangeList();
    saveComments(false);
    //VcsConfiguration.getInstance(myProject).saveCommitMessage(getCommitMessage());
    super.doCancelAction();
  }

  private void doCommit() {
    final CommitHelper helper = new CommitHelper(
      myProject,
      myBrowser.getSelectedChangeList(),
      getIncludedChanges(),
      myActionName,
      getCommitMessage(),
      myHandlers,
      myAllOfDefaultChangeListChangesIncluded, false, myAdditionalData);

    if (myIsAlien) {
      helper.doAlienCommit(myVcs);
    } else {
      helper.doCommit();
    }
  }

  @Nullable
  protected JComponent createCenterPanel() {
    JPanel rootPane = new JPanel(new BorderLayout());

    mySplitter = new Splitter(true);
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setFirstComponent(myBrowser);
    mySplitter.setSecondComponent(myCommitMessageArea);
    mySplitter.setDividerWidth(3);
    initMainSplitter();

    rootPane.add(mySplitter, BorderLayout.CENTER);

    JComponent browserHeader = myBrowser.getHeaderPanel();
    myBrowser.remove(browserHeader);
    rootPane.add(browserHeader, BorderLayout.NORTH);

    JPanel infoPanel = new JPanel(new BorderLayout());
    myChangesInfoCalculator = new ChangeInfoCalculator();
    myLegend = new CommitLegendPanel(myChangesInfoCalculator);
    infoPanel.add(myLegend.getComponent(), BorderLayout.NORTH);
    infoPanel.add(myAdditionalOptionsPanel, BorderLayout.CENTER);
    rootPane.add(infoPanel, BorderLayout.EAST);
    infoPanel.setBorder(IdeBorderFactory.createEmptyBorder(0, 10, 0, 0));

    rootPane.add(myWarningLabel, BorderLayout.SOUTH);

    final JPanel wrapper = new JPanel(new GridBagLayout());
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                                   new Insets(0, 0, 0, 0), 0, 0);
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(wrapper, BorderLayout.WEST);
    rootPane.add(panel, BorderLayout.SOUTH);

    myWarningLabel.setBorder(BorderFactory.createEmptyBorder(5,5,0,5));
    wrapper.add(myWarningLabel, gb);

    myDetailsSplitter = new SplitterWithSecondHideable(true, "Details", rootPane,
                                                      new OnOffListener<Integer>() {
                                                        @Override
                                                        public void on(Integer integer) {
                                                          if (integer == 0) return;
                                                          final Dimension dialogSize = getSize();
                                                          setSize(dialogSize.width, dialogSize.height + integer);
                                                          repaint();
                                                        }

                                                        @Override
                                                        public void off(Integer integer) {
                                                          if (integer == 0) return;
                                                          final Dimension dialogSize = getSize();
                                                          setSize(dialogSize.width, dialogSize.height - integer);
                                                          repaint();
                                                        }
                                                      }, true) {
      @Override
      protected RefreshablePanel createDetails() {
        initDetails();
        return myDiffDetails;
      }

      @Override
      protected float getSplitterInitialProportion() {
        float value = 0;
        final String remembered = PropertiesComponent.getInstance().getValue(DETAILS_SPLITTER_PROPORTION_OPTION);
        if (remembered != null) {
          try {
            value = Float.valueOf(remembered);
          } catch (NumberFormatException e) {
            //
          }
        }
        if (value <= 0.05 || value >= 0.95) {
          return 0.6f;
        }
        return value;
      }
    };

    return myDetailsSplitter.getComponent();
  }

  private void initMainSplitter() {
    final String s = PropertiesComponent.getInstance().getValue(SPLITTER_PROPORTION_OPTION);
    if (s != null) {
      try {
        mySplitter.setProportion(Float.valueOf(s).floatValue());
      } catch (NumberFormatException e) {
        //
      }
    } else {
      mySplitter.setProportion(0.8f);
    }
  }

  private void initDetails() {
    if (myDetailsPanel == null) {
      myDetailsPanel = myDiffDetails.getPanel();
    }
  }

  public Collection<AbstractVcs> getAffectedVcses() {
    if (! myShowVcsCommit) {
      return Collections.emptySet();
    }
    return myBrowserExtender.getAffectedVcses();
  }

  private static List<AbstractVcs> getAffectedVcses(Project project, final Collection<Change> changes) {
    Set<AbstractVcs> result = new HashSet<AbstractVcs>();
    for (Change change : changes) {
      final AbstractVcs vcs = ChangesUtil.getVcsForChange(change, project);
      if (vcs != null) {
        result.add(vcs);
      }
    }
    return new ArrayList<AbstractVcs>(result);
  }

  public Collection<VirtualFile> getRoots() {
    Set<VirtualFile> result = new HashSet<VirtualFile>();
    for (Change change : myBrowser.getCurrentDisplayedChanges()) {
      final FilePath filePath = ChangesUtil.getFilePath(change);
      VirtualFile root = ProjectLevelVcsManager.getInstance(myProject).getVcsRootFor(filePath);
      if (root != null) {
        result.add(root);
      }
    }
    return result;
  }

  public JComponent getComponent() {
    return mySplitter;
  }

  public boolean hasDiffs() {
    return !getIncludedChanges().isEmpty();
  }

  public Collection<VirtualFile> getVirtualFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Change change: getIncludedChanges()) {
      final FilePath path = ChangesUtil.getFilePath(change);
      final VirtualFile vFile = path.getVirtualFile();
      if (vFile != null) {
        result.add(vFile);
      }
    }

    return result;
  }

  public Collection<Change> getSelectedChanges() {
    return new ArrayList<Change>(getIncludedChanges());
  }

  public Collection<File> getFiles() {
    List<File> result = new ArrayList<File>();
    for (Change change: getIncludedChanges()) {
      final FilePath path = ChangesUtil.getFilePath(change);
      final File file = path.getIOFile();
      result.add(file);
    }

    return result;
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean vcsIsAffected(String name) {
    // tod +- performance?
    if (! ProjectLevelVcsManager.getInstance(myProject).checkVcsIsActive(name)) return false;
    final Collection<AbstractVcs> affected = myBrowserExtender.getAffectedVcses();
    for (AbstractVcs vcs : affected) {
      if (Comparing.equal(vcs.getName(), name)) return true;
    }
    return false;
  }

  public void setCommitMessage(final String currentDescription) {
    setCommitMessageText(currentDescription);
    myCommitMessageArea.requestFocusInMessage();
  }

  public Object getContextInfo(Object object) {
    // todo
    return null;
  }

  public void setWarning(String s) {
    // todo
  }

  private void setCommitMessageText(final String currentDescription) {
    myLastKnownComment = currentDescription;
    myCommitMessageArea.setText(currentDescription);
  }

  public String getCommitMessage() {
    return myCommitMessageArea.getComment();
  }

  public void refresh() {
    ChangeListManager.getInstance(myProject).invokeAfterUpdate(new Runnable() {
      public void run() {
        myBrowser.rebuildList();
        for (RefreshableOnComponent component : myAdditionalComponents) {
          component.refresh();
        }
      }
    }, InvokeAfterUpdateMode.SILENT, "commit dialog", ModalityState.current());   // title not shown for silently
  }

  public void saveState() {
    for (RefreshableOnComponent component : myAdditionalComponents) {
      component.saveState();
    }
  }

  public void restoreState() {
    for (RefreshableOnComponent component : myAdditionalComponents) {
      component.restoreState();
    }
  }

  private void updateButtons() {
    if (myDisposed) return;
    final boolean enabled = hasDiffs();
    setOKActionEnabled(enabled);
    if (myCommitAction != null) {
      myCommitAction.setEnabled(enabled);
    }
    if (myExecutorActions != null) {
      for (Action executorAction : myExecutorActions) {
        executorAction.setEnabled(enabled);
      }
    }
    myOKButtonUpdateAlarm.cancelAllRequests();
    myOKButtonUpdateAlarm.addRequest(myUpdateButtonsRunnable, 300, ModalityState.stateForComponent(myBrowser));
  }

  private void updateLegend() {
    if (myDisposed) return;
    myChangesInfoCalculator.update(myBrowser.getCurrentDisplayedChanges(), myBrowserExtender.getCurrentIncludedChanges());
    myLegend.update();
  }

  @NotNull
  private List<Change> getIncludedChanges() {
    return myBrowserExtender.getCurrentIncludedChanges();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "CommitChangelistDialog";
  }
  
  public JComponent getPreferredFocusedComponent() {
    return myCommitMessageArea.getEditorField();
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key == CheckinProjectPanel.PANEL_KEY) {
      sink.put(CheckinProjectPanel.PANEL_KEY, this);
    }
    else {
      myBrowser.calcData(key, sink);
    }
  }

  static String trimEllipsis(final String title) {
    if (title.endsWith("...")) {
      return title.substring(0, title.length() - 3);
    }
    else {
      return title;
    }
  }

  private void ensureDataIsActual(final Runnable runnable) {
    ChangeListManager.getInstance(myProject).invokeAfterUpdate(runnable, InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE,
                                                               "Refreshing changelists...", ModalityState.current());
  }

  private class CommitExecutorAction extends AbstractAction {
    private final CommitExecutor myCommitExecutor;

    public CommitExecutorAction(final CommitExecutor commitExecutor, final boolean isDefault) {
      super(commitExecutor.getActionText());
      myCommitExecutor = commitExecutor;
      if (isDefault) {
        putValue(DEFAULT_ACTION, Boolean.TRUE);
      }
    }

    public void actionPerformed(ActionEvent e) {
      final Runnable callExecutor = new Runnable() {
        public void run() {
          execute(myCommitExecutor);
        }
      };
      if (myBrowser.isDataIsDirty()) {
        ensureDataIsActual(callExecutor);
      } else {
        callExecutor.run();
      }
    }
  }

  private static class DiffCommitMessageEditor extends CommitMessage implements Disposable {
    private CommitChangeListDialog myCommitDialog;

    public DiffCommitMessageEditor(final CommitChangeListDialog dialog) {
      super(dialog.getProject());
      getEditorField().setText(dialog.getCommitMessage());
      myCommitDialog = dialog;
      myCommitDialog.setMessageConsumer(new Consumer<String>() {
        @Override
        public void consume(String s) {
          getEditorField().setText(s);
        }
      });
    }

    public void dispose() {
      if (myCommitDialog != null) {
        myCommitDialog.setMessageConsumer(null);
        final String text = getEditorField().getText();
        if (! Comparing.equal(myCommitDialog.getCommitMessage(), text)) {
          myCommitDialog.setCommitMessage(text);
        }
        myCommitDialog = null;
      }
    }

    public Dimension getPreferredSize() {
      // we don't want to be squeezed to one line
      return new Dimension(400, 120);
    }
  }

  public void setMessageConsumer(Consumer<String> messageConsumer) {
    myCommitMessageArea.setMessageConsumer(messageConsumer);
  }
}
