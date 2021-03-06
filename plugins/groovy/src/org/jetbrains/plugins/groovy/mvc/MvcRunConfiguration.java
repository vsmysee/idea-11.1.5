/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.mvc;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.DefaultJavaProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * @author peter
 */
public abstract class MvcRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule> implements
                                                                                                   CommonJavaRunConfigurationParameters {
  public String vmParams;
  public String cmdLine;
  public boolean depsClasspath = true;
  protected final MvcFramework myFramework;
  public final Map<String, String> envs = new HashMap<String, String>();
  public boolean passParentEnv = true;

  public MvcRunConfiguration(final String name, final RunConfigurationModule configurationModule, final ConfigurationFactory factory, MvcFramework framework) {
    super(name, configurationModule, factory);
    myFramework = framework;
  }

  public MvcFramework getFramework() {
    return myFramework;
  }

  public String getVMParameters() {
    return vmParams;
  }

  public void setVMParameters(String vmParams) {
    this.vmParams = vmParams;
  }

  public void setProgramParameters(@Nullable String value) {
    cmdLine = value;
  }

  @Nullable
  public String getProgramParameters() {
    return cmdLine;
  }

  public void setWorkingDirectory(@Nullable String value) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public String getWorkingDirectory() {
    return null;
  }

  public void setEnvs(@NotNull Map<String, String> envs) {
    this.envs.clear();
    this.envs.putAll(envs);
  }

  @NotNull
  public Map<String, String> getEnvs() {
    return envs;
  }

  public void setPassParentEnvs(boolean passParentEnv) {
    this.passParentEnv = passParentEnv;
  }

  public boolean isPassParentEnvs() {
    return passParentEnv;
  }

  public boolean isAlternativeJrePathEnabled() {
    return false;
  }

  public void setAlternativeJrePathEnabled(boolean enabled) {
    throw new UnsupportedOperationException();
  }

  public String getAlternativeJrePath() {
    return null;
  }

  public void setAlternativeJrePath(String path) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public String getRunClass() {
    return null;
  }

  @Nullable
  public String getPackage() {
    return null;
  }


  public Collection<Module> getValidModules() {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    ArrayList<Module> res = new ArrayList<Module>();
    for (Module module : modules) {
      if (isSupport(module)) {
        res.add(module);
      }
    }
    return res;
  }

  public void readExternal(Element element) throws InvalidDataException {
    PathMacroManager.getInstance(getProject()).expandPaths(element);
    super.readExternal(element);
    readModule(element);
    vmParams = JDOMExternalizer.readString(element, "vmparams");
    cmdLine = JDOMExternalizer.readString(element, "cmdLine");

    String sPassParentEnviroment = JDOMExternalizer.readString(element, "passParentEnv");
    passParentEnv = StringUtil.isEmpty(sPassParentEnviroment) ? true : Boolean.parseBoolean(sPassParentEnviroment);

    envs.clear();
    JDOMExternalizer.readMap(element, envs, null, "env");

    JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element);

    depsClasspath = !"false".equals(JDOMExternalizer.readString(element, "depsClasspath"));
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeModule(element);
    JDOMExternalizer.write(element, "vmparams", vmParams);
    JDOMExternalizer.write(element, "cmdLine", cmdLine);
    JDOMExternalizer.write(element, "depsClasspath", depsClasspath);
    JDOMExternalizer.writeMap(element, envs, null, "env");
    JDOMExternalizer.write(element, "passParentEnv", passParentEnv);

    JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, element);

    PathMacroManager.getInstance(getProject()).collapsePathsRecursively(element);
  }

  protected abstract String getNoSdkMessage();

  protected boolean isSupport(@NotNull Module module) {
    return myFramework.getSdkRoot(module) != null && !myFramework.isAuxModule(module);
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    final Module module = getModule();
    if (module == null) {
      throw new RuntimeConfigurationException("Module not specified");
    }
    if (module.isDisposed()) {
      throw new RuntimeConfigurationException("Module is disposed");
    }
    if (!isSupport(module)) {
      throw new RuntimeConfigurationException(getNoSdkMessage());
    }
    super.checkConfiguration();
  }

  @Nullable
  public Module getModule() {
    return getConfigurationModule().getModule();
  }

  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    final Module module = getModule();
    if (module == null) {
      throw new ExecutionException("Module is not specified");
    }

    if (!isSupport(module)) {
      throw new ExecutionException(getNoSdkMessage());
    }

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final Sdk sdk = rootManager.getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof JavaSdkType)) {
      throw CantRunException.noJdkForModule(module);
    }

    final JavaCommandLineState state = createCommandLineState(environment, module);
    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    return state;

  }

  protected MvcCommandLineState createCommandLineState(@NotNull ExecutionEnvironment environment, Module module) {
    return new MvcCommandLineState(environment, module, false);
  }

  public SettingsEditor<? extends MvcRunConfiguration> getConfigurationEditor() {
    return new MvcRunConfigurationEditor<MvcRunConfiguration>();
  }

  public class MvcCommandLineState extends JavaCommandLineState {
    protected final boolean myForTests;

    protected String myCmdLine;

    protected final Module myModule;

    public MvcCommandLineState(@NotNull ExecutionEnvironment environment, Module module, boolean forTests) {
      super(environment);
      myModule = module;
      myForTests = forTests;
      myCmdLine = cmdLine;
    }

    public String getCmdLine() {
      return myCmdLine;
    }

    public void setCmdLine(String cmdLine) {
      myCmdLine = cmdLine;
    }

    protected void addEnvVars(final JavaParameters params) {
      Map<String, String> envVars = new HashMap<String, String>(envs);

      Map<String, String> oldEnv = params.getEnv();
      if (oldEnv != null) {
        envVars.putAll(oldEnv);
      }

      params.setupEnvs(envVars, passParentEnv);
      
      MvcFramework.addJavaHome(params, myModule);
    }

    @NotNull
    @Override
    protected OSProcessHandler startProcess() throws ExecutionException {
      final OSProcessHandler handler = new DefaultJavaProcessHandler(createCommandLine()) {
        @Override
        protected boolean shouldDestroyProcessRecursively() {
          return true;
        }
      };
      ProcessTerminatedListener.attach(handler);

      final RunnerSettings runnerSettings = getRunnerSettings();
      JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(MvcRunConfiguration.this, handler, runnerSettings);

      return handler;
    }

    protected final JavaParameters createJavaParameters() throws ExecutionException {
      JavaParameters javaParameters = createJavaParametersMVC();
      for(RunConfigurationExtension ext: Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
        ext.updateJavaParameters(MvcRunConfiguration.this, javaParameters, getRunnerSettings());
      }

      return javaParameters;
    }

    protected JavaParameters createJavaParametersMVC() throws ExecutionException {
      Pair<String, String[]> parsedCmd = MvcFramework.parsedCmd(myCmdLine);

      final JavaParameters params = myFramework.createJavaParameters(myModule, false, myForTests, depsClasspath, vmParams, parsedCmd.first,
                                                                     parsedCmd.second);

      addEnvVars(params);

      return params;
    }

  }

}
