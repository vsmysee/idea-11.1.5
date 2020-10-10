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

package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.actions.BaseViewAction;
import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.content.Content;

import javax.swing.*;

public class CloseViewAction extends BaseViewAction {
  private static final Icon ICON = IconLoader.getIcon("/actions/closeNew.png");
  private static final Icon HOVERED_ICON = IconLoader.getIcon("/actions/closeNewHovered.png");

  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    setEnabled(e, isEnabled(content));
    e.getPresentation().setIcon(ICON);
    e.getPresentation().setHoveredIcon(HOVERED_ICON);
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    perform(context, content[0]);
  }

  public static boolean perform(ViewContext context, Content content) {
    return context.getContentManager().removeContent(content, context.isToDisposeRemovedContent());
  }

  public static boolean isEnabled(Content[] content) {
    return content.length == 1 && content[0].isCloseable();
  }
  
}