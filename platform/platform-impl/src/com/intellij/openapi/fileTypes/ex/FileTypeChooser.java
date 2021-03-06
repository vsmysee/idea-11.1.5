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
package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;

public class FileTypeChooser extends DialogWrapper {
  private JList myList;
  private JLabel myTitleLabel;
  private ComboBox myPattern;
  private JPanel myPanel;
  private JRadioButton myOpenInIdea;
  private JRadioButton myOpenAsNative;
  private final String myFileName;

  private FileTypeChooser(@NotNull String[] patterns, @NotNull String fileName) {
    super(true);
    myFileName = fileName;

    myOpenInIdea.setText("Open matching files in " + ApplicationNamesInfo.getInstance().getProductName() + ":");

    FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    Arrays.sort(fileTypes, new Comparator<FileType>() {
      public int compare(final FileType fileType1, final FileType fileType2) {
        if (fileType1 == null){
          return 1;
        }
        if (fileType2 == null){
          return -1;
        }
        return fileType1.getDescription().compareToIgnoreCase(fileType2.getDescription());  
      }
    });

    final DefaultListModel model = new DefaultListModel();
    for (FileType type : fileTypes) {
      if (!type.isReadOnly() && type != FileTypes.UNKNOWN && !(type instanceof NativeFileType)) {
        model.addElement(type);
      }
    }
    myList.setModel(model);
    myPattern.setModel(new CollectionComboBoxModel(ContainerUtil.map(patterns, Function.ID), patterns[0]));

    setTitle(FileTypesBundle.message("filetype.chooser.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    myTitleLabel.setText(FileTypesBundle.message("filetype.chooser.prompt", myFileName));

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new FileTypeRenderer(myList.getCellRenderer()));

    myList.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2){
            doOKAction();
          }
        }
      }
    );

    myList.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          updateButtonsState();
        }
      }
    );

    ListScrollingUtil.selectItem(myList, FileTypes.PLAIN_TEXT);

    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  private void updateButtonsState() {
    setOKActionEnabled(myList.getSelectedIndex() != -1 || myOpenAsNative.isSelected());
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.fileTypes.FileTypeChooser";
  }

  public FileType getSelectedType() {
    return myOpenAsNative.isSelected() ? NativeFileType.INSTANCE : (FileType) myList.getSelectedValue();
  }

  /**
   * If fileName is already associated any known file type returns it.
   * Otherwise asks user to select file type and associates it with fileName extension if any selected.
   * @return Known file type or null. Never returns {@link com.intellij.openapi.fileTypes.FileTypes#UNKNOWN}.
   */
  @Nullable
  public static FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file) {
    FileType type = file.getFileType();
    if (type == FileTypes.UNKNOWN) {
        type = getKnownFileTypeOrAssociate(file.getName());
    }
    return type;
  }

  @Nullable
  public static FileType getKnownFileTypeOrAssociate(String fileName) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType type = fileTypeManager.getFileTypeByFileName(fileName);
    if (type == FileTypes.UNKNOWN) {
      type = associateFileType(fileName);
    }
    return type;
  }

  @Nullable
  public static FileType associateFileType(@NotNull final String fileName) {
    final FileTypeChooser chooser = new FileTypeChooser(suggestPatterns(fileName), fileName);
    chooser.show();
    if (!chooser.isOK()) return null;
    final FileType type = chooser.getSelectedType();
    if (type == FileTypes.UNKNOWN) return null;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileTypeManagerEx.getInstanceEx().associatePattern(type, (String)chooser.myPattern.getSelectedItem());
      }
    });

    return type;
  }

  @NotNull
  static String[] suggestPatterns(@NotNull final String fileName) {
    final Deque<String> patterns = new LinkedList<String>();
    int i = -1;
    patterns.addFirst(fileName);
    while ((i = fileName.indexOf('.', i + 1)) > 0) {
      final String extension = fileName.substring(i);
      if (!StringUtil.isEmpty(extension)) {
        patterns.addFirst("*" + extension);
      }
    }
    return ArrayUtil.toStringArray(patterns);
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.register.association";
  }
}
