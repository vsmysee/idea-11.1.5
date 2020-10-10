// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class HgChangesetStatus extends JLabel implements CustomStatusBarWidget {

  private final String myName;

  public HgChangesetStatus(Icon icon, String name) {
    super(icon, SwingConstants.TRAILING);
    myName = name;
    setVisible(false);
  }

  public void setChanges(final int count, final ChangesetWriter formatter) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (count == 0) {
          setVisible(false);
          return;
        }
        setText(String.valueOf(count));
        setToolTipText(formatter.asString());
        setVisible(true);
      }
    });
  }

  public String getStatusName() {
    return myName;
  }

  public interface ChangesetWriter {
    String asString();
  }

  public JComponent getComponent() {
    return this;
  }

  @NotNull
  public String ID() {
    return "HgChangeSetStatus";
  }

  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  public void install(@NotNull StatusBar statusBar) {
  }

  public void dispose() {
  }
}
