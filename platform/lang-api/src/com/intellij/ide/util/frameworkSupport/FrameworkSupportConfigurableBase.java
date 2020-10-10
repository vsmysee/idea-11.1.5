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

package com.intellij.ide.util.frameworkSupport;

import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
*/
public class FrameworkSupportConfigurableBase extends FrameworkSupportConfigurable {
  private JComboBox myVersionComboBox;
  private final FrameworkSupportProviderBase myFrameworkSupportProvider;
  protected final FrameworkSupportModel myFrameworkSupportModel;
  private final List<FrameworkVersion> myVersions;
  private JPanel myMainPanel;
  private JLabel myDescriptionLabel;

  public FrameworkSupportConfigurableBase(FrameworkSupportProviderBase frameworkSupportProvider, FrameworkSupportModel model) {
    this(frameworkSupportProvider, model, Collections.<FrameworkVersion>emptyList(), null);
  }

  public FrameworkSupportConfigurableBase(FrameworkSupportProviderBase frameworkSupportProvider, FrameworkSupportModel model,
                                          @NotNull List<FrameworkVersion> versions, @Nullable String versionLabelText) {
    myFrameworkSupportProvider = frameworkSupportProvider;
    myFrameworkSupportModel = model;
    myVersions = versions;
    if (versions.size() > 0) {
      myDescriptionLabel.setText(versionLabelText);
      String maxValue = "";
      FrameworkVersion defaultVersion = versions.get(versions.size() - 1);
      for (FrameworkVersion version : versions) {
        myVersionComboBox.addItem(version);
        FontMetrics fontMetrics = myVersionComboBox.getFontMetrics(myVersionComboBox.getFont());
        if (fontMetrics.stringWidth(version.getVersionName()) > fontMetrics.stringWidth(maxValue)) {
          maxValue = version.getVersionName();
        }
        if (version.isDefault()) {
          defaultVersion = version;
        }
      }
      myVersionComboBox.setPrototypeDisplayValue(maxValue + "_");
      myVersionComboBox.setRenderer(new ListCellRendererWrapper(myVersionComboBox.getRenderer()) {
        @Override
        public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value instanceof FrameworkVersion) {
            setText(((FrameworkVersion)value).getVersionName());
          }
        }
      });
      myVersionComboBox.setSelectedItem(defaultVersion);
      myVersionComboBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          fireFrameworkVersionChanged();
        }
      });
    }

    if (versions.size() < 2) {
      myDescriptionLabel.setVisible(false);
      myVersionComboBox.setVisible(false);
    }
  }

  public JComponent getComponent() {
    return myVersions.size() > 1 ? myMainPanel : null;
  }

  @NotNull
  public List<? extends FrameworkVersion> getVersions() {
    return myVersions;
  }

  @NotNull
  public LibraryInfo[] getLibraries() {
    return getSelectedVersion().getLibraries();
  }

  public void addSupport(@NotNull final Module module, @NotNull final ModifiableRootModel rootModel, final @Nullable Library library) {
    myFrameworkSupportProvider.addSupport(module, rootModel, getSelectedVersion(), library);
  }

  public FrameworkVersion getSelectedVersion() {
    return (FrameworkVersion)myVersionComboBox.getSelectedItem();
  }
}
