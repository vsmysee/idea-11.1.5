package org.jetbrains.plugins.groovy.lang;


import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
public class MissingReturnTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}highlighting/missingReturn";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyHighlightingTest.GROOVY_18_PROJECT_DESCRIPTOR;
  }

  public void testMissingReturnWithLastLoop() throws Throwable { doTest(); }
  public void testMissingReturnWithUnknownCall() throws Throwable { doTest(); }
  public void testMissingReturnWithIf() throws Throwable { doTest(); }
  public void testMissingReturnWithAssertion() throws Throwable { doTest(); }
  public void testMissingReturnThrowException() throws Throwable { doTest(); }
  public void testMissingReturnTryCatch() throws Throwable { doTest(); }
  public void testMissingReturnLastNull() throws Throwable { doTest(); }
  public void testMissingReturnImplicitReturns() throws Throwable {doTest();}
  public void testMissingReturnOvertReturnType() throws Throwable {doTest();}
  public void testMissingReturnFromClosure() throws Throwable {doTest();}
  public void testReturnsWithoutValue() throws Throwable {doTest();}
  public void testEndlessLoop() throws Throwable {doTest();}
  public void testEndlessLoop2() throws Throwable {doTest();}
  public void testExceptionWithFinally() throws Throwable {doTest();}
  public void testOnlyAssert() throws Throwable {doTest();}
  public void testImplicitReturnNull() throws Throwable {doTest();}
  public void testMissingReturnInClosure() {doTest();}
  public void testFinally() {doTest();}

  private void doTest() {
    myFixture.enableInspections(new MissingReturnInspection());
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".groovy");
  }

}
