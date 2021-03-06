/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer;

import com.intellij.designer.componentTree.ComponentTree;
import com.intellij.designer.componentTree.ComponentTreeBuilder;
import com.intellij.designer.componentTree.TreeEditableArea;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.propertyTable.PropertyTablePanel;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public final class DesignerToolWindowManager implements ProjectComponent {
  private final MergingUpdateQueue myWindowQueue = new MergingUpdateQueue("designer.components.properties", 200, true, null);
  private final Project myProject;
  private final FileEditorManager myFileEditorManager;
  private ToolWindow myToolWindow;
  private ComponentTree myComponentTree;
  private ComponentTreeBuilder myTreeBuilder;
  private PropertyTablePanel myPropertyTablePanel;
  private boolean myToolWindowReady;
  private boolean myToolWindowDisposed;

  public DesignerToolWindowManager(Project project, FileEditorManager fileEditorManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;
    project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(FileEditorManager source, VirtualFile file) {
        bindToDesigner(getActiveDesigner());
      }

      @Override
      public void fileClosed(FileEditorManager source, VirtualFile file) {
        bindToDesigner(getActiveDesigner());
      }

      @Override
      public void selectionChanged(FileEditorManagerEvent event) {
        bindToDesigner(getDesigner(event.getNewEditor()));
      }
    });
  }

  @Override
  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myToolWindowReady = true;
      }
    });
  }

  @Override
  public void projectClosed() {
    if (!myToolWindowDisposed) {
      myToolWindowDisposed = true;
      clearTreeBuilder();
      myComponentTree = null;
      myPropertyTablePanel = null;
      myToolWindow = null;
    }
  }

  private void clearTreeBuilder() {
    if (myTreeBuilder != null) {
      Disposer.dispose(myTreeBuilder);
      myTreeBuilder = null;
    }
  }

  public static DesignerToolWindowManager getInstance(Project project) {
    return project.getComponent(DesignerToolWindowManager.class);
  }

  public void refresh() {
    if (myTreeBuilder != null) {
      myTreeBuilder.queueUpdate();
    }
  }

  @Nullable
  public TreeEditableArea getTreeArea() {
    return myTreeBuilder == null ? null : myTreeBuilder.getTreeArea();
  }

  @Nullable
  private static DesignerEditorPanel getDesigner(FileEditor editor) {
    if (editor instanceof DesignerEditor) {
      DesignerEditor designerEditor = (DesignerEditor)editor;
      return designerEditor.getDesignerPanel();
    }
    return null;
  }

  @Nullable
  public DesignerEditorPanel getActiveDesigner() {
    FileEditor[] editors = myFileEditorManager.getSelectedEditors();
    return editors.length > 0 ? getDesigner(editors[0]) : null;
  }

  private void bindToDesigner(final DesignerEditorPanel designer) {
    myWindowQueue.cancelAllUpdates();
    myWindowQueue.queue(new Update("update") {
      @Override
      public void run() {
        if (!myToolWindowReady || myToolWindowDisposed) {
          return;
        }
        if (myToolWindow == null) {
          if (designer == null) {
            return;
          }
          initToolWindow();
        }
        clearTreeBuilder();
        myComponentTree.newModel();
        if (designer == null) {
          myComponentTree.setDecorator(null);
          myComponentTree.setActionPanel(null);
          myPropertyTablePanel.getPropertyTable().setArea(null, null);
          myToolWindow.setAvailable(false, null);
        }
        else {
          myComponentTree.setDecorator(designer.getTreeDecorator());
          myComponentTree.setActionPanel(designer.getActionPanel());
          myTreeBuilder = new ComponentTreeBuilder(myComponentTree, designer);
          myPropertyTablePanel.getPropertyTable().setArea(designer, myTreeBuilder.getTreeArea());
          myToolWindow.setAvailable(true, null);
          myToolWindow.show(null);
        }
      }
    });
  }

  private void initToolWindow() {
    myComponentTree = new ComponentTree();
    JScrollPane treeScrollPane = ScrollPaneFactory.createScrollPane(myComponentTree);
    treeScrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    treeScrollPane.setPreferredSize(new Dimension(250, -1));

    myPropertyTablePanel = new PropertyTablePanel();

    // TODO: auto change orientation: IF (width < height) vertical ELSE horizontal
    Splitter toolWindowPanel = new Splitter(true, 0.42f);
    toolWindowPanel.setFirstComponent(treeScrollPane);
    toolWindowPanel.setSecondComponent(myPropertyTablePanel);

    myToolWindow =
      ToolWindowManager.getInstance(myProject)
        .registerToolWindow(DesignerBundle.message("designer.toolwindow.name"), false, ToolWindowAnchor.LEFT, myProject, true);
    myToolWindow.setIcon(IconLoader.getIcon("/com/intellij/designer/icons/toolWindow.png"));

    ((ToolWindowEx)myToolWindow).setTitleActions(createActions());

    ContentManager contentManager = myToolWindow.getContentManager();
    Content content =
      contentManager.getFactory().createContent(toolWindowPanel, DesignerBundle.message("designer.toolwindow.title"), false);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(myComponentTree);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    myToolWindow.setAvailable(false, null);
  }

  private AnAction[] createActions() {
    AnAction expandAll = new AnAction("Expand All", null, IconLoader.getIcon("/com/intellij/designer/icons/ExpandAll.png")) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myTreeBuilder != null) {
          myTreeBuilder.expandAll(null);
        }
      }
    };

    AnAction collapseAll = new AnAction("Collapse All", null, IconLoader.getIcon("/general/collapseAll.png")) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myTreeBuilder != null) {
          TreeUtil.collapseAll(myComponentTree, 1);
        }
      }
    };

    return new AnAction[]{expandAll, collapseAll};
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @NonNls
  @Override
  public String getComponentName() {
    return "UIDesignerToolWindowManager2";
  }
}