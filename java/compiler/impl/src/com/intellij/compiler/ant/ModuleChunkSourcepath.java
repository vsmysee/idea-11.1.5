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
package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.*;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 22, 2004
 */
public class ModuleChunkSourcepath extends CompositeGenerator{
  private final VirtualFile[] mySourceRoots;
  private final VirtualFile[] myTestSourceRoots;

  public ModuleChunkSourcepath(final Project project, ModuleChunk chunk, final GenerationOptions genOptions) {
    final Path sourcepath = new Path(BuildProperties.getSourcepathProperty(chunk.getName()));
    final Path testSourcepath = new Path(BuildProperties.getTestSourcepathProperty(chunk.getName()));
    final PatternSet excludedFromCompilation = new PatternSet(BuildProperties.getExcludedFromCompilationProperty(chunk.getName()));
    final String moduleChunkBasedirProperty = BuildProperties.getModuleChunkBasedirProperty(chunk);
    final Module[] modules = chunk.getModules();

    if (CompilerExcludes.isAvailable(project)) {
      excludedFromCompilation.add(new PatternSetRef(BuildProperties.PROPERTY_COMPILER_EXCLUDES));
    }

    final List<VirtualFile> sourceRootFiles = new ArrayList<VirtualFile>();
    final List<VirtualFile> testSourceRootFiles = new ArrayList<VirtualFile>();

    for (final Module module : modules) {
      final String moduleName = module.getName();
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      final ModuleFileIndex moduleFileIndex = rootManager.getFileIndex();


      final PatternSet excludedFromModule = new PatternSet(BuildProperties.getExcludedFromModuleProperty(moduleName));
      excludedFromModule.add(new PatternSetRef(BuildProperties.PROPERTY_IGNORED_FILES));

      final ContentEntry[] contentEntries = rootManager.getContentEntries();
      for (final ContentEntry contentEntry : contentEntries) {
        final VirtualFile file = contentEntry.getFile();
        if (file == null) {
          continue; // filter invalid entries
        }
        if (!file.isInLocalFileSystem()) {
          continue; // skip content roots inside jar and zip archives
        }
        final VirtualFile dirSetRoot = getDirSetRoot(contentEntry);

        final String dirSetRootRelativeToBasedir = GenerationUtils
          .toRelativePath(dirSetRoot, chunk.getBaseDir(), moduleChunkBasedirProperty, genOptions);
        final DirSet sourcesDirSet = new DirSet(dirSetRootRelativeToBasedir);
        final DirSet testSourcesDirSet = new DirSet(dirSetRootRelativeToBasedir);

        final VirtualFile[] sourceRoots = contentEntry.getSourceFolderFiles();
        for (final VirtualFile root : sourceRoots) {
          if (!moduleFileIndex.isInContent(root)) {
            continue; // skip library sources
          }

          addExcludePatterns(module, root, root, excludedFromModule, true);

          final Include include = new Include(VfsUtilCore.getRelativePath(root, dirSetRoot, '/'));
          if (moduleFileIndex.isInTestSourceContent(root)) {
            testSourcesDirSet.add(include);
            testSourceRootFiles.add(root);
          }
          else {
            sourcesDirSet.add(include);
            sourceRootFiles.add(root);
          }
        }
        if (sourcesDirSet.getGeneratorCount() > 0) {
          sourcepath.add(sourcesDirSet);
        }
        if (testSourcesDirSet.getGeneratorCount() > 0) {
          testSourcepath.add(testSourcesDirSet);
        }
      }

      if (excludedFromModule.getGeneratorCount() > 0) {
        add(excludedFromModule);
        excludedFromCompilation.add(new PatternSetRef(BuildProperties.getExcludedFromModuleProperty(moduleName)));
      }
    }

    mySourceRoots = VfsUtil.toVirtualFileArray(sourceRootFiles);
    myTestSourceRoots = VfsUtil.toVirtualFileArray(testSourceRootFiles);

    if (excludedFromCompilation.getGeneratorCount() > 0) {
      add(excludedFromCompilation, 1);
    }
    if (sourcepath.getGeneratorCount() > 0) {
      add(sourcepath, 1);
    }
    if (testSourcepath.getGeneratorCount() != 0) {
      add(testSourcepath, 1);
    }
  }

  public VirtualFile[] getSourceRoots() {
    return mySourceRoots;
  }

  public VirtualFile[] getTestSourceRoots() {
    return myTestSourceRoots;
  }

  private VirtualFile getDirSetRoot(final ContentEntry contentEntry) {
    final VirtualFile contentRoot = contentEntry.getFile();
    final VirtualFile[] sourceFolderFiles = contentEntry.getSourceFolderFiles();
    for (VirtualFile sourceFolderFile : sourceFolderFiles) {
      if (contentRoot.equals(sourceFolderFile)) {
        return contentRoot.getParent();
      }
    }
    return contentRoot;
  }

  private void addExcludePatterns(Module module, final VirtualFile root, VirtualFile dir, CompositeGenerator generator, final boolean parentIncluded) {
    if (FileTypeManager.getInstance().isFileIgnored(dir)) {
      // ignored files are handled by global 'ignored' patternset
      return;
    }
    final boolean isIncluded = ModuleRootManager.getInstance(module).getFileIndex().isInContent(dir);
    if (isIncluded != parentIncluded) {
      final String relativePath = VfsUtilCore.getRelativePath(dir, root, '/');
      if (isIncluded) {
        generator.add(new Include(relativePath + "/**"));
      }
      else {
        if (!isExcludedByDefault(dir.getName())) {
          generator.add(new Exclude(relativePath + "/**"));
        }
      }
    }
    final VirtualFile[] children = dir.getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        addExcludePatterns(module, root, child, generator, isIncluded);
      }
    }
  }

  private boolean isExcludedByDefault(String name) {
    //noinspection HardCodedStringLiteral
    return "CVS".equals(name) || "SCCS".equals(name) || ".DS_Store".equals(name);
  }
}
