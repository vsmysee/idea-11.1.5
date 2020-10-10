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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.CommonBundle;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.HelpID;
import com.intellij.debugger.ui.breakpoints.actions.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jdom.Element;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 26, 2005
 */
public class FieldBreakpointFactory extends BreakpointFactory{
  public Breakpoint createBreakpoint(Project project, final Element element) {
    return new FieldBreakpoint(project);
  }

  public Icon getIcon() {
    return FieldBreakpoint.ICON;
  }

  public Icon getDisabledIcon() {
    return FieldBreakpoint.DISABLED_ICON;
  }

  public BreakpointPanel createBreakpointPanel(final Project project, final DialogWrapper parentDialog) {
    BreakpointPanel panel = new BreakpointPanel(project, new FieldBreakpointPropertiesPanel(project), new BreakpointPanelAction[] {
      new SwitchViewAction(),
      new AddFieldBreakpointAction(project),
      new GotoSourceAction(project) {
        public void actionPerformed(ActionEvent e) {
          super.actionPerformed(e);
          parentDialog.close(DialogWrapper.OK_EXIT_CODE);
        }
      },
      new ViewSourceAction(project),
      new RemoveAction(project),
      new ToggleGroupByClassesAction(),
      new ToggleFlattenPackagesAction(),
    }, getBreakpointCategory(), DebuggerBundle.message("field.watchpoints.tab.title"), HelpID.FIELD_WATCHPOINTS);
    panel.getTree().setGroupByMethods(false);
    return panel;
  }

  public Key<FieldBreakpoint> getBreakpointCategory() {
    return FieldBreakpoint.CATEGORY;
  }

  private static class AddFieldBreakpointAction extends AddAction {
    private final Project myProject;

    public AddFieldBreakpointAction(Project project) {
      myProject = project;
    }

    public void actionPerformed(ActionEvent e) {
      AddFieldBreakpointDialog dialog = new AddFieldBreakpointDialog(myProject) {
        protected boolean validateData() {
          String className = getClassName();
          if (className.length() == 0) {
            Messages.showMessageDialog(myProject, DebuggerBundle.message("error.field.breakpoint.class.name.not.specified"),
                                       DebuggerBundle.message("add.field.breakpoint.dialog.title"), Messages.getErrorIcon());
            return false;
          }
          String fieldName = getFieldName();
          if (fieldName.length() == 0) {
            Messages.showMessageDialog(myProject, DebuggerBundle.message("error.field.breakpoint.field.name.not.specified"),
                                       DebuggerBundle.message("add.field.breakpoint.dialog.title"), Messages.getErrorIcon());
            return false;
          }
          PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
          if (psiClass != null) {
            PsiFile  psiFile  = psiClass.getContainingFile();
            Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
            if(document != null) {
              PsiField field = psiClass.findFieldByName(fieldName, true);
              if(field != null) {
                int line = document.getLineNumber(field.getTextOffset());
                FieldBreakpoint fieldBreakpoint = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().addFieldBreakpoint(document, line, fieldName);
                if (fieldBreakpoint != null) {
                  getPanel().addBreakpoint(fieldBreakpoint);
                  return true;
                }
              }
              else {
                Messages.showMessageDialog(
                  myProject,
                  DebuggerBundle.message("error.field.breakpoint.field.not.found", className, fieldName, fieldName),
                  CommonBundle.getErrorTitle(),
                  Messages.getErrorIcon()
                );

              }
            }
          } else {
            Messages.showMessageDialog(
              myProject,
              DebuggerBundle.message("error.field.breakpoint.class.sources.not.found", className, fieldName, className),
              CommonBundle.getErrorTitle(),
              Messages.getErrorIcon()
            );
          }
          return false;
        }
      };
      dialog.show();
    }
  }

}
