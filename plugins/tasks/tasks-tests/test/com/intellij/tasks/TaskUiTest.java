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
package com.intellij.tasks;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.tasks.actions.SwitchTaskCombo;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 *         Date: 3/23/12
 */
public class TaskUiTest extends CodeInsightFixtureTestCase {

  public TaskUiTest() {
    PlatformTestCase.initPlatformPrefix(UsefulTestCase.IDEA_MARKER_CLASS, "PlatformLangXml");
  }

  public void testTaskComboVisible() throws Exception {

    TaskManager manager = TaskManager.getManager(getProject());
    SwitchTaskCombo combo = new SwitchTaskCombo();

    LocalTask defaultTask = manager.getActiveTask();
    assertTrue(defaultTask.isDefault());
    assertEquals(defaultTask.getCreated(), defaultTask.getUpdated());

    Presentation presentation = doTest(combo);
    assertFalse(presentation.isVisible());

    try {
      TaskSettings.getInstance().ALWAYS_DISPLAY_COMBO = true;
      presentation = doTest(combo);
      assertTrue(presentation.isVisible());
    }
    finally {
      TaskSettings.getInstance().ALWAYS_DISPLAY_COMBO = false;
    }

    LocalTask task = manager.createLocalTask("test");
    manager.activateTask(task, false, false);

    presentation = doTest(combo);
    assertTrue(presentation.isVisible());

    manager.activateTask(defaultTask, false, false);
    task = manager.getActiveTask();
    assertTrue(task.isDefault());

    presentation = doTest(combo);
    assertTrue(presentation.isVisible());
  }

  private static Presentation doTest(AnAction action) {
    TestActionEvent event = new TestActionEvent(DataManager.getInstance().getDataContext(), action);
    action.update(event);
    return event.getPresentation();
  }

}
