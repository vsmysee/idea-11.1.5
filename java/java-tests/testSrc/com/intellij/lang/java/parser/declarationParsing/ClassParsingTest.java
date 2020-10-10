
package com.intellij.lang.java.parser.declarationParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


public class ClassParsingTest extends JavaParsingTestCase {
  public ClassParsingTest() {
    super("parser-full/declarationParsing/class");
  }

  public void testNoClass() { doTest(true); }
  public void testNoType() { doTest(true); }

  public void testSemicolon() { doTest(true); }
  public void testSemicolon2() { doTest(true); }
  public void testParametrizedClass() { doTest(true); }
  public void testPines() { doTest(true); }
  public void testForError() { doTest(true); }
  public void testEnum1() { doTest(true); }
  public void testEnum2() { doTest(true); }

  public void testEnumWithConstants1() { doTest(true); }
  public void testEnumWithConstants2() { doTest(true); }
  public void testEnumWithConstants3() { doTest(true); }
  public void testEnumWithConstants4() { doTest(true); }
  public void testEnumWithConstants5() { doTest(true); }
  public void testEnumWithConstants6() { doTest(true); }
  public void testEnumWithInitializedConstants() { doTest(true); }
  public void testEnumWithAnnotatedConstants() { doTest(true); }
  public void testEnumWithImport() { doTest(true); }
  public void testEnumWithoutConstants() { doTest(true); }
  public void testEmptyImportList() { doTest(true); }
  public void testLongClass() { doTest(false); }
  public void testIncompleteAnnotation() { doTest(true); }

  public void testExtraOpeningBraceInMethod() { doTest(true); }
  public void testExtraClosingBraceInMethod() { doTest(true); }

  public void testErrors0() { doTest(true); }
  public void testErrors1() { doTest(true); }
  public void testErrors2() { doTest(true); }
  public void testErrors3() { doTest(true); }

  public void testErrors4() {
    String ext = myFileExt;
    try {
      myFileExt = ext + ".txt"; // TODO this test produces OOME so we use nondefault extension, the test could be simplified once the fix is in place
      doTest(true);
    }
    finally {
      myFileExt = ext;
    }
  }
}
