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

package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author spleaner
 */
public class ProgramRunnerUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.ProgramRunnerUtil");
  private static final Icon INVALID_CONFIGURATION = IconLoader.getIcon("/runConfigurations/invalidConfigurationLayer.png");

  private ProgramRunnerUtil() {
  }

  @Nullable
  public static ProgramRunner getRunner(final String executorId, final RunnerAndConfigurationSettings configuration) {
    return RunnerRegistry.getInstance().getRunner(executorId, configuration.getConfiguration());
  }

  public static void executeConfiguration(@NotNull final Project project, @NotNull final RunnerAndConfigurationSettings configuration,
                                            @NotNull final Executor executor, final boolean showSettings) {
    ProgramRunner runner = getRunner(executor.getId(), configuration);
    if (runner == null) {
      LOG.error("Runner MUST not be null! Cannot find runner for " + executor.getId() + " and " + configuration.getConfiguration().getFactory().getName());
      return;
    }
    if (ExecutorRegistry.getInstance().isStarting(project, executor.getId(), runner.getRunnerId())){
      return;
    }

    if (!RunManagerImpl.canRunConfiguration(configuration, executor) || (showSettings && RunManagerImpl.isEditBeforeRun(configuration))) {
      final boolean result = RunDialog.editConfiguration(project, configuration, "Edit configuration", executor.getActionName(), executor.getIcon());
      if (!result) {
        return;
      }

      while (!RunManagerImpl.canRunConfiguration(configuration, executor)) {
        if (0 == Messages.showYesNoDialog(project, "Configuration is still wrong. Do you want to edit it again?", "Change configuration settings", Messages.getErrorIcon())) {
          final boolean result2 = RunDialog.editConfiguration(project, configuration, "Edit configuration", executor.getActionName(), executor.getIcon());
          if (!result2) {
            return;
          }
        } else {
          break;
        }
      }
    }

    final ConfigurationType configurationType = configuration.getType();
    if (configurationType != null) {
      UsageTrigger.trigger("execute." + ConvertUsagesUtil.ensureProperKey(configurationType.getId()) + "." + executor.getId());
    }

    try {
      runner.execute(executor, new ExecutionEnvironment(runner, configuration, project));
    }
    catch (ExecutionException e) {
      ExecutionUtil.handleExecutionError(project, executor.getToolWindowId(), configuration.getConfiguration(), e);
    }
  }

  public static void executeConfiguration(@NotNull final Project project, @NotNull final RunnerAndConfigurationSettings configuration,
                                          @NotNull final Executor executor) {
    executeConfiguration(project, configuration, executor, true);
  }

  public static Icon getConfigurationIcon(final Project project, final RunnerAndConfigurationSettings settings, final boolean invalid) {
    final RunManager runManager = RunManager.getInstance(project);
    return getConfigurationIcon(settings, invalid, runManager.isTemporary(settings.getConfiguration()));
  }

  public static Icon getConfigurationIcon(final RunnerAndConfigurationSettings settings,
                                          final boolean invalid,
                                          boolean isTemporary) {
    RunConfiguration configuration = settings.getConfiguration();
    final Icon icon = settings.getFactory().getIcon(configuration);
    LOG.assertTrue(icon != null, "Icon should not be null!");

    final Icon configurationIcon = isTemporary ? IconLoader.getTransparentIcon(icon, 0.3f) : icon;
    if (invalid) {
      return LayeredIcon.create(configurationIcon, INVALID_CONFIGURATION);
    }

    return configurationIcon;
  }

  public static String shortenName(final String name, final int toBeAdded) {
    if (name == null) return "";
    final int symbols = Math.max(10, 20 - toBeAdded);
    if (name.length() < symbols) return name;
    else return name.substring(0, symbols) + "...";
  }
}
