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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class MoveChangesToAnotherListAction extends AnAction implements DumbAware {
  public MoveChangesToAnotherListAction() {
    super(ActionsBundle.actionText("ChangesView.Move"),
          ActionsBundle.actionDescription("ChangesView.Move"),
          IconLoader.getIcon("/actions/fileStatus.png"));
  }

  public void update(AnActionEvent e) {
    final boolean isEnabled = isEnabled(e);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(isEnabled);
    }
    else {
      e.getPresentation().setEnabled(isEnabled);
    }
  }
  
  private static boolean isEnabled(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return false;

    final List<VirtualFile> unversionedFiles = e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY);
    if (unversionedFiles != null && (! unversionedFiles.isEmpty())) return true;

    final boolean hasChangedOrUnversionedFiles = SelectedFilesHelper.hasChangedOrUnversionedFiles(project, e);
    if (hasChangedOrUnversionedFiles) return true;
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    return changes != null && changes.length > 0;
  }

  @Nullable
  private static Change[] getChangesForSelectedFiles(final Project project, Change[] changes, final List<VirtualFile> unversionedFiles,
                                                     @Nullable final List<VirtualFile> changedFiles, final AnActionEvent e) {
    if (ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().length == 0) {
      return null;
    }
    
    VirtualFile[] virtualFiles = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (virtualFiles != null) {
      List<Change> changesInFiles = new ArrayList<Change>();
      for(VirtualFile vFile: virtualFiles) {
        Change change = ChangeListManager.getInstance(project).getChange(vFile);
        if (change == null) continue;
        if (change.getFileStatus().equals(FileStatus.UNKNOWN)) {
          unversionedFiles.add(vFile);
          if (changedFiles != null) changedFiles.add(vFile);
        }
        else {
          changesInFiles.add(change);
          if (changedFiles != null) changedFiles.add(vFile);
        }
      }
      if (changesInFiles.size() > 0 || unversionedFiles.size() > 0) {
        changes = changesInFiles.toArray(new Change[changesInFiles.size()]);
      }
    }
    return changes;
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    List<VirtualFile> unversionedFiles = e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY);

    final List<VirtualFile> changedFiles = new ArrayList<VirtualFile>();
    boolean activateChangesView = false;
    if (project != null && changes == null && unversionedFiles == null) {
      unversionedFiles = new ArrayList<VirtualFile>();
      changes = getChangesForSelectedFiles(project, changes, unversionedFiles, changedFiles, e);
      activateChangesView = true;
    }

    if (changes == null) return;

    if (!askAndMove(project, changes, unversionedFiles)) return;
    if (activateChangesView) {
      ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
      if (!window.isVisible()) {
        window.activate(new Runnable() {
          public void run() {
            if (changedFiles.size() > 0) {
              ChangesViewManager.getInstance(project).selectFile(changedFiles.get(0));
            }
          }
        });
      }
    }
  }

  public static boolean askAndMove(final Project project, final Change[] changes, final List<VirtualFile> unversionedFiles) {
    final ChangeListManagerImpl listManager = ChangeListManagerImpl.getInstanceImpl(project);
    final List<LocalChangeList> lists = listManager.getChangeLists();
    ChangeListChooser chooser = new ChangeListChooser(project, getPreferredLists(lists, changes, true), guessPreferredList(lists, changes),
                                                      ActionsBundle.message("action.ChangesView.Move.text"), null);
    chooser.show();
    LocalChangeList resultList = chooser.getSelectedList();
    if (resultList != null) {
      listManager.moveChangesTo(resultList, changes);
      if ((unversionedFiles != null) && (! unversionedFiles.isEmpty())) {
        listManager.addUnversionedFiles(resultList, unversionedFiles);
      }
      return true;
    }
    return false;
  }

  @Nullable
  private static ChangeList guessPreferredList(final List<LocalChangeList> lists, final Change[] changes) {
    List<LocalChangeList> preferredLists = getPreferredLists(lists, changes, false);

    for (ChangeList preferredList : preferredLists) {
      if (preferredList.getChanges().isEmpty()) {
        return preferredList;
      }
    }

    if (preferredLists.size() > 0) {
      return preferredLists.get(0);
    }

    return null;
  }

  private static List<LocalChangeList> getPreferredLists(final List<LocalChangeList> lists,
                                                    final Change[] changes,
                                                    final boolean includeDefaultIfEmpty) {
    List<LocalChangeList> preferredLists = new ArrayList<LocalChangeList>(lists);
    Set<Change> changesAsSet = new THashSet<Change>(Arrays.asList(changes));
    for (LocalChangeList list : lists) {
      for (Change change : list.getChanges()) {
        if (changesAsSet.contains(change)) {
          preferredLists.remove(list);
          break;
        }
      }
    }

    if (preferredLists.isEmpty() && includeDefaultIfEmpty) {
      for (LocalChangeList list : lists) {
        if (list.isDefault()) {
          preferredLists.add(list);
        }
      }
    }

    return preferredLists;
  }
}
