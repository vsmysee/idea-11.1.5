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
package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.cls.BytePointer;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.cls.ClsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: Nov 22, 2004
 */
public class IdeaJdk extends SdkType implements JavaSdkType {
  private static final Icon ADD_SDK = IconLoader.getIcon("/add_sdk.png");
  private static final Icon SDK_OPEN = IconLoader.getIcon("/sdk_open.png");
  private static final Icon SDK_CLOSED = IconLoader.getIcon("/sdk_closed.png");

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit.projectRoots.IdeaJdk");
  @NonNls private static final String LIB_DIR_NAME = "lib";
  @NonNls private static final String SRC_DIR_NAME = "src";
  @NonNls private static final String PLUGINS_DIR = "plugins";
  @NonNls private static final String JAVAEE_DIR = "JavaEE";
  @NonNls private static final String JSF_DIR = "JSF";
  @NonNls private static final String PERSISTENCE_SUPPORT = "PersistenceSupport";
  @NonNls private static final String DATABASE_DIR = "DatabaseSupport";
  @NonNls private static final String CSS_DIR = "css";

  public IdeaJdk() {
    super("IDEA JDK");
  }

  public Icon getIcon() {
    return SDK_CLOSED;
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "reference.project.structure.sdk.idea";
  }

  public Icon getIconForExpandedTreeNode() {
    return SDK_OPEN;
  }

  public Icon getIconForAddAction() {
    return ADD_SDK;
  }

  public String suggestHomePath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/');
  }

  public boolean isValidSdkHome(String path) {
    if (isFromIDEAProject(path)) {
      return true;
    }
    File home = new File(path);
    if (!home.exists()) {
      return false;
    }
    if (getBuildNumber(path) == null || getOpenApiJar(path) == null) {
      return false;
    }
    return true;
  }

  @Nullable
  private static File getOpenApiJar(String home) {
    @NonNls final String openapiJar = "openapi.jar";
    @NonNls final String platformApiJar = "platform-api.jar";
    final File libDir = new File(home, LIB_DIR_NAME);
    File f = new File(libDir, openapiJar);
    if (f.exists()) return f;
    f = new File(libDir, platformApiJar);
    if (f.exists()) return f;
    return null;
  }

  public static boolean isFromIDEAProject(String path) {
    File home = new File(path);
    File[] openapiDir = home.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        @NonNls final String name = pathname.getName();
        if (name.equals("openapi") && pathname.isDirectory()) return true; //todo
        return false;
      }
    });
    return openapiDir != null && openapiDir.length != 0;
  }

  @Nullable
  public final String getVersionString(final Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk != null ? internalJavaSdk.getVersionString() : null;
  }

  @Nullable
  private static Sdk getInternalJavaSdk(final Sdk sdk) {
    final SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (data instanceof Sandbox) {
      return ((Sandbox)data).getJavaSdk();
    }
    return null;
  }

  public String suggestSdkName(String currentSdkName, String sdkHome) {
    @NonNls final String productName;
    if (new File(sdkHome, "lib/rubymine.jar").exists()) {
      productName = "RubyMine ";
    }
    else if (new File(sdkHome, "lib/pycharm.jar").exists()) {
      productName = "PyCharm ";
    }
    else if (new File(sdkHome, "lib/webide.jar").exists()) {
      productName = "WebStorm/PhpStorm ";
    }
    else if (new File(sdkHome, "lib/flexide.jar").exists()) {
      productName = "Astella ";
    }
    else if (new File(sdkHome, "license/AppCode_license.txt").exists()) {
      productName = "AppCode ";
    }
    else {
      productName = "IDEA ";
    }
    String buildNumber = getBuildNumber(sdkHome);
    return productName + (buildNumber != null ? buildNumber : "");
  }

  @Nullable
  public static String getBuildNumber(String ideaHome) {
    try {
      @NonNls final String buildTxt = "/build.txt";
      return FileUtil.loadFile(new File(ideaHome + buildTxt)).trim();
    }
    catch (IOException e) {
      return null;
    }
  }

  private static VirtualFile[] getIdeaLibrary(String home) {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    appendIdeaLibrary(home + File.separator + LIB_DIR_NAME, null, result);
    appendIdeaLibrary(home + File.separator + PLUGINS_DIR + File.separator + JAVAEE_DIR + File.separator + LIB_DIR_NAME, "javaee-impl.jar",
                      result);
    appendIdeaLibrary(home + File.separator + PLUGINS_DIR + File.separator + JSF_DIR + File.separator + LIB_DIR_NAME, "jsf-impl.jar", result);
    appendIdeaLibrary(home + File.separator + PLUGINS_DIR + File.separator + PERSISTENCE_SUPPORT + File.separator + LIB_DIR_NAME, "persistence-impl.jar", result);
    appendIdeaLibrary(home + File.separator + PLUGINS_DIR + File.separator + DATABASE_DIR + File.separator + LIB_DIR_NAME, "database-impl.jar", result);
    appendIdeaLibrary(home + File.separator + PLUGINS_DIR + File.separator + CSS_DIR + File.separator + LIB_DIR_NAME, "css.jar", result);
    return VfsUtil.toVirtualFileArray(result);
  }

  private static void appendIdeaLibrary(final String path, @Nullable @NonNls final String forbidden, final ArrayList<VirtualFile> result) {
    final JarFileSystem jfs = JarFileSystem.getInstance();
    final File lib = new File(path);
    if (lib.isDirectory()) {
      File[] jars = lib.listFiles();
      if (jars != null) {
        for (File jar : jars) {
          @NonNls String name = jar.getName();
          if (jar.isFile() && !name.equals(forbidden) && (name.endsWith(".jar") || name.endsWith(".zip"))) {
            result.add(jfs.findFileByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR));
          }
        }
      }
    }
  }


  public boolean setupSdkPaths(final Sdk sdk, SdkModel sdkModel) {
    final Sandbox additionalData = (Sandbox)sdk.getSdkAdditionalData();
    if (additionalData != null) {    
      additionalData.cleanupWatchedRoots();
    }

    final SdkModificator sdkModificator = sdk.getSdkModificator();

    final List<String> javaSdks = new ArrayList<String>();
    final Sdk[] sdks = sdkModel.getSdks();
    for (Sdk jdk : sdks) {
      if (isValidInternalJdk(sdk, jdk)) {
        javaSdks.add(jdk.getName());
      }
    }
    if (javaSdks.isEmpty()){
      JavaSdkVersion requiredVersion = getRequiredJdkVersion(sdk);
      if (requiredVersion != null) {
        Messages.showErrorDialog(DevKitBundle.message("no.java.sdk.for.idea.sdk.found", requiredVersion), "No Java SDK Found");
      }
      else {
        Messages.showErrorDialog(DevKitBundle.message("no.idea.sdk.version.found"), "No Java SDK Found");
      }
      return false;
    }

    final int choice = Messages
      .showChooseDialog("Select Java SDK to be used as IDEA internal platform", "Select Internal Java Platform", ArrayUtil.toStringArray(javaSdks), javaSdks.get(0), Messages.getQuestionIcon());

    if (choice != -1) {
      final String name = javaSdks.get(choice);
      final Sdk jdk = sdkModel.findSdk(name);
      LOG.assertTrue(jdk != null);
      setupSdkPaths(sdkModificator, sdk.getHomePath(), jdk);
      sdkModificator.setSdkAdditionalData(new Sandbox(getDefaultSandbox(), jdk, sdk));
      sdkModificator.setVersionString(jdk.getVersionString());
      sdkModificator.commitChanges();
      return true;
    }
    return false;
  }

  public static boolean isValidInternalJdk(Sdk ideaSdk, Sdk sdk) {
    final SdkType sdkType = sdk.getSdkType();
    if (sdkType instanceof JavaSdk) {
      final JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
      JavaSdkVersion requiredVersion = getRequiredJdkVersion(ideaSdk);
      if (version != null && requiredVersion != null) {
        return version.isAtLeast(requiredVersion);
      }
    }
    return false;
  }

  private static int getIdeaClassFileVersion(final Sdk ideaSdk) {
    int result = -1;
    File apiJar = getOpenApiJar(ideaSdk.getHomePath());
    if (apiJar == null) return -1;
    final VirtualFile mainClassFile = JarFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(apiJar.getPath()) +
                                                                                 "!/com/intellij/psi/PsiManager.class");
    if (mainClassFile != null) {
      final BytePointer ptr;
      try {
        ptr = new BytePointer(mainClassFile.contentsToByteArray(), 6);
        result = ClsUtil.readU2(ptr);
      }
      catch (IOException e) {
        // ignore
      }
      catch (ClsFormatException e) {
        // ignore
      }
    }
    return result;
  }

  @Nullable
  private static JavaSdkVersion getRequiredJdkVersion(final Sdk ideaSdk) {
    int classFileVersion = getIdeaClassFileVersion(ideaSdk);
    switch(classFileVersion) {
      case 48: return JavaSdkVersion.JDK_1_4;
      case 49: return JavaSdkVersion.JDK_1_5;
      case 50: return JavaSdkVersion.JDK_1_6;
      case 51: return JavaSdkVersion.JDK_1_7;
    }
    return null;
  }

  public static void setupSdkPaths(final SdkModificator sdkModificator, final String sdkHome, final Sdk internalJava) {
    //roots from internal jre
    addClasses(sdkModificator, internalJava);
    addDocs(sdkModificator, internalJava);
    addSources(sdkModificator, internalJava);
    //roots for openapi and other libs
    if (!isFromIDEAProject(sdkHome)) {
      final VirtualFile[] ideaLib = getIdeaLibrary(sdkHome);
      if (ideaLib != null) {
        for (VirtualFile aIdeaLib : ideaLib) {
          sdkModificator.addRoot(aIdeaLib, OrderRootType.CLASSES);
        }
      }
      addSources(new File(sdkHome), sdkModificator);
    }
  }

  static String getDefaultSandbox() {
    @NonNls String defaultSandbox = "";
    try {
      defaultSandbox = new File(PathManager.getSystemPath()).getCanonicalPath() + File.separator + "plugins-sandbox";
    }
    catch (IOException e) {
      //can't be on running instance
    }
    return defaultSandbox;
  }

  private static void addSources(File file, SdkModificator sdkModificator) {
    final File src = new File(new File(file, LIB_DIR_NAME), SRC_DIR_NAME);
    if (!src.exists()) return;
    File[] srcs = src.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        @NonNls final String path = pathname.getPath();
        //noinspection SimplifiableIfStatement
        if (path.indexOf("generics") > -1) return false;
        return path.endsWith(".jar") || path.endsWith(".zip");
      }
    });
    for (int i = 0; srcs != null && i < srcs.length; i++) {
      File jarFile = srcs[i];
      if (jarFile.exists()) {
        JarFileSystem jarFileSystem = JarFileSystem.getInstance();
        String path = jarFile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
        jarFileSystem.setNoCopyJarForPath(path);
        VirtualFile vFile = jarFileSystem.findFileByPath(path);
        sdkModificator.addRoot(vFile, OrderRootType.SOURCES);
      }
    }
  }

  private static void addClasses(SdkModificator sdkModificator, final Sdk javaSdk) {
    addOrderEntries(OrderRootType.CLASSES, javaSdk, sdkModificator);
  }

  private static void addDocs(SdkModificator sdkModificator, final Sdk javaSdk) {
    if (!addOrderEntries(JavadocOrderRootType.getInstance(), javaSdk, sdkModificator) &&
        SystemInfo.isMac){
      Sdk[] jdks = ProjectJdkTable.getInstance().getAllJdks();
      for (Sdk jdk : jdks) {
        if (jdk.getSdkType() instanceof JavaSdk) {
          addOrderEntries(JavadocOrderRootType.getInstance(), jdk, sdkModificator);
          break;
        }
      }
    }
  }

  private static void addSources(SdkModificator sdkModificator, final Sdk javaSdk) {
    if (javaSdk != null) {
      if (!addOrderEntries(OrderRootType.SOURCES, javaSdk, sdkModificator)){
        if (SystemInfo.isMac) {
          Sdk[] jdks = ProjectJdkTable.getInstance().getAllJdks();
          for (Sdk jdk : jdks) {
            if (jdk.getSdkType() instanceof JavaSdk) {
              addOrderEntries(OrderRootType.SOURCES, jdk, sdkModificator);
              break;
            }
          }
        }
        else {
          final File jdkHome = new File(javaSdk.getHomePath()).getParentFile();
          @NonNls final String srcZip = "src.zip";
          final File jarFile = new File(jdkHome, srcZip);
          if (jarFile.exists()){
            JarFileSystem jarFileSystem = JarFileSystem.getInstance();
            String path = jarFile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
            jarFileSystem.setNoCopyJarForPath(path);
            sdkModificator.addRoot(jarFileSystem.findFileByPath(path), OrderRootType.SOURCES);
          }
        }
      }
    }
  }

  private static boolean addOrderEntries(OrderRootType orderRootType, Sdk sdk, SdkModificator toModificator){
    boolean wasSmthAdded = false;
    final String[] entries = sdk.getRootProvider().getUrls(orderRootType);
    for (String entry : entries) {
      VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(entry);
      if (virtualFile != null) {
        toModificator.addRoot(virtualFile, orderRootType);
        wasSmthAdded = true;
      }
    }
    return wasSmthAdded;
  }

  public AdditionalDataConfigurable createAdditionalDataConfigurable(final SdkModel sdkModel, SdkModificator sdkModificator) {
    return new IdeaJdkConfigurable(sdkModel, sdkModificator);
  }

  @Nullable
  public String getBinPath(Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getBinPath(internalJavaSdk);
  }

  @Nullable
  public String getToolsPath(Sdk sdk) {
    final Sdk jdk = getInternalJavaSdk(sdk);
    if (jdk != null && jdk.getVersionString() != null){
      return JavaSdk.getInstance().getToolsPath(jdk);
    }
    return null;
  }

  @Nullable
  public String getVMExecutablePath(Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getVMExecutablePath(internalJavaSdk);
  }

  public void saveAdditionalData(SdkAdditionalData additionalData, Element additional) {
    if (additionalData instanceof Sandbox) {
      try {
        ((Sandbox)additionalData).writeExternal(additional);
      }
      catch (WriteExternalException e) {
        LOG.error(e);
      }
    }
  }

  public SdkAdditionalData loadAdditionalData(Sdk sdk, Element additional) {
    Sandbox sandbox = new Sandbox(sdk);
    try {
      sandbox.readExternal(additional);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
    return sandbox;
  }

  public String getPresentableName() {
    return DevKitBundle.message("sdk.title");
  }

  @Nullable
  public static Sdk findIdeaJdk(@Nullable Sdk jdk) {
    if (jdk == null) return null;
    if (jdk.getSdkType() instanceof IdeaJdk) return jdk;
    return null;
  }

  public static SdkType getInstance() {
    return SdkType.findInstance(IdeaJdk.class);
  }

  @Override
  public boolean isRootTypeApplicable(OrderRootType type) {
    return type == OrderRootType.CLASSES ||
           type == OrderRootType.SOURCES ||
           type == JavadocOrderRootType.getInstance() ||
           type == AnnotationOrderRootType.getInstance();
  }

  public String getDefaultDocumentationUrl(final @NotNull Sdk sdk) {
    return JavaSdk.getInstance().getDefaultDocumentationUrl(sdk);
  }
}
