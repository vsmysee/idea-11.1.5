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

package com.intellij.openapi.module.impl;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.components.impl.ModulePathMacroManager;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.IModuleStore;
import com.intellij.openapi.components.impl.stores.ModuleStoreImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependentsScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.StripedLockIntObjectConcurrentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author max
 */
public class ModuleImpl extends ComponentManagerImpl implements Module {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModuleImpl");

  @NotNull private final Project myProject;
  private boolean isModuleAdded;

  @NonNls private static final String OPTION_WORKSPACE = "workspace";

  private final StripedLockIntObjectConcurrentHashMap<GlobalSearchScope> myScopeCache = new StripedLockIntObjectConcurrentHashMap<GlobalSearchScope>();

  private GlobalSearchScope myModuleWithDependentsScope;
  private GlobalSearchScope myModuleTestsWithDependentsScope;

  public static final Object MODULE_RENAMING_REQUESTOR = new Object();

  private String myName;

  private IModuleStore myComponentStore;

  public ModuleImpl(String filePath, Project project) {
    super(project);

    getPicoContainer().registerComponentInstance(Module.class, this);

    myProject = project;

    init(filePath);
  }

  protected void bootstrapPicoContainer() {
    Extensions.instantiateArea(PluginManager.AREA_IDEA_MODULE, this, (AreaInstance)getParentComponentManager());
    super.bootstrapPicoContainer();
    getPicoContainer().registerComponentImplementation(IComponentStore.class, ModuleStoreImpl.class);
    getPicoContainer().registerComponentImplementation(ModulePathMacroManager.class);
  }

  @NotNull
  public synchronized IModuleStore getStateStore() {
    if (myComponentStore == null) {
      myComponentStore = (IModuleStore)getPicoContainer().getComponentInstance(IComponentStore.class);
    }
    return myComponentStore;
  }

  @Override
  public void initializeComponent(Object component, boolean service) {
    getStateStore().initComponent(component, service);
  }

  private void init(String filePath) {
    getStateStore().setModuleFilePath(filePath);
    myName = moduleNameByFileName(PathUtil.getFileName(filePath));

    MyVirtualFileListener myVirtualFileListener = new MyVirtualFileListener();
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener, this);
  }

  public void loadModuleComponents() {
    final IdeaPluginDescriptor[] plugins = PluginManager.getPlugins();
    for (IdeaPluginDescriptor plugin : plugins) {
      if (PluginManager.shouldSkipPlugin(plugin)) continue;
      loadComponentsConfiguration(plugin.getModuleComponents(), plugin, false);
    }
  }

  protected boolean isComponentSuitable(Map<String, String> options) {
    if (!super.isComponentSuitable(options)) return false;
    if (options == null) return true;

    Set<String> optionNames = options.keySet();
    for (String optionName : optionNames) {
      if (Comparing.equal(OPTION_WORKSPACE, optionName)) continue;
      if (!parseOptionValue(options.get(optionName)).contains(getOptionValue(optionName))) return false;
    }

    return true;
  }

  private static List<String> parseOptionValue(String optionValue) {
    if (optionValue == null) return new ArrayList<String>(0);
    return Arrays.asList(optionValue.split(";"));
  }

  @Nullable
  public VirtualFile getModuleFile() {
    return getStateStore().getModuleFile();
  }

  void rename(String newName) {
    myName = newName;
    final VirtualFile file = getStateStore().getModuleFile();
    try {
      if (file != null) {
        ClasspathStorage.moduleRenamed(this, newName);
        file.rename(MODULE_RENAMING_REQUESTOR, newName + ModuleFileType.DOT_DEFAULT_EXTENSION);
        getStateStore().setModuleFilePath(VfsUtil.virtualToIoFile(file).getCanonicalPath());
        return;
      }

      // [dsl] we get here if either old file didn't exist or renaming failed
      final File oldFile = new File(getModuleFilePath());
      final File parentFile = oldFile.getParentFile();

      final File newFile = new File(parentFile, newName + ModuleFileType.DOT_DEFAULT_EXTENSION);
      getStateStore().setModuleFilePath(newFile.getCanonicalPath());
    }
    catch (IOException e) {
      LOG.debug(e);
    }
  }

  @NotNull
  public String getModuleFilePath() {
    return getStateStore().getModuleFilePath();
  }

  public synchronized void dispose() {
    isModuleAdded = false;
    disposeComponents();
    Extensions.disposeArea(this);
    myComponentStore = null;
    super.dispose();
  }


  public void projectOpened() {
    for (ModuleComponent component : getComponents(ModuleComponent.class)) {
      try {
        component.projectOpened();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public void projectClosed() {
    List<ModuleComponent> components = new ArrayList<ModuleComponent>(Arrays.asList(getComponents(ModuleComponent.class)));
    Collections.reverse(components);

    for (ModuleComponent component : components) {
      try {
        component.projectClosed();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public boolean isLoaded() {
    return isModuleAdded;
  }

  public void moduleAdded() {
    isModuleAdded = true;
    for (ModuleComponent component : getComponents(ModuleComponent.class)) {
      component.moduleAdded();
    }
  }

  public void setOption(@NotNull String optionName, @NotNull String optionValue) {
    getStateStore().setOption(optionName, optionValue);
  }

  public void clearOption(@NotNull String optionName) {
    getStateStore().clearOption(optionName);
  }

  public String getOptionValue(@NotNull String optionName) {
    return getStateStore().getOptionValue(optionName);
  }

  public GlobalSearchScope getModuleScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE | ModuleWithDependenciesScope.TESTS);
  }

  @Override
  public GlobalSearchScope getModuleScope(boolean includeTests) {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
  }

  public GlobalSearchScope getModuleWithLibrariesScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE | ModuleWithDependenciesScope.TESTS | ModuleWithDependenciesScope.LIBRARIES);
  }

  public GlobalSearchScope getModuleWithDependenciesScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE | ModuleWithDependenciesScope.TESTS | ModuleWithDependenciesScope.MODULES);
  }

  @Override
  public GlobalSearchScope getModuleContentScope() {
    return getCachedScope(ModuleWithDependenciesScope.CONTENT);
  }

  @Override
  public GlobalSearchScope getModuleContentWithDependenciesScope() {
    return getCachedScope(ModuleWithDependenciesScope.CONTENT | ModuleWithDependenciesScope.MODULES);
  }

  public GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests) {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE |
                          ModuleWithDependenciesScope.MODULES |
                          ModuleWithDependenciesScope.LIBRARIES | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
  }

  public GlobalSearchScope getModuleWithDependentsScope() {
    if (myModuleWithDependentsScope == null) {
      myModuleWithDependentsScope = new ModuleWithDependentsScope(this, false);
    }
    return myModuleWithDependentsScope;
  }

  public GlobalSearchScope getModuleTestsWithDependentsScope() {
    if (myModuleTestsWithDependentsScope == null) {
      myModuleTestsWithDependentsScope = new ModuleWithDependentsScope(this, true);
    }
    return myModuleTestsWithDependentsScope;
  }

  public GlobalSearchScope getModuleRuntimeScope(boolean includeTests) {
    return getCachedScope(
      ModuleWithDependenciesScope.MODULES | ModuleWithDependenciesScope.LIBRARIES | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
  }

  @NotNull
  public GlobalSearchScope getCachedScope(@ModuleWithDependenciesScope.ScopeConstant int options) {
    GlobalSearchScope scope = myScopeCache.get(options);
    if (scope == null) {
      scope = new ModuleWithDependenciesScope(this, options);
      myScopeCache.put(options, scope);
    }
    return scope;
  }

  public void clearScopesCache() {
    myScopeCache.clear();
    myModuleWithDependentsScope = null;
    myModuleTestsWithDependentsScope = null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    if (myName == null) return "Module (not initialized)";
    return "Module: '" + getName() + "' " +(isDisposed() ? "(Disposed)" : "path: '" + getModuleFilePath() + "'");
  }

  private static String moduleNameByFileName(@NotNull String fileName) {
    return StringUtil.trimEnd(fileName, ModuleFileType.DOT_DEFAULT_EXTENSION);
  }

  public <T> T[] getExtensions(final ExtensionPointName<T> extensionPointName) {
    return Extensions.getArea(this).getExtensionPoint(extensionPointName).getExtensions();
  }

  @Override
  protected boolean logSlowComponents() {
    return super.logSlowComponents() || ApplicationInfoImpl.getShadowInstance().isEAP();
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {
    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (!isModuleAdded) return;
      final Object requestor = event.getRequestor();
      if (MODULE_RENAMING_REQUESTOR.equals(requestor)) return;
      if (!VirtualFile.PROP_NAME.equals(event.getPropertyName())) return;

      final VirtualFile parent = event.getParent();
      if (parent != null) {
        final String parentPath = parent.getPath();
        final String ancestorPath = parentPath + "/" + event.getOldValue();
        final String moduleFilePath = getModuleFilePath();
        if (VfsUtil.isAncestor(new File(ancestorPath), new File(moduleFilePath), true)) {
          final String newValue = (String)event.getNewValue();
          final String relativePath = FileUtil.getRelativePath(ancestorPath, moduleFilePath, '/');
          final String newFilePath = parentPath + "/" + newValue + "/" + relativePath;
          setModuleFilePath(moduleFilePath, newFilePath);
        }
      }

      final VirtualFile moduleFile = getModuleFile();
      if (moduleFile == null) return;
      if (moduleFile.equals(event.getFile())) {
        myName = moduleNameByFileName(moduleFile.getName());
        ModuleManagerImpl.getInstanceImpl(getProject()).fireModuleRenamedByVfsEvent(ModuleImpl.this);
      }
    }

    private void setModuleFilePath(String moduleFilePath, String newFilePath) {
      ClasspathStorage.modulePathChanged(ModuleImpl.this, newFilePath);

      final ModifiableModuleModel modifiableModel = ModuleManagerImpl.getInstanceImpl(getProject()).getModifiableModel();
      modifiableModel.setModuleFilePath(ModuleImpl.this, moduleFilePath, newFilePath);
      modifiableModel.commit();

      getStateStore().setModuleFilePath(newFilePath);
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
      final VirtualFile oldParent = event.getOldParent();
      final VirtualFile newParent = event.getNewParent();
      final String dirName = event.getFileName();
      final String ancestorPath = oldParent.getPath() + "/" + dirName;
      final String moduleFilePath = getModuleFilePath();
      if (VfsUtil.isAncestor(new File(ancestorPath), new File(moduleFilePath), true)) {
        final String relativePath = FileUtil.getRelativePath(ancestorPath, moduleFilePath, '/');
        setModuleFilePath(moduleFilePath, newParent.getPath() + "/" + dirName + "/" + relativePath);
      }
    }
  }

  protected MutablePicoContainer createPicoContainer() {
    return Extensions.getArea(this).getPicoContainer();
  }
}
