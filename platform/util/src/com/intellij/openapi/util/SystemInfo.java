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
package com.intellij.openapi.util;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral", "UtilityClassWithoutPrivateConstructor", "UnusedDeclaration"})
public class SystemInfo {
  public static final String OS_NAME = System.getProperty("os.name");
  public static final String OS_VERSION = System.getProperty("os.version").toLowerCase();
  public static final String OS_ARCH = System.getProperty("os.arch");
  public static final String JAVA_VERSION = System.getProperty("java.version");
  public static final String JAVA_RUNTIME_VERSION = System.getProperty("java.runtime.version");
  public static final String ARCH_DATA_MODEL = System.getProperty("sun.arch.data.model");
  public static final String SUN_DESKTOP = System.getProperty("sun.desktop", "");

  private static final String _OS_NAME = OS_NAME.toLowerCase();
  public static final boolean isWindows = _OS_NAME.startsWith("windows");
  public static final boolean isWindowsNT = _OS_NAME.startsWith("windows nt");
  public static final boolean isWindows2000 = _OS_NAME.startsWith("windows 2000");
  public static final boolean isWindows2003 = _OS_NAME.startsWith("windows 2003");
  public static final boolean isWindowsXP = _OS_NAME.startsWith("windows xp");
  public static final boolean isWindowsVista = _OS_NAME.startsWith("windows vista");
  public static final boolean isWindows7 = _OS_NAME.startsWith("windows 7");
  public static final boolean isWindows9x = _OS_NAME.startsWith("windows 9") || _OS_NAME.startsWith("windows me");
  public static final boolean isOS2 = _OS_NAME.startsWith("os/2") || _OS_NAME.startsWith("os2");
  public static final boolean isMac = _OS_NAME.startsWith("mac");
  public static final boolean isFreeBSD = _OS_NAME.startsWith("freebsd");
  public static final boolean isLinux = _OS_NAME.startsWith("linux");
  public static final boolean isSolaris = _OS_NAME.startsWith("sunos");
  public static final boolean isUnix = !isWindows && !isOS2;

  private static final String _SUN_DESKTOP = SUN_DESKTOP.toLowerCase();
  public static final boolean isKDE = _SUN_DESKTOP.contains("kde");
  public static final boolean isGnome = _SUN_DESKTOP.contains("gnome");

  public static final boolean hasXdgOpen = isUnix && new File("/usr/bin/xdg-open").canExecute();

  public static final boolean isMacSystemMenu = isMac && "true".equals(System.getProperty("apple.laf.useScreenMenuBar"));

  public static final boolean isFileSystemCaseSensitive = !isWindows && !isOS2 && !isMac;
  public static final boolean areSymLinksSupported = isUnix ||
                                                     isWindows && OS_VERSION.compareTo("6.0") >= 0 && isJavaVersionAtLeast("1.7");

  public static final boolean is32Bit = ARCH_DATA_MODEL == null || ARCH_DATA_MODEL.equals("32");
  public static final boolean is64Bit = !is32Bit;
  public static final boolean isAMD64 = "amd64".equals(OS_ARCH);
  public static final boolean isMacIntel64 = isMac && "x86_64".equals(OS_ARCH);

  public static final String nativeFileManagerName = isMac ? "Finder" :
                                                     isGnome ? "Nautilus" :
                                                     isKDE ? "Konqueror" :
                                                     isWindows ? "Explorer" :
                                                     "File Manager";

  /**
   * Whether IDEA is running under MacOS X version 10.4 or later.
   *
   * @since 5.0.2
   */
  public static final boolean isMacOSTiger = isTiger();

  /**
   * Whether IDEA is running under MacOS X on an Intel Machine
   *
   * @since 5.0.2
   */
  public static final boolean isIntelMac = isIntelMac();

  /**
   * Running under MacOS X version 10.5 or later;
   *
   * @since 7.0.2
   */
  public static final boolean isMacOSLeopard = isLeopard();

  /**
   * Running under MacOS X version 10.6 or later;
   *
   * @since 9.0
   */
  public static final boolean isMacOSSnowLeopard = isSnowLeopard();

  /**
   * Running under MacOS X version 10.7 or later;
   *
   * @since 11.0
   */
  public static final boolean isMacOSLion = isLion();

  /**
   * Running under MacOS X version 10.8 or later;
   *
   * @since 11.1
   */
  public static final boolean isMacOSMountainLion = isMountainLion();

  /**
   * Operating system is supposed to have middle mouse button click occupied by paste action.
   *
   * @since 6.0
   */
  public static boolean X11PasteEnabledSystem = isUnix && !isMac;

  private static boolean isIntelMac() {
    return isMac && "i386".equals(OS_ARCH);
  }

  private static boolean isTiger() {
    return isMac &&
           !OS_VERSION.startsWith("10.0") &&
           !OS_VERSION.startsWith("10.1") &&
           !OS_VERSION.startsWith("10.2") &&
           !OS_VERSION.startsWith("10.3");
  }

  private static boolean isLeopard() {
    return isMac && isTiger() && !OS_VERSION.startsWith("10.4");
  }

  private static boolean isSnowLeopard() {
    return isMac && isLeopard() && !OS_VERSION.startsWith("10.5");
  }

  private static boolean isLion() {
    return isMac && isSnowLeopard() && !OS_VERSION.startsWith("10.6");
  }

  private static boolean isMountainLion() {
    return isMac && isLion() && !OS_VERSION.startsWith("10.7");
  }

  @NotNull
  public static String getMacOSMajorVersion() {
    return getMacOSMajorVersion(OS_VERSION);
  }

  public static String getMacOSMajorVersion(String version) {
    int[] parts = getMacOSVersionParts(version);
    return String.format("%d.%d", parts[0], parts[1]);
  }

  @NotNull
  public static String getMacOSVersionCode() {
    return getMacOSVersionCode(OS_VERSION);
  }

  @NotNull
  public static String getMacOSMajorVersionCode() {
    return getMacOSMajorVersionCode(OS_VERSION);
  }

  @NotNull
  public static String getMacOSMinorVersionCode() {
    return getMacOSMinorVersionCode(OS_VERSION);
  }

  @NotNull
  public static String getMacOSVersionCode(@NotNull String version) {
    int[] parts = getMacOSVersionParts(version);
    return String.format("%02d%d%d", parts[0], normalize(parts[1]), normalize(parts[2]));
  }

  @NotNull
  public static String getMacOSMajorVersionCode(@NotNull String version) {
    int[] parts = getMacOSVersionParts(version);
    return String.format("%02d%d%d", parts[0], normalize(parts[1]), 0);
  }

  @NotNull
  public static String getMacOSMinorVersionCode(@NotNull String version) {
    int[] parts = getMacOSVersionParts(version);
    return String.format("%02d%02d", parts[1], parts[2]);
  }

  private static int[] getMacOSVersionParts(@NotNull String version) {
    List<String> parts = StringUtil.split(version, ".");
    while (parts.size() < 3) {
      parts.add("0");
    }
    return new int[]{toInt(parts.get(0)), toInt(parts.get(1)), toInt(parts.get(2))};
  }

  private static int normalize(int number) {
    return number > 9 ? 9 : number;
  }

  private static int toInt(String string) {
    try {
      return Integer.valueOf(string);
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  public static boolean isJavaVersionAtLeast(String v) {
    return StringUtil.compareVersionNumbers(JAVA_RUNTIME_VERSION, v) >= 0;
  }

  public static int getIntProperty(@NotNull final String key, final int defaultValue) {
    final String value = System.getProperty(key);
    if (value != null) {
      try {
        return Integer.parseInt(value);
      }
      catch (NumberFormatException ignored) {
      }
    }

    return defaultValue;
  }
}
