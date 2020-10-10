/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.ui.UIUtil;

/**
 * @author ven
 */
public class PullUpTest extends LightRefactoringTestCase {
  private static final String BASE_PATH = "/refactoring/pullUp/";


  public void testQualifiedThis() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("Inner", PsiClass.class));
  }

  public void testQualifiedSuper() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("Inner", PsiClass.class));
  }

  public void testQualifiedReference() throws Exception {     // IDEADEV-25008
    doTest(new RefactoringTestUtil.MemberDescriptor("x", PsiField.class),
           new RefactoringTestUtil.MemberDescriptor("getX", PsiMethod.class),
           new RefactoringTestUtil.MemberDescriptor("setX", PsiMethod.class));
  }

  public void testPullUpInheritedStaticClasses() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("C", PsiClass.class),
           new RefactoringTestUtil.MemberDescriptor("D", PsiClass.class));
  }

  public void testPullUpPrivateInnerClassWithPrivateConstructor() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("C", PsiClass.class));
  }

  public void testPullUpAndAbstractize() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("a", PsiMethod.class),
           new RefactoringTestUtil.MemberDescriptor("b", PsiMethod.class, true));
  }

  public void testTryCatchFieldInitializer() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("field", PsiField.class));
  }

  public void testIfFieldInitializationWithNonMovedField() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }

  public void testIfFieldMovedInitialization() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }

  public void testMultipleConstructorsFieldInitialization() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }

  public void testMultipleConstructorsFieldInitializationNoGood() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }


  public void testRemoveOverride() throws Exception {
    setLanguageLevel(LanguageLevel.JDK_1_5);
    doTest(new RefactoringTestUtil.MemberDescriptor("get", PsiMethod.class));
  }

  public void testTypeParamErasure() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }

  public void testTypeParamSubst() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }

  public void testTypeArgument() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("f", PsiField.class));
  }

  public void testGenericsInAbstractMethod() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("method", PsiMethod.class, true));
  }

  public void testReplaceDuplicatesInInheritors() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class, false));
  }

  public void testGenericsInImplements() throws Exception {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("I", PsiClass.class));
  }

  public void testUpdateStaticRefs() throws Exception {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testRemoveOverrideFromPulledMethod() throws Exception {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testPreserveOverrideInPulledMethod() throws Exception {
    doTest(false, new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  private void doTest(RefactoringTestUtil.MemberDescriptor... membersToFind) throws Exception {
    doTest(true, membersToFind);
  }

  private void doTest(final boolean checkMemebersMovedCount, RefactoringTestUtil.MemberDescriptor... membersToFind) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement elementAt = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    final PsiClass sourceClass = PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
    assertNotNull(sourceClass);

    PsiClass targetClass = sourceClass.getSuperClass();
    if (!targetClass.isWritable()) {
      final PsiClass[] interfaces = sourceClass.getInterfaces();
      assertEquals(1, interfaces.length);
      assertTrue(interfaces[0].isWritable());
      targetClass = interfaces[0];
    }
    MemberInfo[] infos = RefactoringTestUtil.findMembers(sourceClass, membersToFind);

    final int[] countMoved = {0};
    final MoveMemberListener listener = new MoveMemberListener() {
      @Override
      public void memberMoved(PsiClass aClass, PsiMember member) {
        assertEquals(sourceClass, aClass);
        countMoved[0]++;
      }
    };
    JavaRefactoringListenerManager.getInstance(getProject()).addMoveMembersListener(listener);
    final PullUpHelper helper = new PullUpHelper(sourceClass, targetClass, infos, new DocCommentPolicy(DocCommentPolicy.ASIS));
    helper.run();
    UIUtil.dispatchAllInvocationEvents();
    JavaRefactoringListenerManager.getInstance(getProject()).removeMoveMembersListener(listener);
    if (checkMemebersMovedCount) {
      assertEquals(countMoved[0], membersToFind.length);
    }
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
