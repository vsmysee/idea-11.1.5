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
package org.jetbrains.android.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPrecompileTask implements CompileTask {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidPrecompileTask");
  private static final Key<String> LIGHT_BUILD_KEY = Key.create(AndroidCommonUtils.LIGHT_BUILD_OPTION);

  @Override
  public boolean execute(CompileContext context) {
    checkAndroidDependencies(context);

    final Project project = context.getProject();
    
    ExcludedEntriesConfiguration configuration =
      ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(project)).getExcludedEntriesConfiguration();

    Set<ExcludeEntryDescription> addedEntries = new HashSet<ExcludeEntryDescription>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet == null) {
        continue;
      }

      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          AndroidCompileUtil.createGenModulesAndSourceRoots(facet);
        }
      }, indicator != null ? indicator.getModalityState() : ModalityState.NON_MODAL);

      if (context.isRebuild()) {
        clearResCache(facet, context);
      }

      final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
      final int platformToolsRevision = platform != null ? platform.getSdkData().getPlatformToolsRevision() : -1;

      LOG.debug("Platform-tools revision for module " + module.getName() + " is " + platformToolsRevision);

      if (facet.getConfiguration().LIBRARY_PROJECT) {
        if (platformToolsRevision >= 0 && platformToolsRevision <= 7) {
          LOG.debug("Excluded sources of module " + module.getName());
          excludeAllSourceRoots(module, configuration, addedEntries);
        }
        else {
          unexcludeAllSourceRoots(facet, configuration);
        }
      }
    }

    if (addedEntries.size() > 0) {
      LOG.debug("Files excluded by Android: " + addedEntries.size());
      CompilerManager.getInstance(project).addCompilationStatusListener(new MyCompilationStatusListener(project, addedEntries), project);
    }

    if (!AndroidCompileUtil.isFullBuild(context)) {
      context.getCompileScope().putUserData(LIGHT_BUILD_KEY, Boolean.toString(true));
    }
    return true;
  }

  private static void checkAndroidDependencies(@NotNull CompileContext context) {
    for (Module module : context.getCompileScope().getAffectedModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        continue;
      }

      final Pair<String, VirtualFile> manifestMergerProp =
        AndroidRootUtil.getProjectPropertyValue(module, AndroidUtils.ANDROID_MANIFEST_MERGER_PROPERTY);
      if (manifestMergerProp != null && Boolean.parseBoolean(manifestMergerProp.getFirst())) {
        context.addMessage(CompilerMessageCategory.WARNING,
                           "[" + module.getName() + "] " + AndroidBundle.message("android.manifest.merger.not.supported.error"),
                           manifestMergerProp.getSecond().getUrl(), -1, -1);
      }

      if (!facet.getConfiguration().LIBRARY_PROJECT) {

        for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
          if (entry instanceof ModuleOrderEntry) {
            final ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;

            if (moduleOrderEntry.getScope() == DependencyScope.COMPILE) {
              final Module depModule = moduleOrderEntry.getModule();

              if (depModule != null) {
                final AndroidFacet depFacet = AndroidFacet.getInstance(depModule);

                if (depFacet != null && !depFacet.getConfiguration().LIBRARY_PROJECT) {
                  String message = "Suspicious module dependency " +
                                   module.getName() +
                                   " -> " +
                                   depModule.getName() +
                                   ": Android application module depends on other application module. Possibly, you should ";
                  if (AndroidMavenUtil.isMavenizedModule(depModule)) {
                    message += "change packaging type of module " + depModule.getName() + " to 'apklib' in pom.xml file or ";
                  }
                  message += "change dependency scope to 'Provided'.";
                  context.addMessage(CompilerMessageCategory.WARNING, message, null, -1, -1);
                }
              }
            }
          }
        }
      }
    }
  }
  
  private static void clearResCache(@NotNull AndroidFacet facet, @NotNull CompileContext context) {
    final Module module = facet.getModule();

    final String dirPath = AndroidCompileUtil.findResourcesCacheDirectory(module, false, null);
    if (dirPath != null) {
      final File dir = new File(dirPath);
      if (dir.exists()) {
        FileUtil.delete(dir);
      }
    }
  }

  private static void unexcludeAllSourceRoots(AndroidFacet facet,
                                              ExcludedEntriesConfiguration configuration) {
    final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(facet.getModule()).getSourceRoots();
    final Set<VirtualFile> sourceRootSet = new HashSet<VirtualFile>();
    sourceRootSet.addAll(Arrays.asList(sourceRoots));

    final String aidlGenSourceRootPath = AndroidRootUtil.getAidlGenSourceRootPath(facet);
    if (aidlGenSourceRootPath != null) {
      final VirtualFile aidlGenSourceRoot = LocalFileSystem.getInstance().findFileByPath(aidlGenSourceRootPath);

      if (aidlGenSourceRoot != null) {
        sourceRootSet.remove(aidlGenSourceRoot);
      }
    }

    final String aptGenSourceRootPath = AndroidRootUtil.getAptGenSourceRootPath(facet);
    if (aptGenSourceRootPath != null) {
      final VirtualFile aptGenSourceRoot = LocalFileSystem.getInstance().findFileByPath(aptGenSourceRootPath);

      if (aptGenSourceRoot != null) {
        sourceRootSet.remove(aptGenSourceRoot);
      }
    }

    final VirtualFile rsGenRoot = AndroidRootUtil.getRenderscriptGenDir(facet);
    if (rsGenRoot != null) {
      sourceRootSet.remove(rsGenRoot);
    }

    final VirtualFile buildconfigGenDir = AndroidRootUtil.getBuildconfigGenDir(facet);
    if (buildconfigGenDir != null) {
      sourceRootSet.remove(buildconfigGenDir);
    }

    final ExcludeEntryDescription[] descriptions = configuration.getExcludeEntryDescriptions();
    configuration.removeAllExcludeEntryDescriptions();

    for (ExcludeEntryDescription description : descriptions) {
      final VirtualFile file = description.getVirtualFile();

      if (file == null || !sourceRootSet.contains(file)) {
        configuration.addExcludeEntryDescription(description);
      }
    }
  }

  private static void excludeAllSourceRoots(Module module,
                                            ExcludedEntriesConfiguration configuration,
                                            Collection<ExcludeEntryDescription> addedEntries) {
    Project project = module.getProject();
    VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();

    for (VirtualFile sourceRoot : sourceRoots) {
      ExcludeEntryDescription description = new ExcludeEntryDescription(sourceRoot, true, false, project);

      if (!configuration.containsExcludeEntryDescription(description)) {
        configuration.addExcludeEntryDescription(description);
        addedEntries.add(description);
      }
    }
  }

  private static class MyCompilationStatusListener extends CompilationStatusAdapter {
    private final Project myProject;
    private final Set<ExcludeEntryDescription> myEntriesToRemove;

    public MyCompilationStatusListener(Project project, Set<ExcludeEntryDescription> entriesToRemove) {
      myProject = project;
      myEntriesToRemove = entriesToRemove;
    }

    @Override
    public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
      CompilerManager.getInstance(myProject).removeCompilationStatusListener(this);

      ExcludedEntriesConfiguration configuration =
        ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject)).getExcludedEntriesConfiguration();
      ExcludeEntryDescription[] descriptions = configuration.getExcludeEntryDescriptions();

      configuration.removeAllExcludeEntryDescriptions();

      for (ExcludeEntryDescription description : descriptions) {
        if (!myEntriesToRemove.contains(description)) {
          configuration.addExcludeEntryDescription(description);
        }
      }
    }
  }
}
