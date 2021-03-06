/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.ether;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.Sdk;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.artifacts.Artifact;
import org.jetbrains.jps.idea.IdeaProjectLoader;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLoggerImpl;
import org.jetbrains.jps.incremental.java.JavaBuilderLogger;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;
import org.jetbrains.jps.server.ClasspathBootstrap;
import org.jetbrains.jps.server.ProjectDescriptor;

import java.io.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author db
 * @since 26.07.11
 */
public abstract class IncrementalTestCase extends TestCase {
  private final String groupName;
  private final String tempDir = FileUtil.toSystemDependentName(new File(System.getProperty("java.io.tmpdir")).getCanonicalPath());

  private String baseDir;
  private String workDir;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  protected IncrementalTestCase(final String name) throws Exception {
    super(name);
    groupName = name;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    baseDir = PathManagerEx.getTestDataPath() + File.separator + "compileServer" + File.separator + "incremental" + File.separator;

    for (int i = 0; ; i++) {
      final File tmp = new File(tempDir + File.separator + "__temp__" + i);
      if (tmp.mkdir()) {
        workDir = tmp.getPath() + File.separator;
        break;
      }
    }

    FileUtil.copyDir(new File(getBaseDir()), new File(getWorkDir()));
    
    Paths.getInstance().setSystemRoot(new File(workDir));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      super.tearDown();
    }
    finally {
      delete(new File(workDir));
    }
  }

  private String getProjectName() {
    final String name = getName();

    assert (name.startsWith("test"));

    return Character.toLowerCase(name.charAt("test".length())) + name.substring("test".length() + 1);
  }

  private String getDir(final String prefix) {
    return prefix + groupName + File.separator + getProjectName();
  }

  private String getBaseDir() {
    return getDir(baseDir);
  }

  private String getWorkDir() {
    return getDir(workDir);
  }

  private static void delete(final File file) throws Exception {
    if (file.isDirectory()) {
      final File[] files = file.listFiles();

      if (files != null) {
        for (File f : files) {
          delete(f);
        }
      }
    }

    if (!file.delete()) throw new IOException("could not delete file or directory " + file.getPath());
  }

  private static void copy(final File input, final File output) throws Exception {
    if (input.isDirectory()) {
      if (output.mkdirs()) {
        final File[] files = input.listFiles();

        if (files != null) {
          for (File f : files) {
            copy(f, new File(output.getPath(), f.getName()));
          }
        }
      }
      else {
        throw new IOException("unable to create directory " + output.getPath());
      }
    }
    else if (input.isFile()) {
      FileReader in = null;
      FileWriter out = null;

      try {
        in = new FileReader(input);
        out = new FileWriter(output);
        int c;
        while ((c = in.read()) != -1) out.write(c);
      }
      finally {
        try {
          if (in != null) {
            in.close();
          }
        }
        finally {
          if (out != null) {
            out.close();
          }
        }
      }
    }
  }

  private void modify() throws Exception {
    final File dir = new File(getBaseDir());
    final File[] files = dir.listFiles(new FileFilter() {
      public boolean accept(final File pathname) {
        final String name = pathname.getName();

        return name.endsWith(".java.new") || name.endsWith(".java.remove");
      }
    });

    for (File input : files) {
      final String name = input.getName();

      final boolean copy = name.endsWith(".java.new");
      final String postfix = name.substring(0, name.length() - (copy ? ".new" : ".remove").length());
      final int pathSep = postfix.indexOf("$");
      final String basename = pathSep == -1 ? postfix : postfix.substring(pathSep + 1);
      final String path =
        getWorkDir() + File.separator + (pathSep == -1 ? "src" : postfix.substring(0, pathSep).replace('-', File.separatorChar));
      final File output = new File(path, basename);

      if (copy) {
        copy(input, output);
      }
      else {
        output.delete();
      }
    }
  }

  public void doTest() throws Exception {
    final String projectPath = getWorkDir() + File.separator + ".idea";
    final Project project = new Project();

    final Sdk jdk = project.createSdk("JavaSDK", "IDEA jdk", "1.6", System.getProperty("java.home"), null);
    final List<String> paths = new LinkedList<String>();

    paths.add(FileUtil.toSystemIndependentName(ClasspathBootstrap.getResourcePath(Object.class).getCanonicalPath()));

    jdk.setClasspath(paths);

    IdeaProjectLoader.loadFromPath(project, projectPath, "");

    final File dataStorageRoot = Paths.getDataStorageRoot(project);
    final TestJavaBuilderLogger javaBuilderLogger = new TestJavaBuilderLogger(FileUtil.toSystemIndependentName(getWorkDir() + File.separator));
    final ProjectDescriptor projectDescriptor =
      new ProjectDescriptor(project, new FSState(true), new ProjectTimestamps(dataStorageRoot),
                            new BuildDataManager(dataStorageRoot, true), new BuildLoggingManager(new ArtifactBuilderLoggerImpl(), javaBuilderLogger));
    try {
      new IncProjectBuilder(
        projectDescriptor, BuilderRegistry.getInstance(), Collections.<String, String>emptyMap(), CanceledStatus.NULL
      ).build(
        new AllProjectScope(project, Collections.<Artifact>emptySet(), true), false, true
      );

      modify();

      if (SystemInfo.isUnix) {
        Thread.sleep(1000L);
      }

      new IncProjectBuilder(
        projectDescriptor, BuilderRegistry.getInstance(), Collections.<String, String>emptyMap(), CanceledStatus.NULL
      ).build(
        new AllProjectScope(project, Collections.<Artifact>emptySet(), false), true, false
      );

      final String expected = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(getBaseDir() + ".log")));
      final String actual = javaBuilderLogger.myLog.toString();
      assertEquals(expected, actual);
    }
    finally {
      projectDescriptor.release();
    }
  }

  private static class TestJavaBuilderLogger implements JavaBuilderLogger {
    private final String myRoot;
    private final StringBuilder myLog;

    public TestJavaBuilderLogger(String root) {
      myRoot = root;
      myLog = new StringBuilder();
    }

    @Override
    public void log(String line) {
      myLog.append(StringUtil.trimStart(line, myRoot)).append('\n');
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }
}
