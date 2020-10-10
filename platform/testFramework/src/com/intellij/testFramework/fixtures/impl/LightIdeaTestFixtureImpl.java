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

package com.intellij.testFramework.fixtures.impl;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestDataProvider;
import com.intellij.testFramework.fixtures.LightIdeaTestFixture;

/**
 * @author mike
 */
class LightIdeaTestFixtureImpl extends BaseFixture implements LightIdeaTestFixture {
  private final LightProjectDescriptor myProjectDescriptor;

  public LightIdeaTestFixtureImpl(LightProjectDescriptor projectDescriptor) {
    myProjectDescriptor = projectDescriptor;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    IdeaTestApplication application = LightPlatformTestCase.initApplication();
    LightPlatformTestCase.doSetup(myProjectDescriptor, LocalInspectionTool.EMPTY_ARRAY, null);
    ((InjectedLanguageManagerImpl)InjectedLanguageManager.getInstance(getProject())).pushInjectors();
    storeSettings();
    application.setDataProvider(new TestDataProvider(getProject()));
  }

  @Override
  public void tearDown() throws Exception {
    CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    checkForSettingsDamage();
    LightPlatformTestCase.doTearDown(getProject(), LightPlatformTestCase.getApplication(), true);
    super.tearDown();
    ((InjectedLanguageManagerImpl)InjectedLanguageManager.getInstance(getProject())).checkInjectorsAreDisposed();
  }


  @Override
  public Project getProject() {
    return LightPlatformTestCase.getProject();
  }

  @Override
  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    if (CodeStyleSchemes.getInstance().getCurrentScheme() == null) return new CodeStyleSettings();
    return CodeStyleSettingsManager.getSettings(getProject());
  }

  @Override
  public Module getModule() {
    return LightPlatformTestCase.getModule();
  }
}
