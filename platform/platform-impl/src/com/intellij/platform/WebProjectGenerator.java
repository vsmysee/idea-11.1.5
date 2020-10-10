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
package com.intellij.platform;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Extend this class to contribute web project generator to IDEA (available via File -> 'Add Module...' -> 'Web Module')
 * and to small IDE (PhpStorm, WebStorm etc. available via File -> 'New Project...').
 *
 * @author Sergey Simonchik
 */
public abstract class WebProjectGenerator<T> implements DirectoryProjectGenerator<T> {

  @Nls
  @Override
  public abstract String getName();

  @Override
  @Nullable
  public final T showGenerationSettings(VirtualFile baseDir) throws ProcessCanceledException {
    GeneratorPeer<T> peer = createPeer();
    DialogWrapper dialog = new MyDialogWrapper(peer);
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      throw new ProcessCanceledException();
    }
    return peer.getSettings();
  }

  @Override
  public abstract void generateProject(Project project, VirtualFile baseDir, T settings, Module module);

  /**
   * Always returns {@link ValidationResult#OK}.
   * Real validation should be done in {@link WebProjectGenerator.GeneratorPeer#validate()}.
   */
  @NotNull
  @Override
  public final ValidationResult validate(@NotNull String baseDirPath) {
    return ValidationResult.OK;
  }

  @NotNull
  public abstract GeneratorPeer<T> createPeer();

  public boolean isPrimaryGenerator() {
    return true;
  }

  public interface GeneratorPeer<T> {
    @NotNull
    JComponent getComponent();

    @NotNull
    T getSettings();

    @Nullable
    ValidationInfo validate();

    void addSettingsStateListener(@NotNull SettingsStateListener listener);
  }

  public interface SettingsStateListener {
    void stateChanged(boolean validSettings);
  }

  private class MyDialogWrapper extends DialogWrapper {

    private final GeneratorPeer myPeer;
    private final JComponent myCenterComponent;

    protected MyDialogWrapper(@NotNull GeneratorPeer<T> peer) {
      super(true);
      myPeer = peer;
      myCenterComponent = peer.getComponent();
      getOKAction().setEnabled(peer.validate() == null);
      peer.addSettingsStateListener(new SettingsStateListener() {
        @Override
        public void stateChanged(boolean validSettings) {
          getOKAction().setEnabled(validSettings);
        }
      });
      setTitle(WebProjectGenerator.this.getName());
      init();
    }

    @Override
    protected ValidationInfo doValidate() {
      return myPeer.validate();
    }

    @Override
    protected JComponent createCenterPanel() {
      return myCenterComponent;
    }
  }

}
