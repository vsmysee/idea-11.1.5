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
package com.intellij.openapi.editor.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class ContentChooser<Data> extends DialogWrapper {

  private static final Icon textIcon = IconLoader.getIcon("/fileTypes/text.png");

  private JList myList;
  private List<Data> myAllContents;

  private Editor myViewer;
  private final boolean myUseIdeaEditor;

  private Splitter mySplitter;
  private final Project myProject;
  private final boolean myAllowMultipleSelections;

  public ContentChooser(Project project, String title, boolean useIdeaEditor) {
    this(project, title, useIdeaEditor, false);
  }
  
  public ContentChooser(Project project, String title, boolean useIdeaEditor, boolean allowMultipleSelections) {
    super(project, true);
    myProject = project;
    myUseIdeaEditor = useIdeaEditor;
    myAllowMultipleSelections = allowMultipleSelections;

    setOKButtonText(CommonBundle.getOkButtonText());
    setTitle(title);

    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  @Override
  protected JComponent createCenterPanel() {
    myList = new JBList();
    final int selectionMode = myAllowMultipleSelections ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION 
                                                        : ListSelectionModel.SINGLE_SELECTION;
    myList.setSelectionMode(selectionMode);

    rebuildListContent();

    myList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.isConsumed() || e.getClickCount() != 2 || e.isPopupTrigger()) return;
        close(OK_EXIT_CODE);
      }
    });

    myList.setCellRenderer(new MyListCellRenderer());

    if (myAllContents.size() > 0) {
      myList.setSelectedIndex(0);
    }

    myList.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE) {
          int newSelectionIndex = -1;
          for (int i : getSelectedIndices()) {
            removeContentAt(myAllContents.get(i));
            if (newSelectionIndex < 0) {
              newSelectionIndex = i;
            }
          }
          
          rebuildListContent();
          if (myAllContents.size() <= 0) {
            close(CANCEL_EXIT_CODE);
            return;
          }
          newSelectionIndex = Math.min(newSelectionIndex, myAllContents.size() - 1);
          myList.setSelectedIndex(newSelectionIndex);
        }
        else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          close(OK_EXIT_CODE);
        }
        else {
          final char aChar = e.getKeyChar();
          if (aChar >= '0' && aChar <= '9') {
            int idx = aChar == '0' ? 9 : aChar - '1';
            if (idx < myAllContents.size()) {
              myList.setSelectedIndex(idx);
            }
          }
        }
      }
    });

    mySplitter = new Splitter(true);
    mySplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myList));
    mySplitter.setSecondComponent(new JPanel());
    updateViewerForSelection();

    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateViewerForSelection();
      }
    });

    mySplitter.setPreferredSize(new Dimension(500, 500));
    new ListSpeedSearch(myList);

    return mySplitter;
  }

  protected abstract void removeContentAt(final Data content);

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.editor.actions.MultiplePasteAction.Chooser";
  }

  private void updateViewerForSelection() {
    if (myAllContents.size() == 0) return;
    String fullString = "";
    for (int i : getSelectedIndices()) {
      String s = getStringRepresentationFor(myAllContents.get(i));
      fullString += StringUtil.convertLineSeparators(s);
    }

    if (myViewer != null) {
      EditorFactory.getInstance().releaseEditor(myViewer);
    }

    if (myUseIdeaEditor) {
      Document doc = EditorFactory.getInstance().createDocument(fullString);
      myViewer = EditorFactory.getInstance().createViewer(doc, myProject);
      myViewer.getComponent().setPreferredSize(new Dimension(300, 500));
      myViewer.getSettings().setFoldingOutlineShown(false);
      myViewer.getSettings().setLineNumbersShown(false);
      myViewer.getSettings().setLineMarkerAreaShown(false);
      myViewer.getSettings().setIndentGuidesShown(false);
      mySplitter.setSecondComponent(myViewer.getComponent());
    } else {
      final JTextArea textArea = new JTextArea(fullString);
      textArea.setRows(3);
      textArea.setWrapStyleWord(true);
      textArea.setLineWrap(true);
      textArea.setSelectionStart(0);
      textArea.setSelectionEnd(textArea.getText().length());
      textArea.setEditable(false);
      mySplitter.setSecondComponent(ScrollPaneFactory.createScrollPane(textArea));
    }
    mySplitter.revalidate();
  }

  @Override
  public void dispose() {
    super.dispose();
    if (myViewer != null) {
      EditorFactory.getInstance().releaseEditor(myViewer);
      myViewer = null;
    }
  }

  private void rebuildListContent() {
    List<Data> allContents = new ArrayList<Data>(getContents());
    ArrayList<String> shortened = new ArrayList<String>();
    for (Data content : allContents) {
      String fullString = getStringRepresentationFor(content);
      if (fullString != null) {
        fullString = StringUtil.convertLineSeparators(fullString);
        int newLineIdx = fullString.indexOf('\n');
        if (newLineIdx == -1) {
          shortened.add(fullString.trim());
        }
        else {
          int lastLooked = 0;
          do  {
            int nextLineIdx = fullString.indexOf("\n", lastLooked);
            if (nextLineIdx > lastLooked) {
              shortened.add(fullString.substring(lastLooked, nextLineIdx).trim() + " ...");
              break;
            }
            else if (nextLineIdx == -1) {
              shortened.add(" ...");
              break;
            }
            lastLooked = nextLineIdx + 1;
          } while (true);
        }
      }
    }

    myAllContents = allContents;
    myList.setListData(ArrayUtil.toStringArray(shortened));
  }

  protected abstract String getStringRepresentationFor(final Data content);

  protected abstract List<Data> getContents();

  public int getSelectedIndex() {
    if (myList.getSelectedIndex() == -1) return 0;
    return myList.getSelectedIndex();
  }
  
  @NotNull
  public int[] getSelectedIndices() {
    return myList.getSelectedIndices();
  }

  public List<Data> getAllContents() {
    return myAllContents;
  }

  @NotNull
  public String getSelectedText() {
    String result = "";
    for (int i : getSelectedIndices()) {
      String s = getStringRepresentationFor(myAllContents.get(i));
      result += StringUtil.convertLineSeparators(s);
    }
    return result;
  }
  
  private static class MyListCellRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      // Fix GTK background
      if (UIUtil.isUnderGTKLookAndFeel()){
        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        UIUtil.changeBackGround(this, background);
      }
      setIcon(textIcon);
      if (index <= 9) {
        append(String.valueOf((index + 1) % 10) + "  ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      append((String) value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
