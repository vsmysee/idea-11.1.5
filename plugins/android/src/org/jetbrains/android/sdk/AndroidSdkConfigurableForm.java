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
package org.jetbrains.android.sdk;

import com.android.sdklib.IAndroidTarget;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidSdkConfigurableForm {
  private JComboBox myInternalJdkComboBox;
  private JPanel myContentPanel;
  private JComboBox myBuildTargetComboBox;

  private final DefaultComboBoxModel myJdksModel = new DefaultComboBoxModel();
  private final SdkModel mySdkModel;

  private final DefaultComboBoxModel myBuildTargetsModel = new DefaultComboBoxModel();
  private String mySdkLocation;

  public AndroidSdkConfigurableForm(@NotNull SdkModel sdkModel, @NotNull final SdkModificator sdkModificator) {
    mySdkModel = sdkModel;
    myInternalJdkComboBox.setModel(myJdksModel);
    myInternalJdkComboBox.setRenderer(new ListCellRendererWrapper(myInternalJdkComboBox.getRenderer()) {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof Sdk) {
          setText(((Sdk)value).getName());
        }
      }
    });
    myBuildTargetComboBox.setModel(myBuildTargetsModel);

    myBuildTargetComboBox.setRenderer(new ListCellRendererWrapper(myBuildTargetComboBox.getRenderer()) {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof IAndroidTarget) {
          setText(AndroidSdkUtils.getTargetPresentableName((IAndroidTarget)value));
        }
        else if (value == null) {
          setText("<html><font color='red'>[none]</font></html>");
        }
      }
    });

    myBuildTargetComboBox.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        final IAndroidTarget target = (IAndroidTarget)e.getItem();

        List<OrderRoot> roots = AndroidSdkUtils.getLibraryRootsForTarget(target, mySdkLocation);
        Map<OrderRootType, String[]> configuredRoots = new HashMap<OrderRootType, String[]>();

        for (OrderRootType type : OrderRootType.getAllTypes()) {
          final VirtualFile[] oldRoots = sdkModificator.getRoots(type);
          final String[] oldRootPaths = new String[oldRoots.length];
          
          for (int i = 0; i < oldRootPaths.length; i++) {
            oldRootPaths[i] = oldRoots[i].getPath();
          }
          
          configuredRoots.put(type, oldRootPaths);
        }

        for (OrderRoot root : roots) {
          if (e.getStateChange() == ItemEvent.DESELECTED) {
            sdkModificator.removeRoot(root.getFile(), root.getType());
          }
          else {
            String[] configuredRootsForType = configuredRoots.get(root.getType());
            if (ArrayUtil.find(configuredRootsForType, root.getFile().getPath()) == -1) {
              sdkModificator.addRoot(root.getFile(), root.getType());
            }
          }
        }
      }
    });
  }

  @NotNull
  public JPanel getContentPanel() {
    return myContentPanel;
  }

  @Nullable
  public Sdk getSelectedSdk() {
    return (Sdk)myInternalJdkComboBox.getSelectedItem();
  }

  @Nullable
  public IAndroidTarget getSelectedBuildTarget() {
    return (IAndroidTarget)myBuildTargetComboBox.getSelectedItem();
  }

  public void init(@Nullable Sdk jdk, Sdk androidSdk, IAndroidTarget buildTarget) {
    updateJdks();
    
    final String jdkName = jdk != null ? jdk.getName() : null;

    if (androidSdk != null) {
      for (int i = 0; i < myJdksModel.getSize(); i++) {
        if (Comparing.strEqual(((Sdk)myJdksModel.getElementAt(i)).getName(), jdkName)) {
          myInternalJdkComboBox.setSelectedIndex(i);
          break;
        }
      }
    }

    mySdkLocation = androidSdk != null ? androidSdk.getHomePath() : null;
    AndroidSdkData androidSdkData = mySdkLocation != null ? AndroidSdkData.parse(mySdkLocation, new EmptySdkLog()) : null;
    updateBuildTargets(androidSdkData);

    if (buildTarget != null) {
      for (int i = 0; i < myBuildTargetsModel.getSize(); i++) {
        IAndroidTarget target = (IAndroidTarget)myBuildTargetsModel.getElementAt(i);
        if (buildTarget.hashString().equals(target.hashString())) {
          myBuildTargetComboBox.setSelectedIndex(i);
          return;
        }
      }
    }
    myBuildTargetComboBox.setSelectedItem(null);
  }

  private void updateJdks() {
    myJdksModel.removeAllElements();
    for (Sdk sdk : mySdkModel.getSdks()) {
      if (AndroidSdkUtils.isApplicableJdk(sdk)) {
        myJdksModel.addElement(sdk);
      }
    }
  }

  private void updateBuildTargets(AndroidSdkData androidSdkData) {
    myBuildTargetsModel.removeAllElements();

    if (androidSdkData != null) {
      for (IAndroidTarget target : androidSdkData.getTargets()) {
        myBuildTargetsModel.addElement(target);
      }
    }
  }

  public void addJavaSdk(Sdk sdk) {
    myJdksModel.addElement(sdk);
  }

  public void removeJavaSdk(Sdk sdk) {
    myJdksModel.removeElement(sdk);
  }

  public void updateJdks(Sdk sdk, String previousName) {
    final Sdk[] sdks = mySdkModel.getSdks();
    for (Sdk currentSdk : sdks) {
      if (currentSdk.getSdkType().equals(AndroidSdkType.getInstance())) {
        final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)currentSdk.getSdkAdditionalData();
        final Sdk internalJava = data != null ? data.getJavaSdk() : null;
        if (internalJava != null && Comparing.equal(internalJava.getName(), previousName)) {
          data.setJavaSdk(sdk);
        }
      }
    }
    updateJdks();
  }

  public void internalJdkUpdate(final Sdk sdk) {
    AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (data == null) return;
    final Sdk javaSdk = data.getJavaSdk();
    if (myJdksModel.getIndexOf(javaSdk) == -1) {
      myJdksModel.addElement(javaSdk);
    }
    else {
      myJdksModel.setSelectedItem(javaSdk);
    }
  }
}
