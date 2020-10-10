/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang;


import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.siyeh.ig.junit.JUnitAbstractTestClassNamingConventionInspection
import com.siyeh.ig.junit.JUnitTestClassNamingConventionInspection
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyResultOfAssignmentUsedInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyUncheckedAssignmentOfMemberOfRawTypeInspection
import org.jetbrains.plugins.groovy.codeInspection.confusing.ClashingGettersInspection
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyOctalIntegerInspection
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyResultOfIncrementOrDecrementUsedInspection
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialConditionalInspection
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialIfInspection
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyUnnecessaryReturnInspection
import org.jetbrains.plugins.groovy.codeInspection.metrics.GroovyOverlyLongMethodInspection
import org.jetbrains.plugins.groovy.codeInspection.unassignedVariable.UnassignedVariableAccessInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUntypedAccessInspection
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection
import org.jetbrains.plugins.groovy.util.TestUtils
import org.jetbrains.plugins.groovy.codeInspection.bugs.*

/**
 * @author peter
 */
public class GroovyHighlightingTest extends LightCodeInsightFixtureTestCase {
  public static final DefaultLightProjectDescriptor GROOVY_18_PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("GROOVY").getModifiableModel();
      final VirtualFile groovyJar = JarFileSystem.getInstance().refreshAndFindFileByPath(TestUtils.getMockGroovy1_8LibraryName()+"!/");
      assertTrue(groovyJar != null);
      modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES);
      modifiableModel.commit();
    }
  };

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "highlighting/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GROOVY_18_PROJECT_DESCRIPTOR;
  }

  public void testDuplicateClosurePrivateVariable() throws Throwable {
    doTest();
  }

  public void testClosureRedefiningVariable() throws Throwable {
    doTest();
  }

  private void doTest(InspectionProfileEntry... tools) {
    myFixture.enableInspections(tools);
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".groovy");
  }

  public void testCircularInheritance() throws Throwable {
    doTest();
  }

  public void testEmptyTupleType() throws Throwable {
    doTest();
  }

  public void testMapDeclaration() throws Throwable {
    doTest();
  }

  public void testShouldntImplementGroovyObjectMethods() throws Throwable {
    addGroovyObject();
    myFixture.addFileToProject("Foo.groovy", "class Foo {}");
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }

  public void testJavaClassImplementingGroovyInterface() throws Throwable {
    addGroovyObject();
    myFixture.addFileToProject("Foo.groovy", "interface Foo {}");
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }

  private void addGroovyObject() throws IOException {
    myFixture.addClass("package groovy.lang;" +
                       "public interface GroovyObject  {\n" +
                       "    java.lang.Object invokeMethod(java.lang.String s, java.lang.Object o);\n" +
                       "    java.lang.Object getProperty(java.lang.String s);\n" +
                       "    void setProperty(java.lang.String s, java.lang.Object o);\n" +
                       "    groovy.lang.MetaClass getMetaClass();\n" +
                       "    void setMetaClass(groovy.lang.MetaClass metaClass);\n" +
                       "}");
  }

  public void testDuplicateFields() throws Throwable {
    doTest();
  }

  public void testNoDuplicationThroughClosureBorder() throws Throwable {
    myFixture.addClass("package groovy.lang; public interface Closure {}");
    doTest();
  }

  public void testRecursiveMethodTypeInference() throws Throwable {
    doTest();
  }

  public void testSuperClassNotExists() throws Exception {
    doTest();
  }
  public void testDontSimplifyString() throws Throwable { doTest(new GroovyTrivialIfInspection(), new GroovyTrivialConditionalInspection()); }

  public void testRawMethodAccess() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawFieldAccess() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawArrayStyleAccess() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawArrayStyleAccessToMap() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawArrayStyleAccessToList() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testIncompatibleTypesAssignments() throws Throwable { doTest(new GroovyAssignabilityCheckInspection()); }

  public void testAnonymousClassConstructor() throws Throwable {doTest();}
  public void testAnonymousClassAbstractMethod() throws Throwable {doTest();}
  public void testAnonymousClassStaticMethod() throws Throwable {doTest();}
  public void testAnonymousClassShoudImplementMethods() throws Throwable {doTest();}
  public void testAnonymousClassShouldImplementSubstitutedMethod() throws Exception {doTest();}

  public void testDefaultMapConstructorNamedArgs() throws Throwable {
    doTest(new GroovyConstructorNamedArgumentsInspection(), new GroovyAssignabilityCheckInspection());
  }
  public void testDefaultMapConstructorNamedArgsError() throws Throwable {
    doTest(new GroovyConstructorNamedArgumentsInspection(), new GroovyAssignabilityCheckInspection());
  }
  public void testDefaultMapConstructorWhenDefConstructorExists() throws Throwable {
    doTest(new GroovyConstructorNamedArgumentsInspection(), new GroovyAssignabilityCheckInspection());
  }

  public void testSingleAllocationInClosure() throws Throwable {doTest(new GroovyResultOfObjectAllocationIgnoredInspection());}
  public void testUnusedAllocationInClosure() throws Throwable {doTest(new GroovyResultOfObjectAllocationIgnoredInspection());}

  public void testUnresolvedLhsAssignment() throws Throwable { doTest(new GroovyUnresolvedAccessInspection()); }

  public void testUnresolvedMethodCallWithTwoDeclarations() throws Throwable{
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testUnresolvedAccess() throws Exception { doTest(new GroovyUnresolvedAccessInspection()); }
  public void testBooleanProperties() throws Exception { doTest(new GroovyUnresolvedAccessInspection()); }
  public void testUntypedAccess() throws Exception { doTest(new GroovyUntypedAccessInspection()); }

  public void testUnassigned1() throws Exception { doTest(new UnassignedVariableAccessInspection()); }
  public void testUnassigned2() throws Exception { doTest(new UnassignedVariableAccessInspection()); }
  public void testUnassigned3() throws Exception { doTest(new UnassignedVariableAccessInspection()); }
  public void testUnassignedTryFinally() throws Exception { doTest(new UnassignedVariableAccessInspection()); }

  public void testUnusedVariable() throws Exception { doTest(new UnusedDefInspection()); }
  public void testDefinitionUsedInClosure() throws Exception { doTest(new UnusedDefInspection()); }
  public void testDefinitionUsedInClosure2() throws Exception { doTest(new UnusedDefInspection()); }
  public void testDefinitionUsedInSwitchCase() throws Exception { doTest(new UnusedDefInspection()); }
  public void testDuplicateInnerClass() throws Throwable{doTest();}

  public void testThisInStaticContext() throws Throwable {doTest();}
  public void testLocalVariableInStaticContext() throws Exception {doTest();}

  public void testModifiersInPackageAndImportStatements() throws Throwable {
    myFixture.copyFileToProject(getTestName(false) + ".groovy", "x/"+getTestName(false)+".groovy");
    myFixture.testHighlighting(true, false, false, "x/"+getTestName(false)+".groovy");
  }

  public void testBreakOutside() throws Exception {doTest();}
  public void testUndefinedLabel() throws Exception {doTest();}
  public void testUsedLabel() throws Exception {doTest(new GroovyLabeledStatementInspection());}

  public void testNestedMethods() throws Throwable {
    doTest();
  }

  public void testRawOverridedMethod() throws Exception {doTest();}

  public void testFQNJavaClassesUsages() throws Exception {
    doTest();
  }

  public void testGstringAssignableToString() throws Exception {doTest();}
  public void testGstringAssignableToStringInClosureParameter() throws Exception{doTest();}
  public void testEverythingAssignableToString() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}

  public void testEachOverRange() throws Exception {doTest();}

  public void testEllipsisParam() {
    myFixture.configureByText('a.groovy', '''\
class A {
  def foo(int... x){}
  def foo(<error descr="Ellipsis type is not allowed here">int...</error> x, double y) {}
}
''')
    myFixture.checkHighlighting(true, false, false)
  }

  public void testMethodCallWithDefaultParameters() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}
  public void testClosureWithDefaultParameters() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}
  public void testClosureCallMethodWithInapplicableArguments() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}
  public void testCallIsNotApplicable() {doTest(new GroovyAssignabilityCheckInspection());}
  public void testPathCallIsNotApplicable() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testOverlyLongMethodInspection() throws Exception {
    doTest(new GroovyOverlyLongMethodInspection());
  }

  public void testStringAndGStringUpperBound() throws Exception {doTest();}

  public void testWithMethod() throws Exception {doTest();}
  public void testByteArrayArgument() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}

  public void testForLoopWithNestedEndlessLoop() throws Exception {doTest(new UnassignedVariableAccessInspection());}
  public void testPrefixIncrementCfa() throws Exception {doTest(new UnusedDefInspection());}
  public void testIfIncrementElseReturn() throws Exception {doTest(new UnusedDefInspection()); }

  public void testArrayLikeAccess() throws Exception {doTest();}

  public void testSetInitializing() throws Exception {doTest();}

  public void testEmptyTupleAssignability() throws Exception {doTest();}

  public void testGrDefFieldsArePrivateInJavaCode() throws Exception {
    myFixture.configureByText("X.groovy", "public class X{def x=5}");
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testSuperConstructorInvocation() throws Exception {doTest();}

  public void testDuplicateMapKeys() throws Exception {doTest();}

  public void testIndexPropertyAccess() throws Exception {doTest();}

  public void testPropertyAndFieldDeclaration() throws Exception {doTest();}

  public void testGenericsMethodUsage() throws Exception {doTest();}

  public void testWildcardInExtendsList() throws Exception {doTest();}

  public void testOverrideAnnotation() throws Exception {doTest();}

  public void testClosureCallWithTupleTypeArgument() throws Exception {doTest();}

  public void testMethodDuplicates() throws Exception {doTest();}

  public void testPutValueToEmptyMap() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}
  public void testPutIncorrectValueToMap() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}

  public void testAmbiguousCodeBlock() throws Exception {doTest();}
  public void testAmbiguousCodeBlockInMethodCall() throws Exception {doTest();}
  public void testNotAmbiguousClosableBlock() throws Exception {doTest();}
  public void testDuplicateParameterInClosableBlock() throws Exception {doTest();}

  public void testCyclicInheritance() throws Exception {doTest();}

  public void testNoDefaultConstructor() throws Exception {doTest();}

  public void testTupleTypeAssignments() throws Exception{doTest(new GroovyAssignabilityCheckInspection());}

  public void testInaccessibleConstructorCall() {
    doTest(new GroovyAccessibilityInspection());
  }

  public void testSignatureIsNotApplicableToList() throws Exception {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testInheritConstructorsAnnotation() throws Exception {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testCollectionAssignments() {doTest(new GroovyAssignabilityCheckInspection()); }
  public void testReturnAssignability() {doTest(new GroovyAssignabilityCheckInspection()); }

  public void testNumberDuplicatesInMaps() throws Exception {doTest();}

  public void testMapNotAcceptedAsStringParameter()  {doTest(new GroovyAssignabilityCheckInspection());}

  public void testBuiltInTypeInstantiation() {doTest();}

  public void testSwitchControlFlow() {doTest(new UnusedDefInspection(), new GroovyResultOfAssignmentUsedInspection());}

  public void testRawTypeInAssignment() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testSOEInFieldDeclarations() {doTest();}

  public void testVeryLongDfaWithComplexGenerics() {
    IdeaTestUtil.assertTiming("", 10000, 1, new Runnable() {
      @Override
      public void run() {
        doTest(new GroovyAssignabilityCheckInspection(), new UnusedDefInspection());
      }
    });
  }

  public void testWrongAnnotation() {doTest();}

  public void testAmbiguousMethods() {
    myFixture.copyFileToProject(getTestName(false) + ".java");
    doTest();
  }

  public void testMapParamWithNoArgs() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testGroovyEnumInJavaFile() {
    myFixture.copyFileToProject(getTestName(false) + ".groovy");
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testRangeType() {
    doTest(new GroovyRangeTypeCheckInspection());
  }

  public void testResolveMetaClass() {
    doTest(new GroovyAccessibilityInspection());
  }

  public void testSOFInDelegate() {
    doTest();
  }

  public void testInheritInterfaceInDelegate() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testMethodImplementedByDelegate() {
    doTest();
  }

  public void testVarNotAssigned() {
    doTest(new UnassignedVariableAccessInspection());
  }

  public void testMultipleVarNotAssigned() {
    doTest(new UnassignedVariableAccessInspection());
  }

  public void testTestMarkupStubs() {
    doTest();
  }

  public void testResultOfAssignmentUsed() {
    doTest(new GroovyResultOfAssignmentUsedInspection());
  }

  public void testGdslWildcardTypes() {
    myFixture.configureByText("a.groovy",
                              "List<? extends String> la = []; la.get(1); " +
                              "List<? super String> lb = []; lb.get(1); " +
                              "List<?> lc = []; lc.get(1); "
    );
    myFixture.checkHighlighting(true, false, false);
  }

  public void testThisTypeInStaticContext() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testSuppressions() {
    doTest(new GroovyUnresolvedAccessInspection(), new GroovyUntypedAccessInspection());
  }

  public void testUsageInInjection() { doTest(new UnusedDefInspection()); }

  public void testDuplicatedNamedArgs() {doTest();}

  public void testAnonymousClassArgList() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testConstructorWithAllParametersOptional() {
    doTest();
  }

  public void testTupleConstructorAttributes() throws Exception {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testCanonicalConstructorApplicability() throws Exception {
    myFixture.addClass("package groovy.transform; public @interface Canonical {}");
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testUnusedDefsForArgs() {
    doTest(new UnusedDefInspection());
  }

  public void testUsedDefBeforeTry1() {
    doTest(new UnusedDefInspection());
  }

  public void testUsedDefBeforeTry2() {
    doTest(new UnusedDefInspection());
  }

  public void testUsedInCatch() {
    doTest(new UnusedDefInspection())
  }

  public void testStringAssignableToChar() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testInnerClassConstructorThis() {
    myFixture.enableInspections(new GroovyResultOfAssignmentUsedInspection());
    myFixture.testHighlighting(true, true, true, getTestName(false) + ".groovy");
  }

  public void testCurrying(){
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testAnotherCurrying(){
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testResultOfIncUsed() {
    doTest(new GroovyResultOfIncrementOrDecrementUsedInspection());
  }

  public void testNativeMapAssignability() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testDelegatedMethodIsImplemented() {
    doTest();
  }

  public void testEnumImplementsAllGroovyObjectMethods() {
    doTest();
  }

  public void testTwoLevelGrMap() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testPassingCollectionSubtractionIntoGenericMethod() throws Exception {
    doTest(new GroovyAssignabilityCheckInspection(), new GroovyUnresolvedAccessInspection());
  }

  public void testBuilderMembersAreNotUnresolved() throws Exception {
    doTest(new GroovyUnresolvedAccessInspection());
  }

  public void testUnknownVarInArgList() {
    doTest(new GroovyAssignabilityCheckInspection());
  }
  
  public void testImplicitEnumCoercion() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testCallableProperty() {
    doTest(new GroovyAssignabilityCheckInspection());
  }
  
  public void testConstructor() {
    doTest(new GroovyAssignabilityCheckInspection(), new GroovyConstructorNamedArgumentsInspection());
  }

  public void testRecursiveConstructors() {
    doTest();
  }

  public void testEnumConstantConstructors() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testUnnecessaryReturnInSwitch() {
    doTest(new GroovyUnnecessaryReturnInspection());
  }
  
  public void testLiteralConstructorUsages() {
    doTest(new GroovyAssignabilityCheckInspection());
  }
  
  public void testSpreadArguments() {
    doTest(new GroovyAssignabilityCheckInspection());
  }
  
  public void testImmutableConstructorFromJava() {
    myFixture.addFileToProject "a.groovy", '''@groovy.transform.Immutable class Foo { int a; String b }'''
    myFixture.configureByText 'a.java', '''
class Bar {{
  new Foo<error>()</error>;
  new Foo<error>(2)</error>;
  new Foo(2, "3");
}}'''
    myFixture.checkHighlighting(false, false, false)
  }

  public void testTupleConstructorFromJava() {
    myFixture.addFileToProject "a.groovy", '''@groovy.transform.TupleConstructor class Foo { int a; String b }'''
    myFixture.configureByText 'a.java', '''
class Bar {{
  new Foo();
  new Foo(2);
  new Foo(2, "3");
  new Foo<error>(2, "3", 9)</error>;
}}'''
    myFixture.checkHighlighting(false, false, false)
  }

  public void testInheritConstructorsFromJava() throws Exception {
    myFixture.addFileToProject "a.groovy", '''
class Person {
  Person(String first, String last) { }
  Person(String first, String last, String address) { }
  Person(String first, String last, int zip) { }
}

@groovy.transform.InheritConstructors
class PersonAge extends Person {
  PersonAge(String first, String last, int zip) { }
}
'''
    myFixture.configureByText 'a.java', '''
class Bar {{
  new PersonAge("a", "b");
  new PersonAge("a", "b", "c");
  new PersonAge("a", "b", 239);
  new PersonAge<error>(2, "3", 9)</error>;
}}'''
    myFixture.checkHighlighting(false, false, false)
  }

  public void testDiamondTypeInferenceSOE() throws Exception {
    myFixture.configureByText 'a.groovy', ''' Map<Integer, String> a; a[2] = [:] '''
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection())
    myFixture.checkHighlighting(false, false, false)
  }

  public void testMemberShipOperatorCheck() {
    doTest(new GroovyInArgumentCheckInspection());
  }

  void testDefaultInitializersAreNotAllowedInAbstractMethods() {doTest()}
  void testConstructorTypeArgs(){doTest()}

  void testIncorrectEscaping() {doTest()}
  
  void testRegexInCommandArg() {doTest()}
  void testOctalInspection() {
    doTest(new GroovyOctalIntegerInspection())
  }

  void testThisInStaticMethodOfAnonymousClass() {
    myFixture.configureByText('a.groovy', '''\
class A {
    static abc
    def foo() {
        new Runnable() {
            <error descr="Inner classes cannot have static declarations">static</error> void run() {
                print this.@abc
            }
        }.run()
    }
}''')

    myFixture.enableInspections(GroovyAssignabilityCheckInspection)
    myFixture.checkHighlighting(true, false, false);
  }

  public void testJUnitConvention() {
    myFixture.addClass("package junit.framework; public class TestCase {}")
    doTest(new JUnitTestClassNamingConventionInspection(), new JUnitAbstractTestClassNamingConventionInspection())
  }

  void testDuplicateMethods() {
    myFixture.configureByText('a.groovy', '''\
class A {
  <error descr="Method with signature foo() is already defined in the class 'A'">def foo()</error>{}
  <error descr="Method with signature foo() is already defined in the class 'A'">def foo(def a=null)</error>{}
}
''')
    myFixture.checkHighlighting(true, false, false)
  }
  
  void testPrivateTopLevelClassInJava() {
    myFixture.addFileToProject('pack/Foo.groovy', 'package pack; private class Foo{}')
    myFixture.configureByText('Abc.java', '''\
import pack.<error descr="'pack.Foo' is not public in 'pack'. Cannot be accessed from outside package">Foo</error>;

class Abc {
  void foo() {
    System.out.print(new <error descr="'pack.Foo' is not public in 'pack'. Cannot be accessed from outside package">Foo</error>());
  }
}''')

    myFixture.testHighlighting(false, false, false)
  }

  void testDelegateToMethodWithItsOwnTypeParams() {
    myFixture.configureByText('a.groovy', '''\
interface I<S> {
    def <T> void foo(List<T> a);
}

class Foo {
    @Delegate private I list
}

<error descr="Method 'foo' is not implemented">class Bar implements I</error> {
  def <T> void foo(List<T> a){}
}

class Baz implements I {
  def void foo(List a){}
}
''')

    myFixture.testHighlighting(false, false, false)
  }

  void testClashingGetters() {
    myFixture.configureByText('a.groovy', '''\
class Foo {

  boolean <warning descr="getter 'getX' clashes with getter 'isX'">getX</warning>() { true }
  boolean <warning descr="getter 'isX' clashes with getter 'getX'">isX</warning>() { false }

  boolean getY() {true}

  boolean isZ() {false}

  boolean <warning descr="method getFoo(int x) clashes with getter 'isFoo'">getFoo</warning>(int x = 5){}
  boolean <warning descr="getter 'isFoo' clashes with method getFoo(int x)">isFoo</warning>(){}
}

def result = new Foo().x''')
    myFixture.enableInspections(new ClashingGettersInspection())
    myFixture.testHighlighting(true, false, false)
  }

  void testPrimitiveTypeParams() {
    myFixture.configureByText('a.groovy', '''
List<<error descr="Primitive type parameters are not allowed">int</error>> list = new ArrayList<int><EOLError descr="'(' expected"></EOLError>
List<? extends <error descr="Primitive bound types are not allowed">double</error>> l = new ArrayList<double>()
List<?> list2

''')
    myFixture.testHighlighting(true, false, false)
  }

  public void testGloballyUnusedSymbols() {
    doTest(new GroovyUnusedDeclarationInspection(), new UnusedDeclarationInspection())
  }

  public void testGloballyUnusedInnerMethods() {
    myFixture.addClass 'package junit.framework; public class TestCase {}'
    doTest(new GroovyUnusedDeclarationInspection(), new UnusedDeclarationInspection())
  }

  public void testAliasInParameterType() {
    myFixture.configureByText('a_.groovy', '''\
import java.awt.event.ActionListener
import java.awt.event.ActionEvent as AE

public class CorrectImplementor implements ActionListener {
  public void actionPerformed (AE e) { //AE is alias to ActionEvent
  }
}

<error descr="Method 'actionPerformed' is not implemented">public class IncorrectImplementor implements ActionListener</error> {
  public void actionPerformed (Object e) {
  }
}
''')
    myFixture.testHighlighting(true, false, false)
  }

  public void testReassignedHighlighting() {
    myFixture.testHighlighting(true, true, true, getTestName(false) + ".groovy");
  }

  public void testInstanceOf() {
    myFixture.configureByText('_a.groovy', '''\
class DslPointcut {}

private def handleImplicitBind(arg) {
    if (arg instanceof Map && arg.size() == 1 && arg.keySet().iterator().next() instanceof String && arg.values().iterator().next() instanceof DslPointcut) {
        return DslPointcut.bind(arg)
    }
    return arg
}''')
    myFixture.testHighlighting(true, false, false)
  }

  public void testTargetAnnotationInsideGroovy1() {
    myFixture.addFileToProject('Ann.groovy', '''
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.*

@Target(FIELD)
@interface Ann {}
''')

    myFixture.configureByText('_.groovy', '''
@<error descr="'@Ann' not applicable to type">Ann</error>
class C {
  @Ann
  def foo

  def ar() {
    @Ann
    def x
  }
}''')

    myFixture.testHighlighting(true, false, false)
  }

  public void testTargetAnnotationInsideGroovy2() {
    myFixture.addFileToProject('Ann.groovy', '''
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.*

@Target(value=[FIELD, TYPE])
@interface Ann {}
''')

    myFixture.configureByText('_.groovy', '''
@Ann
class C {
  @Ann
  def foo

  def ar() {
    @Ann
    def x
  }
}''')
    myFixture.testHighlighting(true, false, false)
  }

  public void testTargetAnnotationFromGroovyToJava() {
    myFixture.addFileToProject('Ann.groovy', '''
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.*

@Target(value=[FIELD, TYPE])
@interface Ann {}
''')

    myFixture.configureByText('_.java', '''
@Ann
class C {
  @Ann
  int foo;

  @<error descr="'@Ann' not applicable to type use">Ann</error>
  void ar() {
    @<error descr="'@Ann' not applicable to type use">Ann</error>
    int x;
  }
}
''')
    myFixture.testHighlighting(true, false, false)
  }

}