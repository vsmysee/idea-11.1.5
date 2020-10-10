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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.projectWizard.ProjectJdkListRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.projectRoot.JdkListConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 * @since May 18, 2005
 */
public class JdkComboBox extends ComboBoxWithWidePopup {

  @Nullable
  private final Condition<Sdk> myFilter;
  @Nullable
  private final Condition<SdkType> myCreationFilter;

  public JdkComboBox(@NotNull final ProjectSdksModel jdkModel) {
    this(jdkModel, null, null);
  }

  public JdkComboBox(@NotNull final ProjectSdksModel jdkModel,
                     @Nullable Condition<Sdk> filter,
                     @Nullable Condition<SdkType> creationFilter) {
    super(new JdkComboBoxModel(jdkModel, filter));
    myFilter = filter;
    myCreationFilter = creationFilter;
    setRenderer(new ProjectJdkListRenderer(getRenderer()) {
      @Override
      public void doCustomize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (JdkComboBox.this.isEnabled()) {
          if (value instanceof InvalidJdkComboBoxItem) {
            final String str = value.toString();
            append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
          }
          else if (value instanceof ProjectJdkComboBoxItem){
            final Sdk jdk = jdkModel.getProjectSdk();
            if (jdk != null){
              setIcon(jdk.getSdkType().getIcon());
              append(ProjectBundle.message("project.roots.project.jdk.inherited"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              append(" (" + jdk.getName() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            } else {
              final String str = value.toString();
              append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
            }
          }
          else {
            super.doCustomize(list, value != null ? ((JdkComboBoxItem)value).getJdk() : new NoneJdkComboBoxItem(), index, selected,
                              hasFocus);
          }
        }
      }
    });
  }

  @Override
  public Dimension getPreferredSize() {
    final Rectangle rec = ScreenUtil.getScreenRectangle(0, 0);
    final Dimension size = super.getPreferredSize();
    final int maxWidth = rec.width / 4;
    if (size.width > maxWidth) {
      size.width = maxWidth; 
    }
    return size;
  }

  @Override
  public Dimension getMinimumSize() {
    final Dimension minSize = super.getMinimumSize();
    final Dimension prefSize = getPreferredSize();
    if (minSize.width > prefSize.width) {
      minSize.width = prefSize.width;
    }
    return minSize;
  }

  public void setSetupButton(final JButton setUpButton,
                                final Project project,
                                final ProjectSdksModel jdksModel,
                                final JdkComboBoxItem firstItem,
                                @Nullable final Condition<Sdk> additionalSetup,
                                final boolean moduleJdkSetup) {
    setSetupButton(setUpButton, project, jdksModel, firstItem, additionalSetup,
                   ProjectBundle.message("project.roots.set.up.jdk.title", moduleJdkSetup ? 1 : 2));
  }

  public void setSetupButton(final JButton setUpButton,
                                final Project project,
                                final ProjectSdksModel jdksModel,
                                final JdkComboBoxItem firstItem,
                                @Nullable final Condition<Sdk> additionalSetup,
                                final String actionGroupTitle) {
    setUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final JdkListConfigurable configurable = JdkListConfigurable.getInstance(project);
        DefaultActionGroup group = new DefaultActionGroup();
        jdksModel.createAddActions(group, JdkComboBox.this, new Consumer<Sdk>() {
          public void consume(final Sdk jdk) {
            configurable.addJdkNode(jdk, false);
            reloadModel(firstItem, project);
            setSelectedJdk(jdk); //restore selection
            if (additionalSetup != null) {
              if (additionalSetup.value(jdk)) { //leave old selection
                setSelectedJdk(firstItem.getJdk());
              }
            }
          }
        }, myCreationFilter);
        final DataContext dataContext = DataManager.getInstance().getDataContext(JdkComboBox.this);
        if (group.getChildrenCount() > 1) {
          JBPopupFactory.getInstance()
            .createActionGroupPopup(actionGroupTitle, group, dataContext, JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
            .showUnderneathOf(setUpButton);
        }
        else {
          final AnActionEvent event =
            new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, new Presentation(""), ActionManager.getInstance(), 0);
          group.getChildren(event)[0].actionPerformed(event);
        }
      }
    });
  }

  public void setEditButton(final JButton editButton, final Project project, final Computable<Sdk> retrieveJDK){
    editButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Sdk projectJdk = retrieveJDK.compute();
        if (projectJdk != null) {
          ProjectStructureConfigurable.getInstance(project).select(projectJdk, true);
        }
      }
    });
    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final JdkComboBoxItem selectedItem = getSelectedItem();
        if (selectedItem instanceof ProjectJdkComboBoxItem) {
          editButton.setEnabled(ProjectStructureConfigurable.getInstance(project).getProjectJdksModel().getProjectSdk() != null);
        }
        else {
          editButton.setEnabled(!(selectedItem instanceof InvalidJdkComboBoxItem) && selectedItem != null && selectedItem.getJdk() != null);
        }
      }
    });
  }

  public JdkComboBoxItem getSelectedItem() {
    return (JdkComboBoxItem)super.getSelectedItem();
  }

  @Nullable
  public Sdk getSelectedJdk() {
    final JdkComboBoxItem selectedItem = (JdkComboBoxItem)super.getSelectedItem();
    return selectedItem != null? selectedItem.getJdk() : null;
  }

  public void setSelectedJdk(Sdk jdk) {
    final int index = indexOf(jdk);
    if (index >= 0) {
      setSelectedIndex(index);
    }
  }

  public void setInvalidJdk(String name) {
    removeInvalidElement();
    addItem(new InvalidJdkComboBoxItem(name));
    setSelectedIndex(getModel().getSize() - 1);
  }
  
  private int indexOf(Sdk jdk) {
    final JdkComboBoxModel model = (JdkComboBoxModel)getModel();
    final int count = model.getSize();
    for (int idx = 0; idx < count; idx++) {
      final JdkComboBoxItem elementAt = model.getElementAt(idx);
      if (jdk == null) {
        if (elementAt instanceof NoneJdkComboBoxItem || elementAt instanceof ProjectJdkComboBoxItem) {
          return idx;
        }
      }
      else {
        if (jdk.equals(elementAt.getJdk())) {
          return idx;
        }
      }
    }
    return -1;
  }
  
  private void removeInvalidElement() {
    final JdkComboBoxModel model = (JdkComboBoxModel)getModel();
    final int count = model.getSize();
    for (int idx = 0; idx < count; idx++) {
      final JdkComboBoxItem elementAt = model.getElementAt(idx);
      if (elementAt instanceof InvalidJdkComboBoxItem) {
        removeItemAt(idx);
        break;
      }
    }
  }

  public void reloadModel(JdkComboBoxItem firstItem, Project project) {
    final DefaultComboBoxModel model = ((DefaultComboBoxModel)getModel());
    model.removeAllElements();
    model.addElement(firstItem);
    final ProjectSdksModel projectJdksModel = ProjectStructureConfigurable.getInstance(project).getProjectJdksModel();
    List<Sdk> projectJdks = new ArrayList<Sdk>(projectJdksModel.getProjectSdks().values());
    if (myFilter != null) {
      projectJdks = ContainerUtil.filter(projectJdks, myFilter);
    }
    Collections.sort(projectJdks, new Comparator<Sdk>() {
      public int compare(final Sdk o1, final Sdk o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
    for (Sdk projectJdk : projectJdks) {
      model.addElement(new JdkComboBox.JdkComboBoxItem(projectJdk));
    }
  }

  private static class JdkComboBoxModel extends DefaultComboBoxModel {
    public JdkComboBoxModel(final ProjectSdksModel jdksModel, final @Nullable Condition<Sdk> filter) {
      Sdk[] jdks = jdksModel.getSdks();
      if (filter != null) {
        final List<Sdk> filtered = ContainerUtil.filter(jdks, filter);
        jdks = filtered.toArray(new Sdk[filtered.size()]); 
      }
      Arrays.sort(jdks, new Comparator<Sdk>() {
        public int compare(final Sdk s1, final Sdk s2) {
          return s1.getName().compareToIgnoreCase(s2.getName());
        }
      });
      for (Sdk jdk : jdks) {
        addElement(new JdkComboBoxItem(jdk));
      }
    }

    // implements javax.swing.ListModel
    public JdkComboBoxItem getElementAt(int index) {
      return (JdkComboBoxItem)super.getElementAt(index);
    }
  }
  
  public static class JdkComboBoxItem {
    private final Sdk myJdk;

    public JdkComboBoxItem(@Nullable Sdk jdk) {
      myJdk = jdk;
    }

    public Sdk getJdk() {
      return myJdk;
    }

    @Nullable
    public String getSdkName() {
      return myJdk != null ? myJdk.getName() : null;
    }
    
    public String toString() {
      return myJdk.getName();
    }
  }

  public static class ProjectJdkComboBoxItem extends JdkComboBoxItem {
    public ProjectJdkComboBoxItem() {
      super(null);
    }

    public String toString() {
      return ProjectBundle.message("jdk.combo.box.project.item");
    }
  }

  public static class NoneJdkComboBoxItem extends JdkComboBoxItem {
    public NoneJdkComboBoxItem() {
      super(null);
    }

    public String toString() {
      return ProjectBundle.message("jdk.combo.box.none.item");
    }
  }

  private static class InvalidJdkComboBoxItem extends JdkComboBoxItem {
    private final String mySdkName;

    public InvalidJdkComboBoxItem(String name) {
      super(null);
      mySdkName = name;
    }

    public String getSdkName() {
      return mySdkName;
    }

    public String toString() {
      return ProjectBundle.message("jdk.combo.box.invalid.item", mySdkName);
    }
  }
}
