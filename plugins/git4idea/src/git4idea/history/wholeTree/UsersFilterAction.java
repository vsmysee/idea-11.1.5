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
package git4idea.history.wholeTree;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorCustomization;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldProvider;
import com.intellij.util.Consumer;
import com.intellij.util.TextFieldCompletionProvider;
import com.intellij.util.TextFieldCompletionProviderDumbAware;
import git4idea.history.NewGitUsersComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author irengrig
 *         Date: 1/20/11
 *         Time: 7:35 PM
 */
public class UsersFilterAction extends BasePopupAction {
  public static final String ALL = "All";
  public static final String USER = "User:";
  private final UserFilterI myUserFilterI;
  private AnAction myAllAction;
  private AnAction mySelectMe;
  private AnAction mySelect;
  private String myCurrentText;
  private final NewGitUsersComponent myUsers;
  private EditorTextField myEditorField;
  private JBPopup myPopup;
  private ComponentPopupBuilder myComponentPopupBuilder;
  private AnAction mySelectOkAction;
  private TextFieldCompletionProvider myTextFieldCompletionProvider;
  private String myPreselectedUser;
  private DumbAwareAction myPresetUserAction;

  public UsersFilterAction(final Project project, final UserFilterI userFilterI) {
    super(project, USER, "User");
    myUserFilterI = userFilterI;
    myCurrentText = "";
    myUsers = NewGitUsersComponent.getInstance(myProject);
    myAllAction = new DumbAwareAction(ALL) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myLabel.setText(ALL);
        myUserFilterI.allSelected();
        myCurrentText = "";
        myPanel.setToolTipText(USER + " " + ALL);
        myPreselectedUser = null;
      }
    };
    mySelectMe = new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final String meText = getMeText();
        myLabel.setText(meText);
        myPanel.setToolTipText(USER + " " + meText);
        myUserFilterI.meSelected();
        myCurrentText = "";
        myPreselectedUser = null;
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setVisible(myUserFilterI.isMeKnown());
        e.getPresentation().setText(getMeText());
      }
    };
    myPresetUserAction = new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myLabel.setText(myPreselectedUser);
        myPanel.setToolTipText(USER + " " + myPreselectedUser);
        myUserFilterI.filter(myPreselectedUser);
        myCurrentText = myPreselectedUser;
        myPreselectedUser = null;
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setVisible((! StringUtil.isEmptyOrSpaces(myPreselectedUser)) &&
                                       (! Comparing.equal(myPreselectedUser, myUserFilterI.getMe())));
        e.getPresentation().setText(myPreselectedUser);
      }
    };
    createPopup(project);
    mySelect = new DumbAwareAction("Select..") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myPopup != null) {
          mySelectOkAction.unregisterCustomShortcutSet(myPopup.getContent());
        }
        myComponentPopupBuilder.setMayBeParent(true);
        myPopup = myComponentPopupBuilder.createPopup();
        myTextFieldCompletionProvider.apply(myEditorField);
        myEditorField.setText(myCurrentText);
        final JComponent content = myPopup.getContent();
        mySelectOkAction.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, content);
        final Point point = new Point(0, 0);
        SwingUtilities.convertPointToScreen(point, myLabel);
        myPopup.setMinimumSize(new Dimension(200, 90));
        myPopup.showInScreenCoordinates(myLabel, point);
        myPreselectedUser = null;
      }
    };
    myLabel.setText(ALL);
  }

  private void createPopup(Project project) {
    final JPanel panel = new JPanel(new BorderLayout());
    final EditorTextFieldProvider service = ServiceManager.getService(project, EditorTextFieldProvider.class);
    myEditorField = service.getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project,
                                           Collections.singletonList(EditorCustomization.Feature.SOFT_WRAP),
                                           Collections.singletonList(EditorCustomization.Feature.SPELL_CHECK));
    myEditorField.setBorder(new CompoundBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2), myEditorField.getBorder()));
    myEditorField.setText("s");
    myEditorField.setText(myCurrentText);
    myEditorField.setOneLineMode(false);
    panel.add(myEditorField, BorderLayout.CENTER);

    myTextFieldCompletionProvider = new TextFieldCompletionProviderDumbAware(true) {
      @NotNull
      @Override
      protected String getPrefix(@NotNull String currentTextPrefix) {
        final int text = currentTextPrefix.lastIndexOf(',');
        return text == -1 ? currentTextPrefix : currentTextPrefix.substring(text + 1).trim();
      }

      @Override
      protected void addCompletionVariants(@NotNull String text,
                                           int offset,
                                           @NotNull String prefix,
                                           @NotNull CompletionResultSet result) {
        final List<String> list = myUsers.get();
        if (list != null) {
          for (String completionVariant : list) {
            final LookupElementBuilder element = LookupElementBuilder.create(completionVariant);
            result.addElement(element.addLookupString(completionVariant.toLowerCase()));
          }
        }
      }
    };

    myComponentPopupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, myEditorField)
      .setCancelOnClickOutside(true)
      .setAdText(KeymapUtil.getShortcutsText(CommonShortcuts.CTRL_ENTER.getShortcuts()) + " to finish")
      .setTitle("Specify user names, comma separated")
      .setMovable(true)
      .setRequestFocus(true).setResizable(true);
    mySelectOkAction = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myPopup.closeOk(e.getInputEvent());
        final String newText = myEditorField.getText();
        if (Comparing.equal(newText.trim(), myCurrentText.trim())) return;
        myCurrentText = newText;
        setText(myCurrentText.trim());
        myPanel.setToolTipText(USER + " " + myCurrentText);
        myUserFilterI.filter(myCurrentText);
      }
    };
  }

  private void setText(final String text) {
    final String[] pieces = text.split(",");
    if (pieces.length == 0) {
      myLabel.setText(ALL);
    } else if (pieces.length == 1) {
      myLabel.setText(pieces[0].trim());
    } else {
      myLabel.setText(pieces[0].trim() + "+");
    }
  }

  public void setSelectedPresets(final String selected, final boolean meSelected) {
    myCurrentText = selected == null ? "" : selected;
    if (selected == null) {
      myLabel.setText(ALL);
    } else if (meSelected) {
      myLabel.setText(getMeText(selected));
      myPanel.setToolTipText(USER + " " + selected);
    } else {
      setText(selected);
      myPanel.setToolTipText(USER + " " + selected);
    }
  }

  private String getMeText() {
    return getMeText(myUserFilterI.getMe());
  }
  
  private String getMeText(final String name) {
    return "me ( " + name + " )";
  }

  @Override
  protected void createActions(Consumer<AnAction> actionConsumer) {
    actionConsumer.consume(myPresetUserAction);
    actionConsumer.consume(myAllAction);
    actionConsumer.consume(mySelectMe);
    actionConsumer.consume(mySelect);
  }

  public void setPreselectedUser(String preselectedUser) {
    myPreselectedUser = preselectedUser;
  }
}
