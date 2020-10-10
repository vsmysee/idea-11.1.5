package org.jetbrains.jps.android;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.*;
import org.jetbrains.jps.idea.Facet;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.Paths;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.regex.Matcher;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidJpsUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidJpsUtil");

  @NonNls public static final String ANDROID_STORAGE_DIR = "android";
  @NonNls private static final String RESOURCE_CACHE_STORAGE = "res-cache";

  public static final Condition<File> CLASSES_AND_JARS_FILTER = new Condition<File>() {
    @Override
    public boolean value(File file) {
      final String ext = FileUtil.getExtension(file.getName());
      return "jar".equals(ext) || "class".equals(ext);
    }
  };
  @NonNls public static final String GENERATED_RESOURCES_DIR_NAME = "generated_resources";
  @NonNls public static final String AAPT_GENERATED_SOURCE_ROOT_NAME = "aapt";
  @NonNls public static final String AIDL_GENERATED_SOURCE_ROOT_NAME = "aidl";
  @NonNls public static final String RENDERSCRIPT_GENERATED_SOURCE_ROOT_NAME = "rs";
  @NonNls private static final String GENERATED_SOURCES_FOLDER_NAME = "generated_sources";

  private AndroidJpsUtil() {
  }

  @Nullable
  public static File getMainContentRoot(@NotNull AndroidFacet facet) throws IOException {
    final Module module = facet.getModule();

    final List<String> contentRoots = module.getContentRoots();

    if (contentRoots.size() == 0) {
      return null;
    }
    final File manifestFile = facet.getManifestFile();

    if (manifestFile != null) {
      for (String rootPath : contentRoots) {
        final File root = new File(rootPath);

        if (FileUtil.isAncestor(root, manifestFile, true)) {
          return root;
        }
      }
    }
    return new File(contentRoots.get(0));
  }

  public static void addMessages(@NotNull CompileContext context,
                                 @NotNull Map<AndroidCompilerMessageKind, List<String>> messages,
                                 @NotNull String builderName) {
    for (Map.Entry<AndroidCompilerMessageKind, List<String>> entry : messages.entrySet()) {
      for (String message : entry.getValue()) {
        String filePath = null;
        int line = -1;
        final Matcher matcher = AndroidCommonUtils.COMPILER_MESSAGE_PATTERN.matcher(message);

        if (matcher.matches()) {
          filePath = matcher.group(1);
          line = Integer.parseInt(matcher.group(2));
        }
        final BuildMessage.Kind category = toBuildMessageKind(entry.getKey());
        if (category != null) {
          context.processMessage(new CompilerMessage(builderName, category, message, filePath, -1L, -1L, -1L, line, -1L));
        }
      }
    }
  }

  @Nullable
  public static AndroidFacet getFacet(@NotNull Module module) {
    AndroidFacet androidFacet = null;

    for (Facet facet : module.getFacets().values()) {
      if (facet instanceof AndroidFacet) {
        androidFacet = (AndroidFacet)facet;
      }
    }
    return androidFacet;
  }

  @NotNull
  public static String[] toPaths(@NotNull File[] files) {
    final String[] result = new String[files.length];

    for (int i = 0; i < result.length; i++) {
      result[i] = files[i].getPath();
    }
    return result;
  }

  @NotNull
  public static List<String> toPaths(@NotNull Collection<File> files) {
    if (files.size() == 0) {
      return Collections.emptyList();
    }

    final List<String> result = new ArrayList<String>(files.size());
    for (File file : files) {
      result.add(file.getPath());
    }
    return result;
  }

  @Nullable
  public static File getOutputDirectoryForPackagedFiles(@NotNull ProjectPaths paths, @NotNull Module module) {
    // todo: return build directory for mavenized modules to place .dex and .apk files into target dir (not target/classes)
    return paths.getModuleOutputDir(module, false);
  }

  public static void addSubdirectories(@NotNull File baseDir, @NotNull Collection<String> result) {
    // only include files inside packages
    final File[] children = baseDir.listFiles();

    if (children != null) {
      for (File child : children) {
        if (child.isDirectory()) {
          result.add(child.getPath());
        }
      }
    }
  }

  @NotNull
  public static Set<String> getExternalLibraries(@NotNull ProjectPaths paths, @NotNull Module module) {
    final Set<String> result = new HashSet<String>();
    fillClasspath(paths, module, null, result, new HashSet<String>(), false);

    // todo: add annotations.jar to result
    return result;
  }

  @NotNull
  public static Set<String> getClassdirsOfDependentModulesAndPackagesLibraries(@NotNull ProjectPaths paths, @NotNull Module module) {
    final Set<String> result = new HashSet<String>();
    fillClasspath(paths, module, result, null, new HashSet<String>(), false);
    return result;
  }

  private static void fillClasspath(@NotNull ProjectPaths paths,
                                    @NotNull final Module module,
                                    @Nullable final Set<String> outputDirs,
                                    @Nullable final Set<String> libraries,
                                    @NotNull final Set<String> visitedModules,
                                    final boolean exportedLibrariesOnly) {
    if (!visitedModules.add(module.getName())) {
      return;
    }

    if (libraries != null) {
      // todo: do not include provided libs there
      for (ClasspathItem item : module.getClasspath(ClasspathKind.PRODUCTION_RUNTIME, exportedLibrariesOnly)) {
        if (item instanceof Library && !(item instanceof Sdk)) {
          for (Object filePathObj : ((Library)item).getClasspath()) {
            final String filePath = (String)filePathObj;
            final File file = new File(filePath);

            if (file.exists()) {
              processClassFilesAndJarsRecursively(filePath, new Processor<File>() {
                @Override
                public boolean process(File file) {
                  libraries.add(file.getPath());
                  return true;
                }
              });
            }
          }
        }
      }
    }

    for (ClasspathItem item : module.getClasspath(ClasspathKind.PRODUCTION_RUNTIME, false)) {
      if (item instanceof Module) {
        final Module depModule = (Module)item;
        final AndroidFacet depFacet = getFacet(depModule);
        final boolean depLibrary = depFacet != null && depFacet.isLibrary();
        final File depClassDir = paths.getModuleOutputDir(depModule, false);

        if (outputDirs != null && depClassDir != null) {
          if (depLibrary) {
            final File packagedClassesJar = new File(depClassDir, AndroidCommonUtils.CLASSES_JAR_FILE_NAME);

            if (packagedClassesJar.isFile()) {
              outputDirs.add(packagedClassesJar.getPath());
            }
          }
          else if (depFacet == null && depClassDir.isDirectory()) {
            // do not support android-app->android-app compile dependencies
            outputDirs.add(depClassDir.getPath());
          }
        }
        fillClasspath(paths, depModule, outputDirs, libraries, visitedModules, !depLibrary || exportedLibrariesOnly);
      }
    }
  }

  public static void processClassFilesAndJarsRecursively(@NotNull String root, @NotNull final Processor<File> processor) {
    FileUtil.processFilesRecursively(new File(root), new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (file.isFile()) {
          final String ext = FileUtil.getExtension(file.getName());

          // NOTE: we should ignore apklib dependencies (IDEA-82976)
          if ("jar".equals(ext) || "class".equals(ext)) {
            if (!processor.process(file)) {
              return false;
            }
          }
        }
        return true;
      }
    });
  }

  @Nullable
  public static IAndroidTarget parseAndroidTarget(@NotNull AndroidSdk sdk, @NotNull CompileContext context, @NotNull String builderName) {
    final String targetHashString = sdk.getBuildTargetHashString();
    if (targetHashString == null) {
      context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                 "Cannot parse SDK " + sdk.getName() + ": build target is not specified"));
      return null;
    }

    final MessageBuildingSdkLog log = new MessageBuildingSdkLog();
    final SdkManager manager = AndroidCommonUtils.createSdkManager(sdk.getSdkPath(), log);

    if (manager == null) {
      final String message = log.getErrorMessage();
      context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                 "Android SDK is parsed incorrectly." +
                                                 (message.length() > 0 ? " Parsing log:\n" + message : "")));
      return null;
    }

    final IAndroidTarget target = manager.getTargetFromHashString(targetHashString);
    if (target == null) {
      context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                 "Cannot parse SDK '" + sdk.getName() + "': unknown target " + targetHashString));
      return null;
    }
    return target;
  }

  @Nullable
  public static BuildMessage.Kind toBuildMessageKind(@NotNull AndroidCompilerMessageKind kind) {
    switch (kind) {
      case ERROR:
        return BuildMessage.Kind.ERROR;
      case INFORMATION:
        return BuildMessage.Kind.INFO;
      case WARNING:
        return BuildMessage.Kind.WARNING;
      default:
        LOG.error("unknown AndroidCompilerMessageKind object " + kind);
        return null;
    }
  }

  public static void reportExceptionError(@NotNull CompileContext context,
                                          @Nullable String filePath,
                                          @NotNull Exception exception,
                                          @NotNull String builderName) {
    final String message = exception.getMessage();

    if (message != null) {
      context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR, message, filePath));
      LOG.debug(exception);
    }
    else {
      context.processMessage(new CompilerMessage(builderName, exception));
    }
  }

  public static boolean containsAndroidFacet(@NotNull ModuleChunk chunk) {
    for (Module module : chunk.getModules()) {
      if (getFacet(module) != null) {
        return true;
      }
    }
    return false;
  }

  public static boolean containsAndroidFacet(@NotNull Project project) {
    for (Module module : project.getModules().values()) {
      if (getFacet(module) != null) {
        return true;
      }
    }
    return false;
  }

  public static ModuleLevelBuilder.ExitCode handleException(@NotNull CompileContext context,
                                                            @NotNull Exception e,
                                                            @NotNull String builderName)
    throws ProjectBuildException {
    String message = e.getMessage();

    if (message == null) {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      //noinspection IOResourceOpenedButNotSafelyClosed
      e.printStackTrace(new PrintStream(out));
      message = "Internal error: \n" + out.toString();
    }
    context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR, message));
    throw new ProjectBuildException(message, e);
  }

  @Nullable
  public static File getManifestFileForCompilationPath(@NotNull AndroidFacet facet) throws IOException {
    return facet.getUseCustomManifestForCompilation()
           ? facet.getManifestFileForCompilation()
           : facet.getManifestFile();
  }

  @Nullable
  public static Pair<AndroidSdk, IAndroidTarget> getAndroidPlatform(@NotNull Module module,
                                                                    @NotNull CompileContext context,
                                                                    @NotNull String builderName) {
    final Sdk sdk = module.getSdk();
    if (!(sdk instanceof AndroidSdk)) {
      context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                 AndroidJpsBundle.message("android.jps.errors.sdk.not.specified", module.getName())));
      return null;
    }
    final AndroidSdk androidSdk = (AndroidSdk)sdk;

    final IAndroidTarget target = parseAndroidTarget(androidSdk, context, builderName);
    if (target == null) {
      context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                 AndroidJpsBundle.message("android.jps.errors.sdk.invalid", module.getName())));
      return null;
    }
    return Pair.create(androidSdk, target);
  }

  public static String[] collectResourceDirsForCompilation(@NotNull AndroidFacet facet,
                                                           boolean withCacheDirs,
                                                           @NotNull CompileContext context) throws IOException {
    final List<String> result = new ArrayList<String>();

    if (withCacheDirs) {
      final File resourcesCacheDir = getResourcesCacheDir(context, facet.getModule());
      if (resourcesCacheDir.exists()) {
        result.add(resourcesCacheDir.getPath());
      }
    }

    final File resDir = getResourceDirForCompilationPath(facet);
    if (resDir != null) {
      result.add(resDir.getPath());
    }

    final File generatedResourcesStorage = getGeneratedResourcesStorage(facet.getModule());
    if (generatedResourcesStorage.exists()) {
      result.add(generatedResourcesStorage.getPath());
    }

    for (AndroidFacet depFacet : getAllDependentAndroidLibraries(facet.getModule())) {
      final File depResDir = getResourceDirForCompilationPath(depFacet);
      if (depResDir != null) {
        result.add(depResDir.getPath());
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  @Nullable
  public static File getResourceDirForCompilationPath(@NotNull AndroidFacet facet) throws IOException {
    return facet.getUseCustomResFolderForCompilation()
           ? facet.getResourceDirForCompilation()
           : facet.getResourceDir();
  }

  @NotNull
  static List<AndroidFacet> getAllDependentAndroidLibraries(@NotNull Module module) {
    final List<AndroidFacet> result = new ArrayList<AndroidFacet>();
    collectDependentAndroidLibraries(module, result, new HashSet<String>());
    return result;
  }

  private static void collectDependentAndroidLibraries(@NotNull Module module,
                                                       @NotNull List<AndroidFacet> result,
                                                       @NotNull Set<String> visitedSet) {
    for (ClasspathItem item : module.getClasspath(ClasspathKind.PRODUCTION_RUNTIME, false)) {
      if (item instanceof Module) {
        final Module depModule = (Module)item;
        final AndroidFacet depFacet = getFacet(depModule);

        if (depFacet != null && depFacet.getLibrary() && visitedSet.add(module.getName())) {
          collectDependentAndroidLibraries(depModule, result, visitedSet);
          result.add(0, depFacet);
        }
      }
    }
  }

  public static boolean isLightBuild(@NotNull CompileContext context) {
    return Boolean.parseBoolean(context.getBuilderParameter(AndroidCommonUtils.LIGHT_BUILD_OPTION));
  }

  public static boolean isReleaseBuild(@NotNull CompileContext context) {
    return Boolean.parseBoolean(context.getBuilderParameter(AndroidCommonUtils.RELEASE_BUILD_OPTION));
  }

  @NotNull
  public static File getResourcesCacheDir(@NotNull CompileContext context, @NotNull Module module) {
    final File androidStorage = new File(context.getDataManager().getDataStorageRoot(), ANDROID_STORAGE_DIR);
    return new File(new File(androidStorage, RESOURCE_CACHE_STORAGE), module.getName());
  }

  private static void fillSourceRoots(@NotNull Module module, @NotNull Set<Module> visited, @NotNull Set<File> result)
    throws IOException {
    visited.add(module);
    final AndroidFacet facet = getFacet(module);
    File resDir = null;
    File resDirForCompilation = null;

    if (facet != null) {
      resDir = facet.getResourceDir();
      resDirForCompilation = facet.getResourceDirForCompilation();
    }

    for (String sourceRootPath : module.getSourceRoots()) {
      final File sourceRoot = new File(sourceRootPath).getCanonicalFile();

      if (!sourceRoot.equals(resDir) && !sourceRoot.equals(resDirForCompilation)) {
        result.add(sourceRoot);
      }
    }

    if (facet != null && facet.isPackTestCode()) {
      for (String testRootPath : module.getTestRoots()) {
        final File testRoot = new File(testRootPath).getCanonicalFile();

        if (!testRoot.equals(resDir) && !testRoot.equals(resDirForCompilation)) {
          result.add(testRoot);
        }
      }
    }

    for (ClasspathItem classpathItem : module.getClasspath(ClasspathKind.PRODUCTION_RUNTIME)) {
      if (classpathItem instanceof Module) {
        final Module depModule = (Module)classpathItem;

        if (!visited.contains(depModule)) {
          fillSourceRoots(depModule, visited, result);
        }
      }
    }
  }

  @NotNull
  public static File[] getSourceRootsForModuleAndDependencies(@NotNull Module module) throws IOException {
    Set<File> result = new HashSet<File>();
    fillSourceRoots(module, new HashSet<Module>(), result);
    return result.toArray(new File[result.size()]);
  }

  @Nullable
  public static String getApkPath(@NotNull AndroidFacet facet, @NotNull File outputDirForPackagedArtifacts) {
    final String apkRelativePath = facet.getApkRelativePath();
    final Module module = facet.getModule();

    if (apkRelativePath.length() == 0) {
      return new File(outputDirForPackagedArtifacts, getApkName(module)).getPath();
    }
    final String moduleDirPath = module.getBasePath();

    return moduleDirPath != null
           ? FileUtil.toSystemDependentName(moduleDirPath + apkRelativePath)
           : null;
  }

  @NotNull
  public static String getApkName(@NotNull Module module) {
    return module.getName() + ".apk";
  }

  @NotNull
  public static File getGeneratedSourcesStorage(@NotNull Module module) {
    final File dataStorageRoot = Paths.getDataStorageRoot(module.getProject());
    final File androidStorageRoot = new File(dataStorageRoot, ANDROID_STORAGE_DIR);
    final File generatedSourcesRoot = new File(androidStorageRoot, GENERATED_SOURCES_FOLDER_NAME);
    return new File(generatedSourcesRoot, module.getName());
  }

  @NotNull
  public static File getGeneratedResourcesStorage(@NotNull Module module) {
    final File dataStorageRoot = Paths.getDataStorageRoot(module.getProject());
    final File androidStorageRoot = new File(dataStorageRoot, ANDROID_STORAGE_DIR);
    final File generatedSourcesRoot = new File(androidStorageRoot, GENERATED_RESOURCES_DIR_NAME);
    return new File(generatedSourcesRoot, module.getName());
  }
}
