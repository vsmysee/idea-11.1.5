/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.resolve;


import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

/**
 * @author ven
 */
public abstract class GroovyResolveTestCase extends LightGroovyTestCase {
  @NonNls protected static final String MARKER = "<ref>";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (new File("$myFixture.testDataPath/${getTestName(true)}").exists()) {
      myFixture.copyDirectoryToProject(getTestName(true), "");
    }
  }

  protected PsiReference configureByFile(@NonNls String filePath, @Nullable String newFilePath) {
    filePath = StringUtil.trimStart(filePath, getTestName(true) + "/");
    final VirtualFile vFile = myFixture.tempDirFixture.getFile(filePath);
    assertNotNull("file $filePath not found", vFile);

    String fileText;
    try {
      fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    int offset = fileText.indexOf(MARKER);
    assertTrue("'ref' marker is not found", offset >= 0);
    fileText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length());

    if (newFilePath == null) {
      myFixture.configureByText("aaa." + vFile.extension, fileText);
    }
    else {
      myFixture.configureByText(newFilePath, fileText);
    }

    PsiReference ref = myFixture.file.findReferenceAt(offset);
    assertNotNull(ref);
    return ref;
  }

  protected PsiReference configureByFile(@NonNls String filePath) {
    return configureByFile(filePath, null);
  }

  protected PsiReference configureByText(String text) {
    configureByText('a.groovy', text);
  }

  protected PsiReference configureByText(String fileName, String text) {
    myFixture.configureByText fileName, text
    return myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
  }

  @Nullable
  protected PsiElement resolve(String fileName) {
    PsiReference ref = configureByFile(getTestName(true) + "/" + fileName);
    return ref.resolve();
  }

  @Nullable
  protected GroovyResolveResult advancedResolve(String fileName) {
    PsiReference ref = configureByFile(getTestName(true) + "/" + fileName);
    assertInstanceOf(ref, GrReferenceExpression.class);
    return ((GrReferenceExpression)ref).advancedResolve();
  }

}
