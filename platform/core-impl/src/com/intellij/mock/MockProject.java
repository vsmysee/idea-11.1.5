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
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

/**
 * @author yole
 */
public class MockProject extends MockComponentManager implements Project {
  public MockProject(PicoContainer parent, @NotNull Disposable parentDisposable) {
    super(parent, parentDisposable);
    Extensions.instantiateArea("IDEA_PROJECT", this, null);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        Extensions.disposeArea(MockProject.this);
      }
    });
  }

  @Override
  public boolean isDefault() {
    return false;
  }

  @NotNull
  @Override
  public Condition getDisposed() {
    return new Condition() {
      @Override
      public boolean value(final Object o) {
        return isDisposed();
      }
    };
  }

  @Override
  public boolean isOpen() {
    return false;
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public VirtualFile getProjectFile() {
    return null;
  }

  @Override
  @NotNull
  public String getName() {
    return "";
  }

  @Override
  @Nullable
  @NonNls
  public String getPresentableUrl() {
    return null;
  }

  @Override
  @NotNull
  @NonNls
  public String getLocationHash() {
    return "mock";
  }

  @Override
  @Nullable
  @NonNls
  public String getLocation() {
    throw new UnsupportedOperationException("Method getLocation not implemented in " + getClass());
  }

  @Override
  @NotNull
  public String getProjectFilePath() {
    return "";
  }

  @Override
  public VirtualFile getWorkspaceFile() {
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getBaseDir() {
    return null;
  }

  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public void save() {
  }

  @Override
  public <T> T[] getExtensions(final ExtensionPointName<T> extensionPointName) {
    return Extensions.getArea(this).getExtensionPoint(extensionPointName).getExtensions();
  }
}
