package com.intellij.psi;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestCase;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 *  @author dsl
 */
public class LibraryOrderTest extends PsiTestCase {

  public void test1() {
    setupPaths();
    checkClassFromLib("test.A", "1");

    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    final OrderEntry[] order = rootModel.getOrderEntries();
    final int length = order.length;
    OrderEntry lib2 = order[length - 1];
    OrderEntry lib1 = order[length - 2];
    assertTrue(lib1 instanceof LibraryOrderEntry);
    assertEquals("lib1", ((LibraryOrderEntry) lib1).getLibraryName());
    assertTrue(lib2 instanceof LibraryOrderEntry);
    assertEquals("lib2", ((LibraryOrderEntry) lib2).getLibraryName());

    order[length - 1] = lib1;
    order[length - 2] = lib2;
    rootModel.rearrangeOrderEntries(order);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        rootModel.commit();
      }
    }
    );

    checkClassFromLib("test.A", "2");
  }

  public void testNavigation() throws Exception {
    setupPaths();
    final JavaPsiFacade psiManager = getJavaFacade();
    final PsiClass classA = psiManager.findClass("test.A");
    final PsiElement navigationElement = classA.getNavigationElement();
    assertNotNull(navigationElement);
    assertTrue(navigationElement != classA);
    assertEquals("A.java", navigationElement.getContainingFile().getVirtualFile().getName());
  }

  private void checkClassFromLib(String qualifiedName, String index) {
    final PsiClass classA = (PsiClass)JavaPsiFacade.getInstance(myProject).findClass(qualifiedName).getNavigationElement();
    assertNotNull(classA);
    final PsiMethod[] methodsA = classA.getMethods();
    assertEquals(1, methodsA.length);
    assertEquals("methodOfClassFromLib" + index, methodsA[0].getName());
  }

  public void setupPaths() {
    final String basePath = JavaTestUtil.getJavaTestDataPath() + "/psi/libraryOrder/";

    final VirtualFile lib1SrcFile = refreshAndFindFile(basePath + "lib1/src");
    final VirtualFile lib1classes = refreshAndFindFile(basePath + "lib1/classes");
    final VirtualFile lib2SrcFile = refreshAndFindFile(basePath + "lib2/src");
    final VirtualFile lib2classes = refreshAndFindFile(basePath + "lib2/classes");

    assertTrue(lib1SrcFile != null);
    assertTrue(lib2SrcFile != null);

    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    final LibraryTable libraryTable = rootModel.getModuleLibraryTable();

    addLibraryWithSourcePath("lib1", libraryTable, lib1SrcFile, lib1classes);
    addLibraryWithSourcePath("lib2", libraryTable, lib2SrcFile, lib2classes);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        rootModel.commit();
      }
    });
    final List<VirtualFile> list = Arrays.asList(OrderEnumerator.orderEntries(myModule).getClassesRoots());
    assertTrue(list.contains(lib1classes));
    assertTrue(list.contains(lib2classes));
  }

  private VirtualFile refreshAndFindFile(String path) {
    final File ioLib1Src = new File(path);
    final VirtualFile lib1SrcFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioLib1Src);
    return lib1SrcFile;
  }

  private void addLibraryWithSourcePath(String name, final LibraryTable libraryTable, final VirtualFile libSource,
                                        VirtualFile libClasses) {
    final Library lib = libraryTable.createLibrary(name);
    final Library.ModifiableModel libModel = lib.getModifiableModel();
    libModel.addRoot(libClasses, OrderRootType.CLASSES);
    libModel.addRoot(libSource, OrderRootType.SOURCES);
    libModel.commit();
  }
}
