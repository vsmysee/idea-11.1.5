package org.jetbrains.jps.api;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/24/12
 */
public interface GlobalOptions {

  String USE_MEMORY_TEMP_CACHE_OPTION = "use.memory.temp.cache";
  String USE_EXTERNAL_JAVAC_OPTION = "use.external.javac.process";
  String HOSTNAME_OPTION = "localhost.name";
  String VM_EXE_PATH_OPTION = "vm.executable.path";
  String PING_INTERVAL_MS_OPTION = "server.ping.interval";
}
