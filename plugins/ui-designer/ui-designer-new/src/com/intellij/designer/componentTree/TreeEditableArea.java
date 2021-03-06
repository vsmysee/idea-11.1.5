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
package com.intellij.designer.componentTree;

import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.RadComponent;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class TreeEditableArea implements EditableArea, FeedbackTreeLayer, TreeSelectionListener {
  private final EventListenerList myListenerList = new EventListenerList();
  private final ComponentTree myTree;
  private final AbstractTreeBuilder myTreeBuilder;

  public TreeEditableArea(ComponentTree tree, AbstractTreeBuilder treeBuilder) {
    myTree = tree;
    myTreeBuilder = treeBuilder;
    hookSelection();
  }

  private void hookSelection() {
    myTree.getSelectionModel().addTreeSelectionListener(this);
  }

  public void unhookSelection() {
    myTree.getSelectionModel().removeTreeSelectionListener(this);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Selection
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void addSelectionListener(ComponentSelectionListener listener) {
    myListenerList.add(ComponentSelectionListener.class, listener);
  }

  @Override
  public void removeSelectionListener(ComponentSelectionListener listener) {
    myListenerList.remove(ComponentSelectionListener.class, listener);
  }

  private void fireSelectionChanged() {
    for (ComponentSelectionListener listener : myListenerList.getListeners(ComponentSelectionListener.class)) {
      listener.selectionChanged(this);
    }
  }

  @Override
  public void valueChanged(TreeSelectionEvent e) {
    fireSelectionChanged();
  }

  @NotNull
  @Override
  public List<RadComponent> getSelection() {
    return new ArrayList<RadComponent>(getRawSelection());
  }

  @Override
  public boolean isSelected(@NotNull RadComponent component) {
    return getRawSelection().contains(component);
  }

  @Override
  public void select(@NotNull RadComponent component) {
    setRawSelection(component);
  }

  @Override
  public void deselect(@NotNull RadComponent component) {
    Collection<RadComponent> selection = getRawSelection();
    selection.remove(component);
    setRawSelection(selection);
  }

  @Override
  public void appendSelection(@NotNull RadComponent component) {
    Collection<RadComponent> selection = getRawSelection();
    selection.add(component);
    setRawSelection(selection);
  }

  @Override
  public void setSelection(@NotNull List<RadComponent> components) {
    setRawSelection(components);
  }

  @Override
  public void deselectAll() {
    setRawSelection(null);
  }

  private Collection<RadComponent> getRawSelection() {
    return myTreeBuilder.getSelectedElements(RadComponent.class);
  }

  private void setRawSelection(@Nullable Object value) {
    unhookSelection();
    myTreeBuilder.queueUpdate();

    // skip Tree.clearSelection() echo
    Runnable onDone = new Runnable() {
      @Override
      public void run() {
        hookSelection();
        fireSelectionChanged();
      }
    };

    if (value == null) {
      myTreeBuilder.select(ArrayUtil.EMPTY_OBJECT_ARRAY, onDone);
    }
    else if (value instanceof RadComponent) {
      myTreeBuilder.select(value, onDone);
    }
    else {
      myTreeBuilder.select(((Collection)value).toArray(), onDone);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Visual
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void setCursor(@Nullable Cursor cursor) {
    myTree.setCursor(cursor);
  }

  @NotNull
  @Override
  public JComponent getNativeComponent() {
    return myTree;
  }

  @Override
  public RadComponent findTarget(int x, int y, @Nullable ComponentTargetFilter filter) {
    TreePath path = myTree.getPathForLocation(x, y);
    if (path != null) {
      RadComponent component = myTree.extractComponent(path.getLastPathComponent());
      if (filter != null) {
        while (component != null) {
          if (filter.preFilter(component) && filter.resultFilter(component)) {
            break;
          }
          component = component.getParent();
        }
      }
      return component;
    }
    return null;
  }

  @Override
  public InputTool findTargetTool(int x, int y) {
    return null;
  }

  @Override
  public ComponentDecorator getRootSelectionDecorator() {
    return null;
  }

  @Override
  public EditOperation processRootOperation(OperationContext context) {
    return null;
  }

  @Override
  public FeedbackLayer getFeedbackLayer() {
    return null;
  }

  @Override
  public RadComponent getRootComponent() {
    return null;
  }

  @Override
  public boolean isTree() {
    return true;
  }

  @Override
  public FeedbackTreeLayer getFeedbackTreeLayer() {
    return this;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // FeedbackTreeLayer
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private TreePath getPath(RadComponent component) {
    // TODO: I don't know better way gets tree path for element
    return new TreePath(myTreeBuilder.getNodeForElement(component).getPath());
  }

  @Override
  public void mark(RadComponent component, int feedback) {
    if (component != null) {
      TreePath path = getPath(component);
      if (feedback == INSERT_SELECTION) {
        myTree.scrollPathToVisible(path);
        if (!myTree.isExpanded(path)) {
          myTreeBuilder.expand(component, null);
        }
      }
      else {
        myTree.scrollRowToVisible(myTree.getRowForPath(path) + (feedback == INSERT_BEFORE ? -1 : 1));
      }
    }
    myTree.mark(component, feedback);
  }

  @Override
  public boolean isBeforeLocation(RadComponent component, int x, int y) {
    Rectangle bounds = myTree.getPathBounds(getPath(component));
    return bounds != null && y - bounds.y < myTree.getEdgeSize();
  }

  @Override
  public boolean isAfterLocation(RadComponent component, int x, int y) {
    Rectangle bounds = myTree.getPathBounds(getPath(component));
    return bounds != null && bounds.getMaxY() - y < myTree.getEdgeSize();
  }
}