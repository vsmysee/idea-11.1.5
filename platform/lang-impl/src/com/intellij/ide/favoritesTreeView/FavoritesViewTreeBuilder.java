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
package com.intellij.ide.favoritesTreeView;

import com.intellij.ProjectTopics;
import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewPsiTreeChangeListener;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * @author Konstantin Bulenkov
 */
public class FavoritesViewTreeBuilder extends BaseProjectTreeBuilder {
  private final ProjectViewPsiTreeChangeListener myPsiTreeChangeListener;
  private final FileStatusListener myFileStatusListener;
  private final CopyPasteUtil.DefaultCopyPasteListener myCopyPasteListener;
  private final FavoritesListener myFavoritesListener;

  public FavoritesViewTreeBuilder(Project project,
                                  JTree tree,
                                  DefaultTreeModel treeModel,
                                  ProjectAbstractTreeStructureBase treeStructure) {
    super(project, 
          tree, 
          treeModel, 
          treeStructure, 
          new FavoritesComparator(ProjectView.getInstance(project), FavoritesProjectViewPane.ID));
    final MessageBusConnection bus = myProject.getMessageBus().connect(this);
    myPsiTreeChangeListener = new ProjectViewPsiTreeChangeListener(myProject) {
      protected DefaultMutableTreeNode getRootNode() {
        return FavoritesViewTreeBuilder.this.getRootNode();
      }

      protected AbstractTreeUpdater getUpdater() {
        return FavoritesViewTreeBuilder.this.getUpdater();
      }

      protected boolean isFlattenPackages() {
        return getStructure().isFlattenPackages();
      }

      protected void childrenChanged(PsiElement parent, final boolean stopProcessingForThisModificationCount) {
        if (findNodeByElement(parent) == null){
          queueUpdate(true);
        } else {
          super.childrenChanged(parent, true);
        }
      }
    };
    bus.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        queueUpdate(true);
      }
    });
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeListener);
    myFileStatusListener = new MyFileStatusListener();
    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener);
    myCopyPasteListener = new CopyPasteUtil.DefaultCopyPasteListener(getUpdater());
    CopyPasteManager.getInstance().addContentChangedListener(myCopyPasteListener);

    myFavoritesListener = new FavoritesListener() {
      public void rootsChanged(String listName) {
        //if (myListName.equals(listName)) { //todo[kb]: add optimizations?
          updateFromRoot();
        //}
      }

      public void listAdded(String listName) {
        updateFromRoot();
      }

      public void listRemoved(String listName) {
        updateFromRoot();
      }
    };
    FavoritesManager.getInstance(myProject).addFavoritesListener(myFavoritesListener);
    initRootNode();
  }

  @NotNull
  public FavoritesTreeStructure getStructure() {
    final AbstractTreeStructure structure = getTreeStructure();
    assert structure instanceof FavoritesTreeStructure;
    return (FavoritesTreeStructure)structure;
  }
  
  public AbstractTreeNode getRoot() {
    final Object rootElement = getRootElement();
    assert rootElement instanceof AbstractTreeNode;
    return (AbstractTreeNode)rootElement;
  }

  public void updateFromRoot() {
    updateFromRootCB();
  }

  @NotNull
  public ActionCallback updateFromRootCB() {
    getStructure().rootsChanged();
    if (isDisposed()) return new ActionCallback.Done();
    getUpdater().cancelAllRequests();
    return super.updateFromRootCB();
  }

  public ActionCallback select(Object element, VirtualFile file, boolean requestFocus) {
    final DefaultMutableTreeNode node = findSmartFirstLevelNodeByElement(element);
    if (node != null){
      return TreeUtil.selectInTree(node, requestFocus, getTree());
    }
    return super.select(element, file, requestFocus);
  }

  @Nullable
  private static DefaultMutableTreeNode findFirstLevelNodeWithObject(final DefaultMutableTreeNode aRoot, final Object aObject) {
    for (int i = 0; i < aRoot.getChildCount(); i++) {
      final DefaultMutableTreeNode child = (DefaultMutableTreeNode)aRoot.getChildAt(i);
      Object userObject = child.getUserObject();
      if (userObject instanceof FavoritesTreeNodeDescriptor) {
        if (Comparing.equal(((FavoritesTreeNodeDescriptor)userObject).getElement(), aObject)) {
          return child;
        }
      }
    }
    return null;
  }

  protected Object findNodeByElement(Object element) {
    final Object node = findSmartFirstLevelNodeByElement(element);
    if (node != null) return node;
    return super.findNodeByElement(element);
  }
  
  @Nullable
  DefaultMutableTreeNode findSmartFirstLevelNodeByElement(final Object element) {
    for (Object child : getRoot().getChildren()) {
      AbstractTreeNode favorite = (AbstractTreeNode)child;
      Object currentValue = favorite.getValue();
      if (currentValue instanceof SmartPsiElementPointer){
        currentValue = ((SmartPsiElementPointer)favorite.getValue()).getElement();
      }
       /*else if (currentValue instanceof PsiJavaFile) {
        final PsiClass[] classes = ((PsiJavaFile)currentValue).getClasses();
        if (classes.length > 0) {
          currentValue = classes[0];
        }
      }*/
      if (Comparing.equal(element, currentValue)){
        final DefaultMutableTreeNode nodeWithObject = findFirstLevelNodeWithObject((DefaultMutableTreeNode)getTree().getModel().getRoot(), favorite);
        if (nodeWithObject != null){
          return nodeWithObject;
        }
      }
    }
    return null;
  }

  public final void dispose() {
    super.dispose();
    FavoritesManager.getInstance(myProject).removeFavoritesListener(myFavoritesListener);

    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeListener);
    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    CopyPasteManager.getInstance().removeContentChangedListener(myCopyPasteListener);
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    final Object[] childElements = getStructure().getChildElements(nodeDescriptor);
    return childElements != null && childElements.length > 0;
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getParentDescriptor() == null;
  }

  private final class MyFileStatusListener implements FileStatusListener {
    public void fileStatusesChanged() {
      queueUpdateFrom(getRootNode(), false);
    }

    public void fileStatusChanged(@NotNull VirtualFile vFile) {
      PsiElement element;
      PsiManager psiManager = PsiManager.getInstance(myProject);
      if (vFile.isDirectory()) {
        element = psiManager.findDirectory(vFile);
      }
      else {
        element = psiManager.findFile(vFile);
      }

      if (!getUpdater().addSubtreeToUpdateByElement(element) && element instanceof PsiFile && ((PsiFile) element).getFileType() == StdFileTypes.JAVA) {
        getUpdater().addSubtreeToUpdateByElement(((PsiFile)element).getContainingDirectory());
      }
    }
  }

}

