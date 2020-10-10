package org.jetbrains.plugins.groovy.lang.controlFlow;

import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author ven
 */
public class ControlFlowTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/controlFlow/";
  }

  public void testAssignment() throws Throwable { doTest(); }
  public void testClosure1() throws Throwable { doTest(); }
  public void testComplexAssign() throws Throwable { doTest(); }
  public void testFor1() throws Throwable { doTest(); }
  public void testForeach1() throws Throwable { doTest(); }
  public void testGrvy1497() throws Throwable { doTest(); }
  public void testIf1() throws Throwable { doTest(); }
  public void testMultipleAssignment() throws Throwable { doTest(); }
  public void testNested() throws Throwable { doTest(); }
  public void testReturn() throws Throwable { doTest(); }
  public void testSwitch1() throws Throwable { doTest(); }
  public void testSwitch2() throws Throwable { doTest(); }
  public void testSwitch3() throws Throwable { doTest(); }
  public void testSwitch4() throws Throwable { doTest(); }
  public void testSwitch5() throws Throwable { doTest(); }
  public void testThrow1() throws Throwable { doTest(); }
  public void testThrowInCatch() throws Throwable { doTest(); }
  public void testTry1() throws Throwable { doTest(); }
  public void testTry2() throws Throwable { doTest(); }
  public void testTry3() throws Throwable { doTest(); }
  public void testTry4() throws Throwable { doTest(); }
  public void testTry5() throws Throwable { doTest(); }
  public void testTry6() throws Throwable { doTest(); }
  public void testTry7() throws Throwable { doTest(); }
  public void testTry8() throws Throwable { doTest(); }
  public void testTry9() throws Throwable { doTest(); }
  public void testTry10() throws Throwable { doTest(); }
  public void testWhile1() throws Throwable { doTest(); }
  public void testWhile2() throws Throwable { doTest(); }
  public void testWhileNonConstant() throws Throwable { doTest(); }
  public void testIfInstanceofElse() throws Throwable { doTest(); }
  public void testReturnMapFromClosure() {doTest();}
  public void testSwitchInTryWithThrows() {doTest();}
  public void testSomeCatches() {doTest();}

  public void doTest() {
    final List<String> input = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, input.get(0));

    final GroovyFile file = (GroovyFile)myFixture.getFile();
    final SelectionModel model = myFixture.getEditor().getSelectionModel();
    final PsiElement start = file.findElementAt(model.hasSelection() ? model.getSelectionStart() : 0);
    final PsiElement end = file.findElementAt(model.hasSelection() ? model.getSelectionEnd() - 1 : file.getTextLength() - 1);
    final GrControlFlowOwner owner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), GrControlFlowOwner.class, false);
    final Instruction[] instructions = new ControlFlowBuilder(getProject()).buildControlFlow(owner);
    final String cf = ControlFlowUtils.dumpControlFlow(instructions);
    assertEquals(input.get(1).trim(), cf.trim());
  }

}
