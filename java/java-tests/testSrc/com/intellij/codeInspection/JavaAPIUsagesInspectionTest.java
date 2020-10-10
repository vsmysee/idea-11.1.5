/*
 * User: anna
 * Date: 11-Sep-2007
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.FileIndexImplUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.testFramework.InspectionTestCase;

public class JavaAPIUsagesInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    final Java15APIUsageInspection usageInspection = new Java15APIUsageInspection();
    doTest("usage1.5/" + getTestName(true), new LocalInspectionToolWrapper(usageInspection), "java 1.5");
  }

  public void testConstructor() throws Exception {
    final LanguageLevel[] languageLevel = {null};
    try {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          ModifiableRootModel model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
          LanguageLevelModuleExtension extension = model.getModuleExtension(LanguageLevelModuleExtension.class);
          languageLevel[0] = extension.getLanguageLevel();
          extension.setLanguageLevel(LanguageLevel.JDK_1_4);
          model.commit();
        }
      });

      doTest();
    }
    finally {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          ModifiableRootModel model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
          LanguageLevelModuleExtension extension = model.getModuleExtension(LanguageLevelModuleExtension.class);
          extension.setLanguageLevel(languageLevel[0]);
          model.commit();
        }
      });
    }
  }

  public void testIgnored() throws Exception {
    doTest();
  }

  public void _testCollectSinceApiUsages() {
    final String version = "1.4";
    final ContentIterator contentIterator = new ContentIterator() {
      @Override
      public boolean processFile(VirtualFile fileOrDir) {
        final PsiFile file = PsiManager.getInstance(getProject()).findFile(fileOrDir);
        if (file instanceof PsiJavaFile) {
          file.accept(new JavaRecursiveElementVisitor(){
            @Override
            public void visitElement(PsiElement element) {
              super.visitElement(element);
              if (element instanceof PsiDocCommentOwner) {
                final PsiDocComment comment = ((PsiDocCommentOwner)element).getDocComment();
                if (comment != null) {
                  for (PsiDocTag tag : comment.getTags()) {
                    if (Comparing.strEqual(tag.getName(), "since")) {
                      final PsiDocTagValue value = tag.getValueElement();
                      if (value != null && value.getText().equals(version)) {
                        System.out.println(Java15APIUsageInspection.getSignature((PsiMember)element));
                      }
                      break;
                    }
                  }
                }
              }
            }
          });
        }
        return true;
      }
    };
    final VirtualFile srcFile = JarFileSystem.getInstance().findFileByPath("c:/program files/java/jdk1.6.0_12/src.zip!/");
    FileIndexImplUtil.iterateRecursively(srcFile, VirtualFileFilter.ALL, contentIterator);
  }
}
