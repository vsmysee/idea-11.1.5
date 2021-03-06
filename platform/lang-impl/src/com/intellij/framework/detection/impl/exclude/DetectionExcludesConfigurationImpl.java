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
package com.intellij.framework.detection.impl.exclude;

import com.intellij.framework.FrameworkType;
import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.framework.detection.impl.exclude.old.OldFacetDetectionExcludesConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
@State(
  name = "FrameworkDetectionExcludesConfiguration",
  storages = {
    @Storage(
      id="other",
      file = "$PROJECT_FILE$"
    )
  }
)
public class DetectionExcludesConfigurationImpl extends DetectionExcludesConfiguration
         implements PersistentStateComponent<ExcludesConfigurationState>, Disposable {
  private Map<String, VirtualFilePointerContainer> myExcludedFiles;
  private Set<String> myExcludedFrameworks;
  private final Project myProject;
  private VirtualFilePointerManager myPointerManager;
  private boolean myConverted;

  public DetectionExcludesConfigurationImpl(Project project, VirtualFilePointerManager pointerManager) {
    myProject = project;
    myPointerManager = pointerManager;
    myExcludedFrameworks = new HashSet<String>();
    myExcludedFiles = new FactoryMap<String, VirtualFilePointerContainer>() {
      @Override
      protected VirtualFilePointerContainer create(String key) {
        return myPointerManager.createContainer(DetectionExcludesConfigurationImpl.this);
      }
    };
  }

  @Override
  public void addExcludedFramework(@NotNull FrameworkType type) {
    convert();
    myExcludedFrameworks.add(type.getId());
    final VirtualFilePointerContainer container = myExcludedFiles.remove(type.getId());
    if (container != null) {
      container.clear();
    }
  }

  @Override
  public void addExcludedFile(@NotNull VirtualFile file, @Nullable FrameworkType type) {
    convert();
    final String typeId = type != null ? type.getId() : null;
    if (typeId != null && myExcludedFrameworks.contains(typeId) || isFileExcluded(file, typeId)) {
      return;
    }

    final VirtualFilePointerContainer container = myExcludedFiles.get(typeId);
    if (typeId == null) {
      for (VirtualFilePointerContainer pointerContainer : myExcludedFiles.values()) {
        removeDescendants(file, pointerContainer);
      }
    }
    else {
      removeDescendants(file, container);
    }
    container.add(file);
  }

  @Override
  public void addExcludedUrl(@NotNull String url, @Nullable FrameworkType type) {
    final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file != null) {
      addExcludedFile(file, type);
      return;
    }

    convert();
    final String typeId = type != null ? type.getId() : null;
    if (typeId != null && myExcludedFrameworks.contains(typeId)) {
      return;
    }
    myExcludedFiles.get(typeId).add(url);
  }

  private void convert() {
    ensureOldSettingsLoaded();
    markAsConverted();
  }

  private void markAsConverted() {
    myConverted = true;
    OldFacetDetectionExcludesConfiguration.getInstance(myProject).loadState(null);
  }

  private void ensureOldSettingsLoaded() {
    if (!myConverted) {
      final OldFacetDetectionExcludesConfiguration oldConfiguration = OldFacetDetectionExcludesConfiguration.getInstance(myProject);
      final ExcludesConfigurationState oldState = oldConfiguration.convert();
      if (oldState != null) {
        doLoadState(oldState);
      }
    }
  }

  private boolean isFileExcluded(@NotNull VirtualFile file, @Nullable String typeId) {
    if (myExcludedFiles.containsKey(typeId) && isUnder(file, myExcludedFiles.get(typeId))) return true;
    return typeId != null && myExcludedFiles.containsKey(null) && isUnder(file, myExcludedFiles.get(null));
  }

  private static boolean isUnder(VirtualFile file, final VirtualFilePointerContainer container) {
    for (VirtualFile excludedFile : container.getFiles()) {
      if (VfsUtil.isAncestor(excludedFile, file, false)) {
        return true;
      }
    }
    return false;
  }

  private void removeDescendants(VirtualFile file, VirtualFilePointerContainer container) {
    for (VirtualFile virtualFile : container.getFiles()) {
      if (VfsUtil.isAncestor(file, virtualFile, false)) {
        container.remove(myPointerManager.create(virtualFile, this, null));
      }
    }
  }

  public void removeExcluded(@NotNull Collection<VirtualFile> files, final FrameworkType frameworkType) {
    ensureOldSettingsLoaded();
    if (myExcludedFrameworks.contains(frameworkType.getId())) {
      files.clear();
      return;
    }

    final Iterator<VirtualFile> iterator = files.iterator();
    while (iterator.hasNext()) {
      VirtualFile file = iterator.next();
      if (isFileExcluded(file, frameworkType.getId())) {
        iterator.remove();
      }
    }
  }

  @Nullable
  public ExcludesConfigurationState getActualState() {
    ensureOldSettingsLoaded();
    if (myExcludedFiles.isEmpty() && myExcludedFrameworks.isEmpty()) {
      return null;
    }

    final ExcludesConfigurationState state = new ExcludesConfigurationState();
    state.getFrameworkTypes().addAll(myExcludedFrameworks);
    Collections.sort(state.getFrameworkTypes(), String.CASE_INSENSITIVE_ORDER);

    for (String typeId : myExcludedFiles.keySet()) {
      final VirtualFilePointerContainer container = myExcludedFiles.get(typeId);
      for (String url : container.getUrls()) {
        state.getFiles().add(new ExcludedFileState(url, typeId));
      }
    }
    Collections.sort(state.getFiles(), new Comparator<ExcludedFileState>() {
      @Override
      public int compare(ExcludedFileState o1, ExcludedFileState o2) {
        return StringUtil.comparePairs(o1.getFrameworkType(), o1.getUrl(), o2.getFrameworkType(), o2.getUrl(), true);
      }
    });
    return state;
  }

  @Override @Nullable
  public ExcludesConfigurationState getState() {
    if (!myConverted) return null;
    return getActualState();
  }

  @Override
  public void loadState(@Nullable ExcludesConfigurationState state) {
    doLoadState(state);
    if (!myExcludedFiles.isEmpty() || !myExcludedFrameworks.isEmpty()) {
      markAsConverted();
    }
  }

  private void doLoadState(@Nullable ExcludesConfigurationState state) {
    myExcludedFrameworks.clear();
    for (VirtualFilePointerContainer container : myExcludedFiles.values()) {
      container.clear();
    }
    if (state != null) {
      myExcludedFrameworks.addAll(state.getFrameworkTypes());
      for (ExcludedFileState fileState : state.getFiles()) {
        myExcludedFiles.get(fileState.getFrameworkType()).add(fileState.getUrl());
      }
    }
  }

  @Override
  public void dispose() {
  }
}
