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


package org.jetbrains.idea.svn;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.MultiLineTooltipUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.config.ConfigureProxiesListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class SvnConfigurable implements Configurable {

  private final Project myProject;
  private JCheckBox myUseDefaultCheckBox;
  private TextFieldWithBrowseButton myConfigurationDirectoryText;
  private JButton myClearAuthButton;
  private JCheckBox myUseCommonProxy;
  private JButton myEditProxiesButton;
  private JPanel myComponent;

  private JLabel myConfigurationDirectoryLabel;
  private JLabel myClearCacheLabel;
  private JLabel myUseCommonProxyLabel;
  private JLabel myEditProxyLabel;
  private JCheckBox myLockOnDemand;
  private JCheckBox myCheckNestedInQuickMerge;
  private JCheckBox myDetectNestedWorkingCopiesCheckBox;
  private JCheckBox myIgnoreWhitespaceDifferenciesInCheckBox;
  private JCheckBox myShowMergeSourceInAnnotate;
  private JSpinner myNumRevsInAnnotations;
  private JCheckBox myMaximumNumberOfRevisionsCheckBox;
  private JSpinner mySSHConnectionTimeout;
  private JSpinner mySSHReadTimeout;
  private JRadioButton myJavaHLAcceleration;
  private JRadioButton myNoAcceleration;
  private JLabel myJavaHLInfo;
  private JRadioButton myWithCommandLineClient;
  private TextFieldWithBrowseButton myCommandLineClient;

  @NonNls private static final String HELP_ID = "project.propSubversion";

  public SvnConfigurable(Project project) {
    myProject = project;

    myUseDefaultCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        boolean enabled = !myUseDefaultCheckBox.isSelected();
        myConfigurationDirectoryText.setEnabled(enabled);
        myConfigurationDirectoryLabel.setEnabled(enabled);
        SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
        String path = configuration.getConfigurationDirectory();
        if (!enabled || path == null) {
          myConfigurationDirectoryText.setText(IdeaSubversionConfigurationDirectory.getPath());
        }
        else {
          myConfigurationDirectoryText.setText(path);
        }
      }
    });
    myCommandLineClient.addBrowseFolderListener("Subversion", "Select path to Subversion executable (1.7+)", project,
                                       FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());

    myClearAuthButton.addActionListener(new ActionListener(){
      public void actionPerformed(final ActionEvent e) {
        String path = myConfigurationDirectoryText.getText();
        if (path != null) {
          int result = Messages.showYesNoDialog(myComponent, SvnBundle.message("confirmation.text.delete.stored.authentication.information"),
                                                SvnBundle.message("confirmation.title.clear.authentication.cache"),
                                                             Messages.getWarningIcon());
          if (result == 0) {
            SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
            SvnConfiguration.getInstance(myProject).clearAuthenticationDirectory(myProject);
          }
        }

      }
    });


    final FileChooserDescriptor descriptor = createFileDescriptor();

    myConfigurationDirectoryText.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        @NonNls String path = myConfigurationDirectoryText.getText().trim();
        path = "file://" + path.replace(File.separatorChar, '/');
        VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(path);

        String oldValue = PropertiesComponent.getInstance().getValue("FileChooser.showHiddens");
        PropertiesComponent.getInstance().setValue("FileChooser.showHiddens", Boolean.TRUE.toString());
        VirtualFile file = FileChooser.chooseFile(myComponent, descriptor, root);
        PropertiesComponent.getInstance().setValue("FileChooser.showHiddens", oldValue);
        if (file == null) {
          return;
        }
        myConfigurationDirectoryText.setText(file.getPath().replace('/', File.separatorChar));
      }
    });
    myConfigurationDirectoryText.setEditable(false);

    myConfigurationDirectoryLabel.setLabelFor(myConfigurationDirectoryText);

    myUseCommonProxy.setText(SvnBundle.message("use.idea.proxy.as.default", ApplicationNamesInfo.getInstance().getProductName()));
    myEditProxiesButton.addActionListener(new ConfigureProxiesListener(myProject));

    myMaximumNumberOfRevisionsCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());
      }
    });
    myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());

    ButtonGroup bg = new ButtonGroup();
    bg.add(myNoAcceleration);
    bg.add(myJavaHLAcceleration);
    bg.add(myWithCommandLineClient);
    final boolean internal = ApplicationManager.getApplication().isInternal();
    myJavaHLAcceleration.setEnabled(internal);
    myJavaHLAcceleration.setVisible(internal);
    myJavaHLInfo.setVisible(internal);
  }

  private static FileChooserDescriptor createFileDescriptor() {
    final FileChooserDescriptor descriptor =  FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setShowFileSystemRoots(true);
    descriptor.setTitle(SvnBundle.message("dialog.title.select.configuration.directory"));
    descriptor.setDescription(SvnBundle.message("dialog.description.select.configuration.directory"));
    descriptor.setHideIgnored(false);
    return descriptor;
  }

  public JComponent createComponent() {

    return myComponent;
  }

  public String getDisplayName() {
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return HELP_ID;
  }

  public boolean isModified() {
    if (myComponent == null) {
      return false;
    }
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    if (configuration.isUseDefaultConfiguation() != myUseDefaultCheckBox.isSelected()) {
      return true;
    }
    if (configuration.isIsUseDefaultProxy() != myUseCommonProxy.isSelected()) {
      return true;
    }
    if (configuration.UPDATE_LOCK_ON_DEMAND != myLockOnDemand.isSelected()) {
      return true;
    }
    if (configuration.DETECT_NESTED_COPIES != myDetectNestedWorkingCopiesCheckBox.isSelected()) {
      return true;
    }
    if (configuration.CHECK_NESTED_FOR_QUICK_MERGE != myCheckNestedInQuickMerge.isSelected()) {
      return true;
    }
    if (configuration.IGNORE_SPACES_IN_ANNOTATE != myIgnoreWhitespaceDifferenciesInCheckBox.isSelected()) {
      return true;
    }
    if (configuration.SHOW_MERGE_SOURCES_IN_ANNOTATE != myShowMergeSourceInAnnotate.isSelected()) {
      return true;
    }
    if (! configuration.myUseAcceleration.equals(acceleration())) return true;
    final int annotateRevisions = configuration.getMaxAnnotateRevisions();
    final boolean useMaxInAnnot = annotateRevisions != -1;
    if (useMaxInAnnot != myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      return true;
    }
    if (myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      if (annotateRevisions != ((SpinnerNumberModel) myNumRevsInAnnotations.getModel()).getNumber().intValue()) {
        return true;
      }
    }
    if (configuration.mySSHConnectionTimeout/1000 != ((SpinnerNumberModel) mySSHConnectionTimeout.getModel()).getNumber().longValue()) {
      return true;
    }
    if (configuration.mySSHReadTimeout/1000 != ((SpinnerNumberModel) mySSHReadTimeout.getModel()).getNumber().longValue()) {
      return true;
    }
    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    if (! Comparing.equal(applicationSettings17.getCommandLinePath(), myCommandLineClient.getText().trim())) return true;
    return !configuration.getConfigurationDirectory().equals(myConfigurationDirectoryText.getText().trim());
  }
  
  private SvnConfiguration.UseAcceleration acceleration() {
    if (myNoAcceleration.isSelected()) return SvnConfiguration.UseAcceleration.nothing;
    if (myJavaHLAcceleration.isSelected()) return SvnConfiguration.UseAcceleration.javaHL;
    if (myWithCommandLineClient.isSelected()) return SvnConfiguration.UseAcceleration.commandLine;
    return SvnConfiguration.UseAcceleration.nothing;
  }

  private void setAcceleration(SvnConfiguration.UseAcceleration acceleration) {
    if (! CheckJavaHL.isPresent()) {
      myJavaHLInfo.setText(CheckJavaHL.getProblemDescription());
      myJavaHLInfo.setForeground(Color.red);
      myJavaHLInfo.setEnabled(true);
      myJavaHLAcceleration.setEnabled(false);
      /*myJavaHLAcceleration.setText(myJavaHLAcceleration.getText() + ". " + CheckJavaHL.getProblemDescription());
      myJavaHLAcceleration.setEnabled(false);
      myJavaHLAcceleration.setForeground(Color.red);*/
    } else {
      myJavaHLInfo.setText("You need to have JavaHL 1.7.2");
      myJavaHLInfo.setForeground(UIUtil.getInactiveTextColor());
      myJavaHLAcceleration.setEnabled(true);
    }

    if (SvnConfiguration.UseAcceleration.javaHL.equals(acceleration)) {
      myJavaHLAcceleration.setSelected(true);
      return;
    } else if (SvnConfiguration.UseAcceleration.commandLine.equals(acceleration)) {
      myWithCommandLineClient.setSelected(true);
      return;
    }
    myNoAcceleration.setSelected(true);
  }

  public void apply() throws ConfigurationException {
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    configuration.setConfigurationDirectory(myConfigurationDirectoryText.getText());
    configuration.setUseDefaultConfiguation(myUseDefaultCheckBox.isSelected());
    configuration.setIsUseDefaultProxy(myUseCommonProxy.isSelected());
    final SvnVcs vcs17 = SvnVcs.getInstance(myProject);
    if ((! configuration.DETECT_NESTED_COPIES) && (configuration.DETECT_NESTED_COPIES != myDetectNestedWorkingCopiesCheckBox.isSelected())) {
      vcs17.invokeRefreshSvnRoots(true);
    }
    configuration.DETECT_NESTED_COPIES = myDetectNestedWorkingCopiesCheckBox.isSelected();
    configuration.CHECK_NESTED_FOR_QUICK_MERGE = myCheckNestedInQuickMerge.isSelected();
    configuration.UPDATE_LOCK_ON_DEMAND = myLockOnDemand.isSelected();
    configuration.setIgnoreSpacesInAnnotate(myIgnoreWhitespaceDifferenciesInCheckBox.isSelected());
    configuration.SHOW_MERGE_SOURCES_IN_ANNOTATE = myShowMergeSourceInAnnotate.isSelected();
    if (! myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      configuration.setMaxAnnotateRevisions(-1);
    } else {
      configuration.setMaxAnnotateRevisions(((SpinnerNumberModel) myNumRevsInAnnotations.getModel()).getNumber().intValue());
    }
    configuration.mySSHConnectionTimeout = ((SpinnerNumberModel) mySSHConnectionTimeout.getModel()).getNumber().longValue() * 1000;
    configuration.mySSHReadTimeout = ((SpinnerNumberModel) mySSHReadTimeout.getModel()).getNumber().longValue() * 1000;
    configuration.myUseAcceleration = acceleration();

    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    applicationSettings17.setCommandLinePath(myCommandLineClient.getText().trim());
    if (SvnConfiguration.UseAcceleration.commandLine.equals(configuration.myUseAcceleration)) {
      vcs17.checkCommandLineVersion();
    }
  }

  public void reset() {
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    String path = configuration.getConfigurationDirectory();
    if (configuration.isUseDefaultConfiguation() || path == null) {
      path = IdeaSubversionConfigurationDirectory.getPath();
    }
    myConfigurationDirectoryText.setText(path);
    myUseDefaultCheckBox.setSelected(configuration.isUseDefaultConfiguation());
    myUseCommonProxy.setSelected(configuration.isIsUseDefaultProxy());
    myDetectNestedWorkingCopiesCheckBox.setSelected(configuration.DETECT_NESTED_COPIES);
    myCheckNestedInQuickMerge.setSelected(configuration.CHECK_NESTED_FOR_QUICK_MERGE);

    boolean enabled = !myUseDefaultCheckBox.isSelected();
    myConfigurationDirectoryText.setEnabled(enabled);
    myConfigurationDirectoryLabel.setEnabled(enabled);
    myLockOnDemand.setSelected(configuration.UPDATE_LOCK_ON_DEMAND);
    myIgnoreWhitespaceDifferenciesInCheckBox.setSelected(configuration.IGNORE_SPACES_IN_ANNOTATE);
    myShowMergeSourceInAnnotate.setSelected(configuration.SHOW_MERGE_SOURCES_IN_ANNOTATE);

    final int annotateRevisions = configuration.getMaxAnnotateRevisions();
    if (annotateRevisions == -1) {
      myMaximumNumberOfRevisionsCheckBox.setSelected(false);
      myNumRevsInAnnotations.setValue(SvnConfiguration.ourMaxAnnotateRevisionsDefault);
    } else {
      myMaximumNumberOfRevisionsCheckBox.setSelected(true);
      myNumRevsInAnnotations.setValue(annotateRevisions);
    }
    myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());
    mySSHConnectionTimeout.setValue(Long.valueOf(configuration.mySSHConnectionTimeout / 1000));
    mySSHReadTimeout.setValue(Long.valueOf(configuration.mySSHReadTimeout / 1000));
    setAcceleration(configuration.myUseAcceleration);
    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    myCommandLineClient.setText(applicationSettings17.getCommandLinePath());
  }

  public void disposeUIResources() {
  }

  private void createUIComponents() {
    myLockOnDemand = new JCheckBox() {
      @Override
      public JToolTip createToolTip() {
        JToolTip toolTip = new JToolTip(){{
          setUI(new MultiLineTooltipUI());
        }};
        toolTip.setComponent(this);
        return toolTip;
      }
    };

    final SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    int value = configuration.getMaxAnnotateRevisions();
    value = (value == -1) ? SvnConfiguration.ourMaxAnnotateRevisionsDefault : value;
    myNumRevsInAnnotations = new JSpinner(new SpinnerNumberModel(value, 10, 100000, 100));

    final Long maximum = 30 * 60 * 1000L;
    final long connection = configuration.mySSHConnectionTimeout <= maximum ? configuration.mySSHConnectionTimeout : maximum;
    final long read = configuration.mySSHReadTimeout <= maximum ? configuration.mySSHReadTimeout : maximum;
    mySSHConnectionTimeout = new JSpinner(new SpinnerNumberModel(Long.valueOf(connection / 1000), Long.valueOf(0L), maximum, Long.valueOf(10L)));
    mySSHReadTimeout = new JSpinner(new SpinnerNumberModel(Long.valueOf(read / 1000), Long.valueOf(0L), maximum, Long.valueOf(10L)));
  }
}

