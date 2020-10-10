package com.intellij.lang.java.parser.annotationParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiRecursiveElementVisitor;

/**
 * @author ven
 */
public class AnnotationParsingTest extends JavaParsingTestCase {
  public AnnotationParsingTest() {
    super("parser-full/annotationParsing/annotation");
  }

  public void testMarker() { doTest(true); }
  public void testSimple1() { doTest(true); }
  public void testSimple2() { doTest(true); }
  public void testComplex() { doTest(true); }
  public void testMultiple() { doTest(true); }
  public void testArray() { doTest(true); }
  public void testNested() { doTest(true); }
  public void testParameterAnnotation () { doTest(true); }
  public void testPackageAnnotation () { doTest(true); }
  public void testParameterizedMethod () { doTest(true); }
  public void testQualifiedAnnotation() { doTest(true); }
  public void testEnumSmartTypeCompletion() { doTest(true); }

  public void testTypeAnno() {
    withLevel(LanguageLevel.JDK_1_8, new Runnable() { @Override public void run() {
      doTest(true);
      myFile.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitErrorElement(PsiErrorElement element) {
          fail(element.getErrorDescription());
          super.visitErrorElement(element);
        }
      });
    }});
  }

  public void testErrors() { doTest(true); }
}
