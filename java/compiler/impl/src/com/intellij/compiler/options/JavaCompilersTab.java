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
package com.intellij.compiler.options;

import com.intellij.compiler.CompileServerManager;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2004
 */
public class JavaCompilersTab implements SearchableConfigurable, Configurable.NoScroll {
  private JPanel myPanel;
  private JPanel myContentPanel;
  private JComboBox myCompiler;
  private final CardLayout myCardLayout;

  private final Project myProject;
  private final BackendCompiler myDefaultCompiler;
  private BackendCompiler mySelectedCompiler;
  private final CompilerConfigurationImpl myCompilerConfiguration;
  private final Collection<Configurable> myConfigurables;

  public JavaCompilersTab(final Project project, Collection<BackendCompiler> compilers, BackendCompiler defaultCompiler) {
    myProject = project;
    myDefaultCompiler = defaultCompiler;
    myCompilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    myConfigurables = new ArrayList<Configurable>(compilers.size());

    myCardLayout = new CardLayout();
    myContentPanel.setLayout(myCardLayout);

    for (BackendCompiler compiler : compilers) {
      Configurable configurable = compiler.createConfigurable();
      myConfigurables.add(configurable);

      myContentPanel.add(configurable.createComponent(), compiler.getId());
    }
    myCompiler.setModel(new DefaultComboBoxModel(new Vector(compilers)));
    myCompiler.setRenderer(new ListCellRendererWrapper<BackendCompiler>(myCompiler.getRenderer()) {
      @Override
      public void customize(final JList list, final BackendCompiler value, final int index, final boolean selected, final boolean hasFocus) {
        setText(value != null ? value.getPresentableName() : "");
      }
    });
    myCompiler.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        BackendCompiler compiler = (BackendCompiler)myCompiler.getSelectedItem();
        if (compiler == null) return;
        selectCompiler(compiler);
      }
    });
  }

  public String getDisplayName() {
    return CompilerBundle.message("java.compiler.description");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.projectsettings.compiler.javacompiler";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    if (!Comparing.equal(mySelectedCompiler, myCompilerConfiguration.getDefaultCompiler())) {
      return true;
    }
    for (Configurable configurable : myConfigurables) {
      if (configurable.isModified()) {
        return true;
      }
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    for (Configurable configurable : myConfigurables) {
      configurable.apply();
    }
    myCompilerConfiguration.setDefaultCompiler(mySelectedCompiler);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        CompileServerManager.getInstance().sendReloadRequest(myProject);
      }
    });
  }

  public void reset() {
    for (Configurable configurable : myConfigurables) {
      configurable.reset();
    }
    selectCompiler(myCompilerConfiguration.getDefaultCompiler());
  }

  public void disposeUIResources() {
  }

  private void selectCompiler(BackendCompiler compiler) {
    if(compiler == null) {
      compiler = myDefaultCompiler;
    }
    myCompiler.setSelectedItem(compiler);
    mySelectedCompiler = compiler;
    myCardLayout.show(myContentPanel, compiler.getId());
    myContentPanel.revalidate();
    myContentPanel.repaint();
  }
}
