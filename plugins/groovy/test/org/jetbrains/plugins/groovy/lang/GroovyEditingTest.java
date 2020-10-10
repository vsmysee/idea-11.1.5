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
package org.jetbrains.plugins.groovy.lang;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author peter
 */
public class GroovyEditingTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "editing/";
  }

  private void doTest(final char c) throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.type(c);
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testCodeBlockRightBrace() throws Throwable { doTest('{'); }
  public void testInterpolationInsideStringRightBrace() throws Throwable { doTest('{'); }
  public void testStructuralInterpolationInsideStringRightBrace() throws Throwable { doTest('{'); }
  public void testEnterInMultilineString() throws Throwable { doTest('\n'); }
  public void testEnterInStringInRefExpr() throws Throwable {doTest('\n');}
  public void testEnterInGStringInRefExpr() throws Throwable {doTest('\n');}
  public void testPairAngleBracketAfterClassName() throws Throwable {doTest('<');}
  public void testPairAngleBracketAfterClassNameOvertype() throws Throwable {doTest('>');}
  public void testPairAngleBracketAfterClassNameBackspace() throws Throwable {doTest('\b');}
  public void testNoPairLess() throws Throwable {doTest('<');}
}