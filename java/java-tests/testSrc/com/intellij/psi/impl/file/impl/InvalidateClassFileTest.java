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
package com.intellij.psi.impl.file.impl;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;

import java.io.File;

/**
 * @author ven
 */
public class InvalidateClassFileTest extends PsiTestCase {
  private File myRoot;

  private static final String BASE_PATH = "/psi/java/cls/";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRoot = createTempDirectory(false);
    myFilesToDelete.add(myRoot);
  }

  public void test1() throws Exception {
    String srcPath = JavaTestUtil.getJavaTestDataPath() + BASE_PATH + "Clazz.class";
    final File srcFile = new File(srcPath);
    final File dstFile = new File(myRoot, "Clazz.class");
    assertFalse(dstFile.exists());
    FileUtil.copy(srcFile, dstFile);

    final VirtualFile rootVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myRoot);

    assertNotNull(rootVFile);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        Module module = myModule;
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        final Library library = rootModel.getModuleLibraryTable().createLibrary();
        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        libraryModel.addRoot(rootVFile.getUrl(), OrderRootType.CLASSES);
        libraryModel.commit();
        rootModel.commit();
      }
    });

    PsiClass clazz = getJavaFacade().findClass("Clazz", GlobalSearchScope.allScope(myProject));
    assertNotNull(clazz);
    final boolean[] notified = new boolean[] {false};
    final PsiTreeChangeAdapter listener = new PsiTreeChangeAdapter() {
      @Override
      public void childRemoved(PsiTreeChangeEvent event) {
        notified[0] = true;
      }

      @Override
      public void childrenChanged(PsiTreeChangeEvent event) {
        notified[0] = true;
      }
    };
    getPsiManager().addPsiTreeChangeListener(listener);

    try {
      dstFile.setLastModified(System.currentTimeMillis());
      VirtualFileManager.getInstance().refresh(false);
      assertTrue("No event sent!", notified[0]);
    }
    finally {
      getPsiManager().removePsiTreeChangeListener(listener);
    }

    assertFalse(clazz.isValid());
    assertNotNull(getJavaFacade().findClass("Clazz", GlobalSearchScope.allScope(myProject)));
  }
}
