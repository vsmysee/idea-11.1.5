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
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenConsoleImpl;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.File;

public class MavenRunConfiguration extends RunConfigurationBase implements LocatableConfiguration, ModuleRunProfile {
  private MavenSettings mySettings;

  protected MavenRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(project, factory, name);
    mySettings = new MavenSettings(project);
  }

  public MavenRunConfiguration clone() {
    MavenRunConfiguration clone = (MavenRunConfiguration)super.clone();
    clone.mySettings = mySettings.clone();
    return clone;
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new MavenRunConfigurationSettings(getProject());
  }

  public JDOMExternalizable createRunnerSettings(ConfigurationInfoProvider provider) {
    return null;
  }

  public SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(ProgramRunner runner) {
    return null;
  }

  public JavaParameters createJavaParameters(@Nullable Project project) throws ExecutionException {
    return MavenExternalParameters
      .createJavaParameters(project, mySettings.myRunnerParameters, mySettings.myGeneralSettings, mySettings.myRunnerSettings, this);
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    JavaCommandLineState state = new JavaCommandLineState(env) {
      protected JavaParameters createJavaParameters() throws ExecutionException {
        return MavenRunConfiguration.this.createJavaParameters(env.getProject());
      }

      @Override
      public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        DefaultExecutionResult res = (DefaultExecutionResult)super.execute(executor, runner);
        if (res != null) {
          if (executor.getId().equals(ToolWindowId.RUN)
              && MavenResumeAction.isApplicable(env.getProject(), getJavaParameters(), MavenRunConfiguration.this)) {
            MavenResumeAction resumeAction = new MavenResumeAction(res.getProcessHandler(), runner, executor, env);
            res.setRestartActions(resumeAction);
          }
        }
        return res;
      }

      @NotNull
      @Override
      protected OSProcessHandler startProcess() throws ExecutionException {
        OSProcessHandler result = super.startProcess();
        result.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(ProcessEvent event) {
            updateProjectsFolders();
          }
        });
        return result;
      }
    };
    state.setConsoleBuilder(MavenConsoleImpl.createConsoleBuilder(getProject()));
    return state;
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    String filePath = mySettings.myRunnerParameters.getPomFilePath();
    if (filePath == null || !new File(filePath).isFile()) {
      throw new RuntimeConfigurationError(RunnerBundle.message("maven.run.configuration.error.file.not.found"));
    }
  }

  private void updateProjectsFolders() {
    MavenProjectsManager.getInstance(getProject()).updateProjectTargetFolders();
  }

  @NotNull
  public Module[] getModules() {
    return Module.EMPTY_ARRAY;
  }

  @Nullable
  public MavenGeneralSettings getGeneralSettings() {
    return mySettings.myGeneralSettings;
  }

  public void setGeneralSettings(@Nullable MavenGeneralSettings settings) {
    mySettings.myGeneralSettings = settings;
  }

  @Nullable
  public MavenRunnerSettings getRunnerSettings() {
    return mySettings.myRunnerSettings;
  }

  public void setRunnerSettings(@Nullable MavenRunnerSettings settings) {
    mySettings.myRunnerSettings = settings;
  }

  public MavenRunnerParameters getRunnerParameters() {
    return mySettings.myRunnerParameters;
  }

  public void setRunnerParameters(MavenRunnerParameters p) {
    mySettings.myRunnerParameters = p;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    Element mavenSettingsElement = element.getChild(MavenSettings.TAG);
    if (mavenSettingsElement != null) {
      mySettings = XmlSerializer.deserialize(mavenSettingsElement, MavenSettings.class);
      if (mySettings == null) mySettings = new MavenSettings();

      if (mySettings.myRunnerParameters == null) mySettings.myRunnerParameters = new MavenRunnerParameters();

      // fix old settings format
      mySettings.myRunnerParameters.fixAfterLoadingFromOldFormat();
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.addContent(XmlSerializer.serialize(mySettings));
  }

  public boolean isGeneratedName() {
    return Comparing.equal(getName(), getGeneratedName());
  }

  public String suggestedName() {
    return getGeneratedName();
  }

  private String getGeneratedName() {
    return MavenRunConfigurationType.generateName(getProject(), mySettings.myRunnerParameters);
  }

  public static class MavenSettings implements Cloneable {
    public static final String TAG = "MavenSettings";

    public MavenGeneralSettings myGeneralSettings;
    public MavenRunnerSettings myRunnerSettings;
    public MavenRunnerParameters myRunnerParameters;

    /* reflection only */
    public MavenSettings() {
    }

    public MavenSettings(Project project) {
      this(null, null, new MavenRunnerParameters());
    }

    private MavenSettings(@Nullable MavenGeneralSettings cs, @Nullable MavenRunnerSettings rs, MavenRunnerParameters rp) {
      myGeneralSettings = cs == null ? null : cs.clone();
      myRunnerSettings = rs == null ? null : rs.clone();
      myRunnerParameters = rp.clone();
    }

    protected MavenSettings clone() {
      return new MavenSettings(myGeneralSettings, myRunnerSettings, myRunnerParameters);
    }
  }
}
