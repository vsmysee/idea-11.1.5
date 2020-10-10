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

package com.intellij.ide.hierarchy;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class CallHierarchyBrowserBase extends HierarchyBrowserBaseEx {

  @SuppressWarnings({"UnresolvedPropertyKey"})
  public static final String CALLEE_TYPE = IdeBundle.message("title.hierarchy.callees.of");
  @SuppressWarnings({"UnresolvedPropertyKey"})
  public static final String CALLER_TYPE = IdeBundle.message("title.hierarchy.callers.of");

  private static final String CALL_HIERARCHY_BROWSER_DATA_KEY = "com.intellij.ide.hierarchy.CallHierarchyBrowserBase";

  public CallHierarchyBrowserBase(final Project project, final PsiElement method) {
    super(project, method);


  }

  @Nullable
  protected JPanel createLegendPanel() {
    return null;
  }

  @NotNull
  protected String getBrowserDataKey() {
    return CALL_HIERARCHY_BROWSER_DATA_KEY;
  }

  protected void prependActions(@NotNull DefaultActionGroup actionGroup) {
    actionGroup.add(new ChangeViewTypeActionBase(IdeBundle.message("action.caller.methods.hierarchy"),
                                                 IdeBundle.message("action.caller.methods.hierarchy"),
                                                 IconLoader.getIcon("/hierarchy/caller.png"), CALLER_TYPE));
    actionGroup.add(new ChangeViewTypeActionBase(IdeBundle.message("action.callee.methods.hierarchy"),
                                                 IdeBundle.message("action.callee.methods.hierarchy"),
                                                 IconLoader.getIcon("/hierarchy/callee.png"), CALLEE_TYPE));
    actionGroup.add(new AlphaSortAction());
    actionGroup.add(new ChangeScopeAction());
  }

  @NotNull
  protected String getActionPlace() {
    return ActionPlaces.CALL_HIERARCHY_VIEW_TOOLBAR;
  }

  @NotNull
  protected String getPrevOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.call.prev.occurence.name");
  }

  @NotNull
  protected String getNextOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.call.next.occurence.name");
  }

  private class ChangeViewTypeActionBase extends ToggleAction {
    private final String myTypeName;

    public ChangeViewTypeActionBase(final String shortDescription, final String longDescription, final Icon icon, String typeName) {
      super(shortDescription, longDescription, icon);
      myTypeName = typeName;
    }

    public final boolean isSelected(final AnActionEvent event) {
      return myTypeName.equals(myCurrentViewType);
    }

    public final void setSelected(final AnActionEvent event, final boolean flag) {
      if (flag) {
//        setWaitCursor();
        // invokeLater is called to update state of button before long tree building operation
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            changeView(myTypeName);
          }
        });
      }
    }

    public final void update(final AnActionEvent event) {
      super.update(event);
      setEnabled(isValidBase());
    }
  }

  protected static class BaseOnThisMethodAction extends BaseOnThisElementAction {
    public BaseOnThisMethodAction() {
      super(IdeBundle.message("action.base.on.this.method"), IdeActions.ACTION_CALL_HIERARCHY, CALL_HIERARCHY_BROWSER_DATA_KEY);
    }
  }

}
