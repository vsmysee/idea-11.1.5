/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve;


import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiIntersectionType
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType
import org.jetbrains.plugins.groovy.util.TestUtils

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING

/**
 * @author ven
 */
public class TypeInferenceTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "resolve/inference/";
  }

  public void testTryFinallyFlow() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("tryFinallyFlow/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertTrue(type instanceof PsiIntersectionType);
    final PsiType[] conjuncts = ((PsiIntersectionType) type).getConjuncts();
    assertEquals(conjuncts.length, 2);
  }

  public void testTryFinallyFlow1() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("tryFinallyFlow1/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertTrue(type.equalsToText("java.lang.Integer"));
  }

  public void testTryFinallyFlow2() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("tryFinallyFlow2/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertTrue(type.equalsToText("java.lang.Integer"));
  }

  public void testThrowVariable() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("throwVariable/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertEquals("java.lang.Exception", type.getCanonicalText());
  }

  public void testGrvy852() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("grvy852/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertEquals("java.lang.Object", type.getCanonicalText());
  }

  public void testGenericMethod() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("genericMethod/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertEquals("java.util.List<java.lang.String>", type.getCanonicalText());
  }

  public void testCircular() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("circular/A.groovy").getElement();
    assertNull(ref.getType());
  }

  public void  testCircular1() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("circular1/A.groovy").getElement();
    assertNull(ref.getType());
  }

  public void testClosure() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("closure/A.groovy").getElement();
    assertNotNull(ref.getType());
  }

  public void testClosure1() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("closure1/A.groovy").getElement();
    assertTrue(ref.getType().equalsToText("java.lang.Integer"));
  }

  public void testClosure2() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("closure2/A.groovy").getElement();
    assertTrue(ref.getType().equalsToText("java.lang.Integer"));
  }

  public void testGrvy1209() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("grvy1209/A.groovy").getElement();
    assertTrue(ref.getType().equalsToText("java.lang.String"));
  }

  public void testLeastUpperBoundClosureType() throws Exception {
    GrReferenceExpression ref= (GrReferenceExpression)configureByFile("leastUpperBoundClosureType/A.groovy").getElement();
    assertInstanceOf(ref.getType(), GrClosureType.class);
  }

  public void testJavaLangClassType() throws Exception {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("javaLangClassType/A.groovy").getElement();
    assertEquals("java.lang.String", ref.getType().getCanonicalText());
  }

  public void testGenericWildcard() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("genericWildcard/A.groovy").getElement();
    assertEquals("A<Base>", ref.getType().getCanonicalText());
  }

  public void testArrayLikeAccessWithIntSequence() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("arrayLikeAccessWithIntSequence/A.groovy").getElement();
    assertEquals("java.util.List<java.lang.Integer>", ref.getType().getCanonicalText());
  }

  public void testArrayAccess() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("arrayAccess/A.groovy");
    assertEquals(JAVA_LANG_STRING, ref.getType().getCanonicalText());
  }

  public void testReturnTypeByTailExpression() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("returnTypeByTailExpression/A.groovy");
    assertEquals(JAVA_LANG_STRING, ref.getType().getCanonicalText());
  }

  public void testParameterWithBuiltinType() {
    GrReferenceExpression refExpr = (GrReferenceExpression)configureByFile("parameterWithBuiltinType/A.groovy");
    assertEquals("java.lang.Integer", refExpr.getType().getCanonicalText());
  }

  public void testRawTypeInReturnExpression() {
    assertNotNull(resolve("A.groovy"));
  }

  public void testMethodCallInvokedOnArrayAccess() {
    final GrReferenceExpression reference = (GrReferenceExpression)configureByFile("A.groovy");
    assertEquals("java.lang.Integer", reference.getType().getCanonicalText());
  }

  private void assertTypeEquals(String expected, String fileName) {
    final PsiReference ref = configureByFile(getTestName(true) + "/" + fileName);
    assertInstanceOf(ref, GrReferenceExpression.class);
    final PsiType type = ((GrReferenceExpression)ref).getType();
    assertNotNull(type);
    assertEquals(expected, type.getCanonicalText());
  }

  public void testTypeOfGroupBy() {
    assertTypeEquals("java.util.Map<java.lang.Integer,java.util.List<java.lang.Integer>>", "A.groovy");
  }

  public void testConditionalExpressionWithNumericTypes() {
    assertTypeEquals("java.math.BigDecimal", "A.groovy");
  }

  public void testImplicitCallMethod() {
    assertEquals("java.lang.String", ((GrExpression)configureByFile("A.groovy")).getType().getCanonicalText());
  }

  public void testTupleWithNullInIt() {
    assertTypeEquals("java.util.ArrayList", "A.groovy");
  }

  public void testImplicitlyReturnedMethodCall() {
    assertTypeEquals("java.util.Map<BasicRange,java.util.Map<BasicRange,java.lang.Double>>", "A.groovy");
  }

  public void testInferWithClosureType() {
    assertTypeEquals("java.util.Date", "A.groovy");
  }

  public void testPlusEquals1() {
    assertTypeEquals("Test", "A.groovy");
  }

  public void testPlusEquals2() {
    assertTypeEquals("java.lang.String", "A.groovy");
  }

  public void testPlusEquals3() {
    assertTypeEquals("java.lang.String", "A.groovy");
  }
  public void testPlusEqualsClosure() {
    assertTypeEquals("java.lang.String", "A.groovy");
  }

  public void testGetAtClosure() {
    assertTypeEquals("java.lang.String", "A.groovy");
  }

  public void testPreferMethodOverloader() {
    assertTypeEquals("java.lang.String", "A.groovy");
  }

  public void testSafeInvocationInClassQualifier() {
    assertTypeEquals("java.lang.Class", "SafeInvocationInClassQualifier.groovy");
  }

  public void testReturnTypeFromMethodClosure() {
    assertTypeEquals("java.lang.String","A.groovy");
  }

  public void testNoSOF() {
    final PsiReference ref = configureByFile(getTestName(true) + "/A.groovy");
    assertInstanceOf(ref, GrReferenceExpression.class);
    final PsiType type = ((GrReferenceExpression)ref).getType();
    assertNull(type);
  }

  public void testTraditionalForVar() {
    assertTypeEquals(JAVA_LANG_INTEGER, "A.groovy");
  }

  public void testIncMethod() {
    assertTypeEquals(JAVA_LANG_INTEGER, "A.groovy");
  }

  public void testDGMFind() {
    assertTypeEquals("java.io.File", "a.groovy");
  }

  public void testMultiTypeParameter() {
    assertTypeEquals("X | Y", "a.groovy");
  }

  public void testTypeArgsInAccessor() {
    assertTypeEquals("Foo<java.lang.String>", "a.groovy");
  }

  public void testSingleParameterInStringInjection() {
    assertTypeEquals("java.lang.StringBuilder", "a.groovy");
  }

  void testIndexPropertyPlusAssigned() {
    GroovyFile file = myFixture.configureByText('a.groovy', '''
class X {
    def putAt(String s, X x){new Date()}

    def getAt(String s) {new X()}

    def plus(X x, int i) {x}
}

map = new X()

map['i'] += 2
''') as GroovyFile
    GrAssignmentExpression assignment = file.topStatements[2] as GrAssignmentExpression
    assertTrue(assignment.getLValue().getType().equalsToText(CommonClassNames.JAVA_UTIL_DATE))
  }

  void testAllTypeParamsAreSubstituted() {
    assertTypeEquals('java.util.Map', 'a.groovy')
  }

  void testDiamond() {
    GroovyFile file = myFixture.configureByText('a.groovy', '''
List<String> list = new ArrayList<>()
List<Integer> l2

(list, l2) = [new ArrayList<>(), new ArrayList<>()]
''') as GroovyFile

    def statements = file.topStatements

    assertEquals('java.util.ArrayList<java.lang.String>', (statements[0] as GrVariableDeclaration).variables[0].initializerGroovy.type.canonicalText)
    assertEquals('java.util.ArrayList<java.lang.String>', ((statements[2] as GrAssignmentExpression).RValue as GrListOrMap).initializers[0].type.canonicalText)
    assertEquals('java.util.ArrayList<java.lang.Integer>', ((statements[2] as GrAssignmentExpression).RValue as GrListOrMap).initializers[1].type.canonicalText)
  }

  void testWildCardsNormalized() {
    assertTypeEquals(Object.canonicalName, 'a.groovy')
  }
  
  void testIndexPropertyInLHS() {
    assertTypeEquals("java.util.Map", 'a.groovy')
  }
  
  void testEmptyMapTypeArgs() {
    myFixture.configureByText('a.groovy', '''
class X<A, B> implements Map<A, B> {}

X<String, Integer> x = [:]
''')
    
    def type = ((myFixture.file as GroovyFile).statements[0] as GrVariableDeclaration).variables[0].initializerGroovy.type
    assertEquals("java.util.Map<java.lang.String,java.lang.Integer>", type.canonicalText)
  }

  void testRawCollectionsInCasts() {
    def file = myFixture.configureByText('_a.groovy', '''\
String[] a = ["a"]
def b = a as ArrayList
def cc = b[0]
print c<caret>c''')
    def ref = (GrReferenceExpression)file.findReferenceAt(myFixture.editor.caretModel.offset)
    assertEquals(String.canonicalName, ref.type.canonicalText)
  }
}
