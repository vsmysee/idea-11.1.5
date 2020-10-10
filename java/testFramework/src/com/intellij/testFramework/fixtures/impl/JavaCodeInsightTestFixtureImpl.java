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
package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class JavaCodeInsightTestFixtureImpl extends CodeInsightTestFixtureImpl implements JavaCodeInsightTestFixture {
  public JavaCodeInsightTestFixtureImpl(IdeaProjectTestFixture projectFixture, TempDirTestFixture tempDirFixture) {
    super(projectFixture, tempDirFixture);
  }

  @Override
  public JavaPsiFacade getJavaFacade() {
    assertInitialized();
    return JavaPsiFacade.getInstance(getProject());
  }

  @Override
  public PsiClass addClass(@NotNull @NonNls final String classText) {
    assertInitialized();
    final PsiClass psiClass = addClass(getTempDirPath(), classText);
    final VirtualFile file = psiClass.getContainingFile().getVirtualFile();
    allowTreeAccessForFile(file);
    return psiClass;
  }

  private PsiClass addClass(@NonNls final String rootPath, @NotNull @NonNls final String classText) {
    final String qName =
      ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          final PsiClass aClass = ((PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("a.java", classText)).getClasses()[0];
          return aClass.getQualifiedName();
        }
      });
    assert qName != null;
    final PsiFile psiFile = new WriteCommandAction<PsiFile>(getProject()) {
      @Override
      protected void run(Result<PsiFile> result) throws Throwable {
        result.setResult(addFileToProject(rootPath, qName.replace('.', '/') + ".java", classText));
      }
    }.execute().getResultObject();
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
            public PsiClass compute() {
              return ((PsiJavaFile)psiFile).getClasses()[0];
            }
          });
  }

  @Override
  @NotNull
  public PsiClass findClass(@NotNull @NonNls final String name) {
    final PsiClass aClass = getJavaFacade().findClass(name, ProjectScope.getProjectScope(getProject()));
    assertNotNull("Class " + name + " not found", aClass);
    return aClass;
  }

  @Override
  @NotNull
  public PsiPackage findPackage(@NotNull @NonNls final String name) {
    final PsiPackage aPackage = getJavaFacade().findPackage(name);
    assertNotNull("Package " + name + " not found", aPackage);
    return aPackage;
  }

  @Override
  public void tearDown() throws Exception {
    ((PsiModificationTrackerImpl)getPsiManager().getModificationTracker()).incCounter();// drop all caches
    super.tearDown();
  }
}
