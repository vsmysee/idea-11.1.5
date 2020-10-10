/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
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
 */

package org.jetbrains.plugins.groovy.intentions;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class ConvertConcatenationToGstringTest extends GrIntentionTestCase {

  private static final String CONVERT_TO_GSTRING = "Convert to GString";

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
        final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("GROOVY").getModifiableModel();
        final VirtualFile groovyJar = JarFileSystem.getInstance().refreshAndFindFileByPath(TestUtils.getMockGroovy1_7LibraryName() + "!/");
        modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES);
        modifiableModel.commit();
      }
    };
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/convertConcatenationToGstring/";
  }

  public void testSimpleCase() throws Exception {
    doTest(CONVERT_TO_GSTRING, true);
  }

  public void testVeryComplicatedCase() throws Exception {
    doTest(CONVERT_TO_GSTRING, true);
  }

  public void testQuotes() throws Exception {
    doTest(CONVERT_TO_GSTRING, true);
  }

  public void testQuotes2() throws Exception {
    doTest(CONVERT_TO_GSTRING, true);
  }

  public void testQuotesInMultilineString() throws Exception {
    doTest(CONVERT_TO_GSTRING, true);
  }
  
  public void testDot() {
    doTest(CONVERT_TO_GSTRING, true);
  }
}
