/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven;

import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.List;

public abstract class MavenTestCase extends UsefulTestCase {
  protected static final MavenConsole NULL_MAVEN_CONSOLE = new NullMavenConsole();
  // should not be static
  protected static MavenProgressIndicator EMPTY_MAVEN_PROCESS = new MavenProgressIndicator(new EmptyProgressIndicator());

  private static File ourTempDir;

  protected IdeaProjectTestFixture myTestFixture;

  protected Project myProject;

  protected File myDir;
  protected VirtualFile myProjectRoot;

  protected VirtualFile myProjectPom;
  protected List<VirtualFile> myAllPoms = new ArrayList<VirtualFile>();
  private boolean myCompileInBackground;

  static {
    IdeaTestCase.initPlatformPrefix();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    ensureTempDirCreated();

    myDir = new File(ourTempDir, getTestName(false));
    myDir.mkdirs();

    setUpFixtures();

    myProject = myTestFixture.getProject();

    MavenWorkspaceSettingsComponent.getInstance(myProject).loadState(new MavenWorkspaceSettings());

    String home = getTestMavenHome();
    if (home != null) {
      getMavenGeneralSettings().setMavenHome(home);
    }

    restoreSettingsFile();

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              setUpInWriteAction();
            }
            catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        });
      }
    });

    myCompileInBackground = CompilerWorkspaceConfiguration.getInstance(myProject).COMPILE_IN_BACKGROUND;
    CompilerWorkspaceConfiguration.getInstance(myProject).COMPILE_IN_BACKGROUND = false;
  }

  private static void ensureTempDirCreated() {
    if (ourTempDir != null) return;

    ourTempDir = new File(FileUtil.getTempDirectory(), "mavenTests");
    FileUtil.delete(ourTempDir);
    ourTempDir.mkdirs();
  }

  protected void setUpFixtures() throws Exception {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName()).getFixture();
    myTestFixture.setUp();
  }

  protected void setUpInWriteAction() throws Exception {
    File projectDir = new File(myDir, "project");
    projectDir.mkdirs();
    myProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir);
  }

  @Override
  protected void tearDown() throws Exception {
    CompilerWorkspaceConfiguration.getInstance(myProject).COMPILE_IN_BACKGROUND = myCompileInBackground;

    myProject = null;
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          tearDownFixtures();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
    if (!FileUtil.delete(myDir)) {
      System.out.println("Cannot delete " + myDir);
      myDir.deleteOnExit();
    }

    MavenIndicesManager.getInstance().clear();

    super.tearDown();
    resetClassFields(getClass());
  }

  protected void tearDownFixtures() throws Exception {
    myTestFixture.tearDown();
    myTestFixture = null;
  }

  private void resetClassFields(final Class<?> aClass) {
    if (aClass == null) return;

    final Field[] fields = aClass.getDeclaredFields();
    for (Field field : fields) {
      final int modifiers = field.getModifiers();
      if ((modifiers & Modifier.FINAL) == 0
          && (modifiers & Modifier.STATIC) == 0
          && !field.getType().isPrimitive()) {
        field.setAccessible(true);
        try {
          field.set(this, null);
        }
        catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    }

    if (aClass == MavenTestCase.class) return;
    resetClassFields(aClass.getSuperclass());
  }

  @Override
  protected void runTest() throws Throwable {
    try {
      if (runInWriteAction()) {
        new WriteAction() {
          @Override
          protected void run(Result result) throws Throwable {
            MavenTestCase.super.runTest();
          }
        }.executeSilently().throwException();
      }
      else {
        MavenTestCase.super.runTest();
      }
    }
    catch (Exception throwable) {
      Throwable each = throwable;
      do {
        if (each instanceof HeadlessException) {
          printIgnoredMessage("Doesn't work in Headless environment");
          return;
        }
      }
      while ((each = each.getCause()) != null);
      throw throwable;
    }
  }

  @Override
  protected void invokeTestRunnable(Runnable runnable) throws Exception {
    runnable.run();
  }

  protected boolean runInWriteAction() {
    return false;
  }

  protected static String getRoot() {
    if (SystemInfo.isWindows) return "c:";
    return "";
  }

  protected static String getEnvVar() {
    if (SystemInfo.isWindows) return "TEMP";
    else if (SystemInfo.isLinux) return "HOME";
    return "TMPDIR";
  }

  protected MavenGeneralSettings getMavenGeneralSettings() {
    return MavenProjectsManager.getInstance(myProject).getGeneralSettings();
  }

  protected MavenImportingSettings getMavenImporterSettings() {
    return MavenProjectsManager.getInstance(myProject).getImportingSettings();
  }

  protected String getRepositoryPath() {
    String path = getRepositoryFile().getPath();
    return FileUtil.toSystemIndependentName(path);
  }

  protected File getRepositoryFile() {
    return getMavenGeneralSettings().getEffectiveLocalRepository();
  }

  protected void setRepositoryPath(String path) {
    getMavenGeneralSettings().setLocalRepository(path);
  }

  protected String getProjectPath() {
    return myProjectRoot.getPath();
  }

  protected String getParentPath() {
    return myProjectRoot.getParent().getPath();
  }

  protected String pathFromBasedir(String relPath) {
    return pathFromBasedir(myProjectRoot, relPath);
  }

  protected static String pathFromBasedir(VirtualFile root, String relPath) {
    return FileUtil.toSystemIndependentName(root.getPath() + "/" + relPath);
  }

  protected VirtualFile updateSettingsXml(String content) throws IOException {
    return updateSettingsXmlFully(createSettingsXmlContent(content));
  }

  protected VirtualFile updateSettingsXmlFully(@NonNls @Language("XML") String content) throws IOException {
    File ioFile = new File(myDir, "settings.xml");
    ioFile.createNewFile();
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    setFileContent(f, content);
    getMavenGeneralSettings().setUserSettingsFile(f.getPath());
    return f;
  }

  protected void deleteSettingsXml() throws IOException {
    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myDir, "settings.xml"));
        if (f != null) f.delete(this);
      }
    }.execute().throwException();
  }

  private static String createSettingsXmlContent(String content) {
    String mirror = System.getProperty("idea.maven.test.mirror",
                                       "http://maven.labs.intellij.net:8081/nexus/content/groups/public/");
    return "<settings>" +
           content +
           "<mirrors>" +
           "  <mirror>" +
           "    <id>Nexus</id>" +
           "    <url>" + mirror + "</url>" +
           "    <mirrorOf>*</mirrorOf>" +
           "  </mirror>" +
           "</mirrors>" +
           "</settings>";
  }

  protected void restoreSettingsFile() throws IOException {
    updateSettingsXml("");
  }

  protected Module createModule(String name) throws IOException {
    return createModule(name, StdModuleTypes.JAVA);
  }

  protected Module createModule(final String name, final ModuleType type) throws IOException {
    return new WriteCommandAction<Module>(myProject) {
      @Override
      protected void run(Result<Module> moduleResult) throws Throwable {
        VirtualFile f = createProjectSubFile(name + "/" + name + ".iml");
        Module module = ModuleManager.getInstance(myProject).newModule(f.getPath(), type);
        ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        model.addContentEntry(f.getParent());
        model.commit();
        moduleResult.setResult(module);
      }
    }.execute().getResultObject();
  }

  protected VirtualFile createProjectPom(@NonNls String xml) throws IOException {
    return myProjectPom = createPomFile(myProjectRoot, xml);
  }

  protected VirtualFile createModulePom(String relativePath, String xml) throws IOException {
    return createPomFile(createProjectSubDir(relativePath), xml);
  }

  protected VirtualFile createPomFile(final VirtualFile dir, String xml) throws IOException {
    VirtualFile f = dir.findChild("pom.xml");
    if (f == null) {
      f = new WriteAction<VirtualFile>() {
        @Override
        protected void run(Result<VirtualFile> result) throws Throwable {
          VirtualFile res = dir.createChildData(null, "pom.xml");
          result.setResult(res);
        }
      }.execute().getResultObject();
      myAllPoms.add(f);
    }
    setFileContent(f, createPomXml(xml));
    return f;
  }

  @NonNls @Language(value="XML")
  protected static String createPomXml(@NonNls @Language(value="XML", prefix="<xml>", suffix="</xml>") String xml) {
    return "<?xml version=\"1.0\"?>" +
           "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
           "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
           "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
           "  <modelVersion>4.0.0</modelVersion>" +
           xml +
           "</project>";
  }

  protected VirtualFile createProfilesXmlOldStyle(String xml) throws IOException {
    return createProfilesFile(myProjectRoot, xml, true);
  }

  protected VirtualFile createProfilesXmlOldStyle(String relativePath, String xml) throws IOException {
    return createProfilesFile(createProjectSubDir(relativePath), xml, true);
  }

  protected VirtualFile createProfilesXml(String xml) throws IOException {
    return createProfilesFile(myProjectRoot, xml, false);
  }

  protected VirtualFile createProfilesXml(String relativePath, String xml) throws IOException {
    return createProfilesFile(createProjectSubDir(relativePath), xml, false);
  }

  private static VirtualFile createProfilesFile(VirtualFile dir, String xml, boolean oldStyle) throws IOException {
    return createProfilesFile(dir, createValidProfiles(xml, oldStyle));
  }

  protected VirtualFile createFullProfilesXml(String content) throws IOException {
    return createProfilesFile(myProjectRoot, content);
  }

  protected VirtualFile createFullProfilesXml(String relativePath, String content) throws IOException {
    return createProfilesFile(createProjectSubDir(relativePath), content);
  }

  private static VirtualFile createProfilesFile(final VirtualFile dir, String content) throws IOException {
    VirtualFile f = dir.findChild("profiles.xml");
    if (f == null) {
      f = new WriteAction<VirtualFile>() {
        @Override
        protected void run(Result<VirtualFile> result) throws Throwable {
          VirtualFile res = dir.createChildData(null, "profiles.xml");
          result.setResult(res);
        }
      }.execute().getResultObject();
    }
    setFileContent(f, content);
    return f;
  }

  @Language("XML")
  private static String createValidProfiles(String xml, boolean oldStyle) {
    if (oldStyle) {
      return "<?xml version=\"1.0\"?>" +
             "<profiles>" +
             xml +
             "</profiles>";
    }
    return "<?xml version=\"1.0\"?>" +
           "<profilesXml>" +
           "<profiles>" +
           xml +
           "</profiles>" +
           "</profilesXml>";
  }

  protected void deleteProfilesXml() throws IOException {
    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        VirtualFile f = myProjectRoot.findChild("profiles.xml");
        if (f != null) f.delete(this);
      }
    }.execute().throwException();
  }

  protected void createStdProjectFolders() {
    createProjectSubDirs("src/main/java",
                         "src/main/resources",
                         "src/test/java",
                         "src/test/resources");
  }

  protected void createProjectSubDirs(String... relativePaths) {
    for (String path : relativePaths) {
      createProjectSubDir(path);
    }
  }

  protected VirtualFile createProjectSubDir(String relativePath) {
    File f = new File(getProjectPath(), relativePath);
    f.mkdirs();
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
  }

  protected VirtualFile createProjectSubFile(String relativePath) throws IOException {
    File f = new File(getProjectPath(), relativePath);
    f.getParentFile().mkdirs();
    f.createNewFile();
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
  }

  protected VirtualFile createProjectSubFile(String relativePath, String content) throws IOException {
    VirtualFile file = createProjectSubFile(relativePath);
    setFileContent(file, content);
    return file;
  }

  private static void setFileContent(VirtualFile file, String content) throws IOException {
    file.setBinaryContent(content.getBytes(), file.getModificationStamp() + 4000, file.getTimeStamp() + 4000);
  }

  protected static void assertPathEquals(String expected, String actual) {
    if (expected != null) expected = FileUtil.toSystemIndependentName(expected);
    if (actual != null) actual = FileUtil.toSystemIndependentName(actual);
    assertEquals(expected, actual);
  }

  protected static <T, U> void assertOrderedElementsAreEqual(Collection<U> actual, Collection<T> expected) {
    assertOrderedElementsAreEqual(actual, expected.toArray());
  }

  protected static <T> void assertUnorderedElementsAreEqual(Collection<T> actual, Collection<T> expected) {
    assertEquals(new HashSet<T>(expected), new HashSet<T>(actual));
  }

  protected static <T> void assertUnorderedElementsAreEqual(T[] actual, T... expected) {
    assertUnorderedElementsAreEqual(Arrays.asList(actual), expected);
  }

  protected static <T> void assertUnorderedElementsAreEqual(Collection<T> actual, T... expected) {
    assertUnorderedElementsAreEqual(actual, Arrays.asList(expected));
  }

  protected static <T, U> void assertOrderedElementsAreEqual(Collection<U> actual, T... expected) {
    String s = "\nexpected: " + Arrays.asList(expected) + "\nactual: " + new ArrayList<U>(actual);
    assertEquals(s, expected.length, actual.size());

    List<U> actualList = new ArrayList<U>(actual);
    for (int i = 0; i < expected.length; i++) {
      T expectedElement = expected[i];
      U actualElement = actualList.get(i);
      assertEquals(s, expectedElement, actualElement);
    }
  }

  protected static <T> void assertContain(List<? extends T> actual, T... expected) {
    List<T> expectedList = Arrays.asList(expected);
    assertTrue("expected: " + expectedList + "\n" + "actual: " + actual.toString(), actual.containsAll(expectedList));
  }

  protected static <T> void assertDoNotContain(List<T> actual, T... expected) {
    List<T> actualCopy = new ArrayList<T>(actual);
    actualCopy.removeAll(Arrays.asList(expected));
    assertEquals(actual.toString(), actualCopy.size(), actual.size());
  }

  protected boolean ignore() {
    printIgnoredMessage(null);
    return true;
  }

  protected boolean hasMavenInstallation() {
    boolean result = getTestMavenHome() != null;
    if (!result) printIgnoredMessage("Maven installation not found");
    return result;
  }

  private void printIgnoredMessage(String message) {
    String toPrint = "Ignored";
    if (message != null) {
      toPrint += ", because " + message;
    }
    toPrint += ": " + getClass().getSimpleName() + "." + getName();
    System.out.println(toPrint);
  }

  private static String getTestMavenHome() {
    return System.getProperty("idea.maven.test.home");
  }
}
