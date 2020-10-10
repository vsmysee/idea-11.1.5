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
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class GroovyUnusedDeclarationInspection extends DescriptorProviderInspection implements UnfairLocalInspectionTool {
  public static final String SHORT_NAME = "GroovyUnusedDeclaration";

  @Override
  public void runInspection(@NotNull AnalysisScope scope, @NotNull InspectionManager manager) {
  }

  @NotNull
  @Override
  public JobDescriptor[] getJobDescriptors(GlobalInspectionContext globalInspectionContext) {
    return JobDescriptor.EMPTY_ARRAY;
  }

}
