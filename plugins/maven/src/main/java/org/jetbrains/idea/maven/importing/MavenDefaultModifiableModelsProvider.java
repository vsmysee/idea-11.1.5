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
package org.jetbrains.idea.maven.importing;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collection;

public class MavenDefaultModifiableModelsProvider extends MavenBaseModifiableModelsProvider {
  private final LibraryTable.ModifiableModel myLibrariesModel;

  private static final Logger LOG = Logger.getInstance("#" + MavenDefaultModifiableModelsProvider.class.getName());

  public MavenDefaultModifiableModelsProvider(Project project) {
    super(project);
    myLibrariesModel = ProjectLibraryTable.getInstance(myProject).getModifiableModel();
  }

  @Override
  protected ModifiableArtifactModel doGetArtifactModel() {
    return new ReadAction<ModifiableArtifactModel>() {
      protected void run(final Result<ModifiableArtifactModel> result) {
        result.setResult(ArtifactManager.getInstance(myProject).createModifiableModel());
      }
    }.execute().getResultObject();
  }

  @Override
  protected ModifiableModuleModel doGetModuleModel() {
    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      return ModuleManager.getInstance(myProject).getModifiableModel();
    }
    finally {
      accessToken.finish();
    }
  }

  @Override
  protected ModifiableRootModel doGetRootModel(final Module module) {
    return new ReadAction<ModifiableRootModel>() {
      protected void run(Result<ModifiableRootModel> result) throws Throwable {
        result.setResult(ModuleRootManager.getInstance(module).getModifiableModel());
      }
    }.execute().getResultObject();
  }

  @Override
  protected ModifiableFacetModel doGetFacetModel(Module module) {
    return FacetManager.getInstance(module).createModifiableModel();
  }

  @Override
  public LibraryTable.ModifiableModel getProjectLibrariesModel() {
    return myLibrariesModel;
  }

  public Library[] getAllLibraries() {
    return myLibrariesModel.getLibraries();
  }

  public Library getLibraryByName(String name) {
    return myLibrariesModel.getLibraryByName(name);
  }

  public Library createLibrary(String name) {
    return myLibrariesModel.createLibrary(name);
  }

  public void removeLibrary(Library library) {
    myLibrariesModel.removeLibrary(library);
  }

  @Override
  protected Library.ModifiableModel doGetLibraryModel(Library library) {
    return library.getModifiableModel();
  }

  public void commit() {
    MavenUtil.invokeAndWaitWriteAction(myProject, new Runnable() {
      public void run() {
        ((ProjectRootManagerEx)ProjectRootManager.getInstance(myProject)).mergeRootsChangesDuring(new Runnable() {
          public void run() {
            processExternalArtifactDependencies();
            for (Library.ModifiableModel each : myLibraryModels.values()) {
              each.commit();
            }
            myLibrariesModel.commit();
            Collection<ModifiableRootModel> rootModels = myRootModels.values();

            ProjectRootManager.getInstance(myProject).multiCommit(myModuleModel,
                                                                  rootModels.toArray(new ModifiableRootModel[rootModels.size()]));
            LOG.info("Commit end");

            for (ModifiableFacetModel each : myFacetModels.values()) {
              each.commit();
            }
            if (myArtifactModel != null) {
              myArtifactModel.commit();
            }
          }
        });
        LOG.info("Commit write action end");
        ModuleManager.getInstance(myProject).getModules();
      }
    });
  }

  public void dispose() {
    MavenUtil.invokeAndWaitWriteAction(myProject, new Runnable() {
      public void run() {
        for (ModifiableRootModel each : myRootModels.values()) {
          each.dispose();
        }
        myModuleModel.dispose();
        if (myArtifactModel != null) {
          myArtifactModel.dispose();
        }
      }
    });
  }

  public ModalityState getModalityStateForQuestionDialogs() {
    return ModalityState.NON_MODAL;
  }
}
