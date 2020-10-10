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
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.*;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 13, 2007
 */
public class PropertiesBuilder extends AntElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.psi.impl.PropertiesBuilder");

  @NotNull private final AntFile myPropertyHolder;
  private final Set<AntTarget> myVisitedTargets = new HashSet<AntTarget>();
  private final Set<AntFile> myVisitedFiles = new HashSet<AntFile>();
  private final Map<AntProject, List<Runnable>> myPostponedProcessing = new HashMap<AntProject, List<Runnable>>();
  private final List<PsiFile> myDependentFiles = new ArrayList<PsiFile>();
  
  private PropertiesBuilder(@NotNull AntFile propertyHolder) {
    myPropertyHolder = propertyHolder;
  }

  @Override public void visitAntTypedef(final AntTypeDef def) {
    // at this point all properties used in classpath for this typedef must be defined
    // so to make sure the class set is complete, need to reload classes here
    def.clearClassesCache();
    def.getDefinitions();
  }

  @Override public void visitAntFile(final AntFile antFile) {
    if (!myVisitedFiles.contains(antFile)) {
      myVisitedFiles.add(antFile);
      final AntProject project = antFile.getAntProject();
      if (project != null) {
        project.acceptAntElementVisitor(this);
        ((AntProjectImpl)project).clearImports();
      }
    }
  }

  @Override public void visitAntTask(final AntTask task) {
    if (task instanceof AntProperty) {
      visitAntProperty((AntProperty)task);
    }
    else {
      super.visitAntTask(task);
    }
  }

  public void visitAntStructuredElement(final AntStructuredElement element) {
    if (element instanceof AntFilesProviderImpl) {
      // need to clear cached files here 
      // because they might be cached at the moment when not all properties were loaded (e.g. because of antProject.getChildren()) 
      // and thus not all paths might resolve
      ((AntFilesProviderImpl)element).clearCachedFiles();
    }
    super.visitAntStructuredElement(element);
  }

  @Override public void visitAntProject(final AntProject antProject) {
    final Set<AntTarget> projectTargets = new LinkedHashSet<AntTarget>();
    for (PsiElement child : antProject.getChildren()) {
      if (child instanceof AntElement) {
        if (child instanceof AntTarget) {
          final AntTarget antTarget = (AntTarget)child;
          if (antProject.equals(antTarget.getAntProject())) {
            // heuristic: do not collect imported targets
            projectTargets.add(antTarget);
          }
        }
        else {
          ((AntElement)child).acceptAntElementVisitor(this);
        }
      }
    }

    final AntTarget entryTarget = antProject.getDefaultTarget();
    if (entryTarget != null) {
      entryTarget.acceptAntElementVisitor(this);
    }

    projectTargets.removeAll(myVisitedTargets);
    // process unvisited targets
    for (AntTarget antTarget : projectTargets) {
      antTarget.acceptAntElementVisitor(this);
    }
    // process postponed targets
    final List<Runnable> list = myPostponedProcessing.get(antProject);
    if (list != null) {
      for (Runnable runnable : list) {
        runnable.run();
      }
      myPostponedProcessing.remove(antProject);
    }
  }

  @Override public void visitAntProperty(final AntProperty antProperty) {
    final PropertiesFile propertiesFile = antProperty.getPropertiesFile();
    if (propertiesFile != null) {
      myDependentFiles.add(propertiesFile.getContainingFile());
    }

    final String environment = antProperty.getEnvironment();
    if (environment != null) {
      myPropertyHolder.addEnvironmentPropertyPrefix(environment);
    }

    final String[] names = antProperty.getNames();
    if (names != null) {
      for (String name : names) {
        myPropertyHolder.setProperty(name, antProperty);
      }
    }
  }

  @Override public void visitAntTarget(final AntTarget target) {
    if (myVisitedTargets.contains(target)) {
      return;
    }
    myVisitedTargets.add(target);
    
    final AntTarget[] dependsTargets = target.getDependsTargets();
    for (AntTarget dependsTarget : dependsTargets) {
      dependsTarget.acceptAntElementVisitor(this);
    }

    final String ifProperty = target.getConditionalPropertyName(AntTarget.ConditionalAttribute.IF);
    if (ifProperty != null && myPropertyHolder.getProperty(ifProperty) == null) {
      postponeTargetVisiting(target);
      return; // skip target because 'if' property not defined
    }

    final String unlessProperty = target.getConditionalPropertyName(AntTarget.ConditionalAttribute.UNLESS);
    if (unlessProperty != null && myPropertyHolder.getProperty(unlessProperty) != null) {
      postponeTargetVisiting(target);
      return; // skip target because 'unless' property is defined 
    }

    visitTargetChildren(target);
  }

  private void postponeTargetVisiting(final AntTarget target) {
    final AntProject antProject = target.getAntProject();
    List<Runnable> list = myPostponedProcessing.get(antProject);
    if (list == null) {
      list = new ArrayList<Runnable>();
      myPostponedProcessing.put(antProject, list);
    }
    list.add(new Runnable() {
      public void run() {
        visitTargetChildren(target);
      }
    });
  }

  private void visitTargetChildren(final AntTarget target) {
    for (PsiElement child : target.getChildren()) {
      if (child instanceof AntElement) {
        ((AntElement)child).acceptAntElementVisitor(this);
      }
    }
  }

  @Override public void visitAntImport(final AntImport antImport) {
    final AntFile antFile = antImport.getImportedFile();
    if (antFile != null) {
      myDependentFiles.add(antFile);
      visitAntFile(antFile);
    }
  }

  public static List<PsiFile> defineProperties(AntFile file) {
    final AntProject project = file.getAntProject();
    LOG.assertTrue(project != null);
    

    final PropertiesBuilder builder = new PropertiesBuilder(file);
    file.acceptAntElementVisitor(builder);

    /*
    for (AntTarget target : builder.myVisitedTargets) {
      if (target instanceof AntTargetImpl) {
        definePseudoProperties((AntTargetImpl)target);
      }
    }
    */
    return builder.myDependentFiles;
  }

  /*
  private static void definePseudoProperties(final AntTargetImpl target) {
    final XmlTag se = target.getSourceElement();
    XmlAttribute propNameAttribute = se.getAttribute(AntFileImpl.IF_ATTR, null);
    if (propNameAttribute == null) {
      propNameAttribute = se.getAttribute(AntFileImpl.UNLESS_ATTR, null);
    }
    if (propNameAttribute != null) {
      final XmlAttributeValue valueElement = propNameAttribute.getValueElement();
      if (valueElement != null) {
        final String value = target.computeAttributeValue(valueElement.getValue());
        final AntFile propertyHolder = target.getAntFile();
        if (propertyHolder.getProperty(value) == null) {
          target.setPropertyDefinitionElement(valueElement);
          propertyHolder.setExternalProperty(value, target);
        }
      }
    }
  }
  */
  
}
