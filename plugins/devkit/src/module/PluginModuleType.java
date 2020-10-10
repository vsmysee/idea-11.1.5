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
package org.jetbrains.idea.devkit.module;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.*;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration;
import org.jetbrains.idea.devkit.build.PluginBuildUtil;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PluginModuleType extends ModuleType<PluginModuleBuilder> {
  private static final Icon PLUGIN_MODULE_ICON = IconLoader.getIcon("/modules/pluginModule.png");
  private static final Icon PLUGIN_MODULE_NODE_ICON = IconLoader.getIcon("/nodes/plugin.png");
  private static final Icon ADD_PLUGIN_MODULE_ICON = IconLoader.getIcon("/add_plugin_modulewizard.png");
  @NonNls private static final String ID = "PLUGIN_MODULE";

  public PluginModuleType() {
    super(ID);
  }

  public static PluginModuleType getInstance() {
    return (PluginModuleType) ModuleTypeManager.getInstance().findByID(ID);
  }

  public static boolean isOfType(Module module) {
    return get(module) instanceof PluginModuleType;
  }

  public ModuleWizardStep[] createWizardSteps(final WizardContext wizardContext,
                                              PluginModuleBuilder moduleBuilder,
                                              ModulesProvider modulesProvider) {
    final ProjectWizardStepFactory stepFactory = ProjectWizardStepFactory.getInstance();
    ArrayList<ModuleWizardStep> steps = new ArrayList<ModuleWizardStep>();
    steps.add(stepFactory.createSourcePathsStep(wizardContext, moduleBuilder, ADD_PLUGIN_MODULE_ICON, "reference.dialogs.new.project.fromScratch.source"));
    steps.add(stepFactory.createProjectJdkStep(wizardContext, IdeaJdk.getInstance(), moduleBuilder, new Computable<Boolean>() {
      public Boolean compute() {
        final Sdk projectJdk = wizardContext.getProjectJdk();
        return IdeaJdk.findIdeaJdk(projectJdk) == null ? Boolean.TRUE : Boolean.FALSE;
      }
    }, ADD_PLUGIN_MODULE_ICON, "reference.dialogs.new.project.fromScratch.sdk"));
    final ModuleWizardStep[] wizardSteps = steps.toArray(new ModuleWizardStep[steps.size()]);
    return ArrayUtil.mergeArrays(wizardSteps, super.createWizardSteps(wizardContext, moduleBuilder, modulesProvider));
  }

  public PluginModuleBuilder createModuleBuilder() {
    return new PluginModuleBuilder();
  }

  public String getName() {
    return DevKitBundle.message("module.title");
  }

  public String getDescription() {
    return DevKitBundle.message("module.description");
  }

  public Icon getBigIcon() {
    return PLUGIN_MODULE_ICON;
  }

  public Icon getNodeIcon(boolean isOpened) {
    return PLUGIN_MODULE_NODE_ICON;
  }

  @Nullable
  public static XmlFile getPluginXml(Module module) {
    return getPluginXml(module, true);
  }

  @Nullable
  public static XmlFile getPluginXml(Module module, boolean initialize) {
    if (module == null) return null;
    if (!(get(module) instanceof PluginModuleType)) return null;

    final PluginBuildConfiguration buildConfiguration = PluginBuildConfiguration.getInstance(module);
    if (buildConfiguration == null) return null;
    final VirtualFilePointer pluginXMLPointer = initialize ? buildConfiguration.getPluginXmlPointer() : buildConfiguration.getStoredPluginXmlPointer();
    final VirtualFile vFile = pluginXMLPointer != null ? pluginXMLPointer.getFile() : null;
    if (vFile == null) return null;
    final PsiFile file = PsiManager.getInstance(module.getProject()).findFile(vFile);
    return file instanceof XmlFile ? (XmlFile)file : null;
}

  public static boolean isPluginModuleOrDependency(@NotNull Module module) {
    if (isOfType(module)) return true;

    return getCandidateModules(module).size() > 0;
  }

  public static List<Module> getCandidateModules(Module module) {
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);

    final Sdk jdk = manager.getSdk();
    // don't allow modules that don't use an IDEA-JDK
    if (IdeaJdk.findIdeaJdk(jdk) == null) {
      return Collections.emptyList();
    }

    final Module[] modules = ModuleManager.getInstance(module.getProject()).getModules();
    final List<Module> candidates = new ArrayList<Module>(modules.length);
    final Set<Module> deps = new HashSet<Module>(modules.length);
    for (Module m : modules) {
      if (get(m) == getInstance()) {
        deps.clear();
        PluginBuildUtil.getDependencies(m, deps);

        if (deps.contains(module) && getPluginXml(m) != null) {
          candidates.add(m);
        }
      }
    }
    return candidates;
  }

  @Override
  public boolean isValidSdk(final Module module, final Sdk projectSdk) {
    return JavaModuleType.isValidJavaSdk(module);
  }
}