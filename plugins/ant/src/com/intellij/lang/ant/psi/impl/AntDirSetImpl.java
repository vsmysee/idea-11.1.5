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
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFilesProvider;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: May 4, 2007
 */
public class AntDirSetImpl extends AntFilesProviderImpl{
  public AntDirSetImpl(final AntElement parent, final XmlTag sourceElement) {
    super(parent, sourceElement);
  }

  public AntDirSetImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition, @NonNls final String nameElementAttribute) {
    super(parent, sourceElement, definition, nameElementAttribute);
  }

  public AntDirSetImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  @NotNull
  protected List<File> getFiles(final AntPattern pattern, final Set<AntFilesProvider> processed) {
    final File singleFile = getCanonicalFile(computeAttributeValue(getSourceElement().getAttributeValue("file")));
    if (singleFile == null || pattern.hasIncludePatterns()) {
      // if singleFile is specified, there are no implicit includes
      final File root = getCanonicalFile(computeAttributeValue(getSourceElement().getAttributeValue("dir")));
      if (root != null) {
        final ArrayList<File> files = new ArrayList<File>();
        if (singleFile != null && singleFile.isDirectory()) {
          files.add(singleFile);
        }
        new FilesCollector().collectFiles(files, root, "", pattern);
        return files;
      }
    }

    if (singleFile != null && singleFile.isDirectory()) {
      Collections.singletonList(singleFile);
    }
    
    return Collections.emptyList();
  }
  
  private static class FilesCollector {
    private static final int MAX_DIRS_TO_PROCESS = 100;
    private int myDirsProcessed = 0;
    
    public void collectFiles(List<File> container, File from, String relativePath, final AntPattern pattern) {
      if (myDirsProcessed > MAX_DIRS_TO_PROCESS) {
        return;
      }
      myDirsProcessed++;
      final File[] children = from.listFiles();
      if (children != null) {
        for (File child : children) {
          if (child.isDirectory()) {
            final String childPath = makePath(relativePath, child.getName());
            if (pattern.acceptPath(childPath)) {
              container.add(child);
            }
            if (pattern.couldBeIncluded(childPath)) {
              collectFiles(container, child, childPath, pattern);
            }
          }
        }
      }
    }

    private static String makePath(final String parentPath, final String name) {
      if (parentPath.length() == 0) {
        return name;
      }
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        return builder.append(parentPath).append("/").append(name).toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
    
  }

}
