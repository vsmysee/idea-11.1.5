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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.wm.*;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author yole
 */
public class IncomingChangesIndicator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.IncomingChangesIndicator");

  private final Project myProject;
  private final CommittedChangesCache myCache;
  private IndicatorComponent myIndicatorComponent;

  public IncomingChangesIndicator(Project project, CommittedChangesCache cache, MessageBus bus) {
    myProject = project;
    myCache = cache;
    final MessageBusConnection connection = bus.connect();
    connection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new CommittedChangesAdapter() {
      public void incomingChangesUpdated(@Nullable final List<CommittedChangeList> receivedChanges) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            refreshIndicator();
          }
        });
      }
    });
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, new VcsListener() {
      @Override
      public void directoryMappingChanged() {
        updateIndicatorVisibility();
      }
    });
  }

  private void updateIndicatorVisibility() {
    final StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (needIndicator()) {
      if (myIndicatorComponent == null) {
        myIndicatorComponent = new IndicatorComponent();
        statusBar.addWidget(myIndicatorComponent, myProject);
        refreshIndicator();
      }
    }
    else {
      if (myIndicatorComponent != null) {
        statusBar.removeWidget(myIndicatorComponent.ID());
        myIndicatorComponent = null;
      }
    }
  }

  private boolean needIndicator() {
    final AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss();
    for (AbstractVcs vcs : vcss) {
      if (vcs.getCachingCommittedChangesProvider() != null) {
        return true;
      }
    }
    return false;
  }

  private void refreshIndicator() {
    if (myIndicatorComponent == null) {
      return;
    }
    final List<CommittedChangeList> list = myCache.getCachedIncomingChanges();
    if (list == null || list.isEmpty()) {
      debug("Refreshing indicator: no changes");
      myIndicatorComponent.clear();
    }
    else {
      debug("Refreshing indicator: " + list.size() + " changes");
      myIndicatorComponent.setChangesAvailable(VcsBundle.message("incoming.changes.indicator.tooltip", list.size()));
    }
  }

  private static void debug(@NonNls final String message) {
    LOG.debug(message);
  }

  private static class IndicatorComponent implements StatusBarWidget, StatusBarWidget.IconPresentation {
    private StatusBar myStatusBar;

    private static final Icon CHANGES_AVAILABLE_ICON = IconLoader.getIcon("/ide/incomingChangesOn.png");
    private static final Icon CHANGES_NOT_AVAILABLE_ICON = IconLoader.getIcon("/ide/incomingChangesOff.png");

    private Icon myCurrentIcon = CHANGES_NOT_AVAILABLE_ICON;
    private String myToolTipText;

    private IndicatorComponent() {
    }

    void clear() {
      update(CHANGES_NOT_AVAILABLE_ICON, "No incoming changelists available");
    }

    void setChangesAvailable(@NotNull final String toolTipText) {
      update(CHANGES_AVAILABLE_ICON, toolTipText);
    }

    private void update(@NotNull final Icon icon, @Nullable final String toolTipText) {
      myCurrentIcon = icon;
      myToolTipText = toolTipText;
      if (myStatusBar != null) myStatusBar.updateWidget(ID());
    }

    @NotNull
    public Icon getIcon() {
      return myCurrentIcon;
    }

    public String getTooltipText() {
      return myToolTipText;
    }

    public Consumer<MouseEvent> getClickConsumer() {
      return new Consumer<MouseEvent>() {
        public void consume(final MouseEvent mouseEvent) {
          if (myStatusBar != null) {
          DataContext dataContext = DataManager.getInstance().getDataContext((Component) myStatusBar);
          final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
          if (project != null) {
            ToolWindow changesView = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
            changesView.show(new Runnable() {
              public void run() {
                ChangesViewContentManager.getInstance(project).selectContent("Incoming");
              }
            });
          }
          }
        }
      };
    }

    @NotNull
    public String ID() {
      return "IncomingChanges";
    }

    public WidgetPresentation getPresentation(@NotNull PlatformType type) {
      return this;
    }

    public void install(@NotNull StatusBar statusBar) {
      myStatusBar = statusBar;
    }

    public void dispose() {
      myStatusBar = null;
    }
  }
}
