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
package com.intellij.util.concurrency.readwrite;

import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.IdeFrame;

public class WriteActionWaiter extends AbstractWaiter implements ApplicationListener {

  private final Runnable myActionRunnable;

  public WriteActionWaiter(Runnable aActionRunnable) {
    myActionRunnable = aActionRunnable;

    setFinished(false);
    ApplicationManager.getApplication().addApplicationListener(this);
  }

  public void writeActionFinished(Object aRunnable) {
    if (aRunnable == myActionRunnable) {
      setFinished(true);

      ApplicationManager.getApplication().removeApplicationListener(this);
    }
  }

  public void applicationExiting() {
  }

  public void beforeWriteActionStart(Object action) {
  }

  public boolean canExitApplication() {
    return true;
  }

  public void writeActionStarted(Object action) {
  }

  public void applicationActivated(IdeFrame ideFrame) {
  }

  public void applicationDeactivated(IdeFrame ideFrame) {
  }
}
