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

package com.intellij.ide.util.gotoByName;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.ui.ScreenUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChooseByNamePopup extends ChooseByNameBase implements ChooseByNamePopupComponent{
  private static final Key<ChooseByNamePopup> CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY = new Key<ChooseByNamePopup>("ChooseByNamePopup");
  private Component myOldFocusOwner = null;
  private boolean myShowListForEmptyPattern = false;
  private boolean myMayRequestCurrentWindow;

  protected ChooseByNamePopup(@Nullable final Project project, final ChooseByNameModel model, ChooseByNameItemProvider provider, final ChooseByNamePopup oldPopup,
                            @Nullable final String predefinedText, boolean mayRequestOpenInCurrentWindow, int initialIndex) {
    super(project, model, provider, oldPopup != null ? oldPopup.getEnteredText() : predefinedText, initialIndex);
    if (oldPopup == null && predefinedText != null) {
      setPreselectInitialText(true);
    }
    if (oldPopup != null) { //inherit old focus owner
      myOldFocusOwner = oldPopup.myPreviouslyFocusedComponent;
    }
    myMayRequestCurrentWindow = mayRequestOpenInCurrentWindow;
  }

  public String getEnteredText() {
    return myTextField.getText();
  }

  public int getSelectedIndex() {
    return myList.getSelectedIndex();
  }

  protected void initUI(final Callback callback, final ModalityState modalityState, boolean allowMultipleSelection) {
    super.initUI(callback, modalityState, allowMultipleSelection);
    //LaterInvocator.enterModal(myTextFieldPanel);
    if (myInitialText != null) {
      rebuildList(myInitialIndex, 0, null, ModalityState.current(), null);
    }
    if (myOldFocusOwner != null){
      myPreviouslyFocusedComponent = myOldFocusOwner;
      myOldFocusOwner = null;
    }
  }

  @Override
  public boolean isOpenInCurrentWindowRequested() {
    return super.isOpenInCurrentWindowRequested() && myMayRequestCurrentWindow;
  }

  protected boolean isCheckboxVisible() {
    return true;
  }

  protected boolean isShowListForEmptyPattern(){
    return myShowListForEmptyPattern;
  }

  public void setShowListForEmptyPattern(boolean showListForEmptyPattern) {
    myShowListForEmptyPattern = showListForEmptyPattern;
  }

  protected boolean isCloseByFocusLost() {
    return UISettings.getInstance().HIDE_NAVIGATION_ON_FOCUS_LOSS;
  }

  protected void showList() {
    final JLayeredPane layeredPane = myTextField.getRootPane().getLayeredPane();

    Rectangle bounds = new Rectangle(myTextFieldPanel.getLocationOnScreen(), myTextField.getSize());
    bounds.y += myTextFieldPanel.getHeight() + (SystemInfo.isMac ? 3 : 1);

    final Dimension preferredScrollPaneSize = myListScrollPane.getPreferredSize();
    if (myList.getModel().getSize() == 0) {
      preferredScrollPaneSize.height = UIManager.getFont("Label.font").getSize();
    }

    preferredScrollPaneSize.width = Math.max(myTextFieldPanel.getWidth(), preferredScrollPaneSize.width);

    Rectangle preferredBounds = new Rectangle(bounds.x, bounds.y, preferredScrollPaneSize.width, preferredScrollPaneSize.height);
    Rectangle original = new Rectangle(preferredBounds);

    ScreenUtil.fitToScreen(preferredBounds);
    if (original.width > preferredBounds.width) {
      int height = myListScrollPane.getHorizontalScrollBar().getPreferredSize().height;
      preferredBounds.height += height;
    }

    myListScrollPane.setVisible(true);
    myListScrollPane.setBorder(null);
    String adText = myMayRequestCurrentWindow ? "Press " + KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_MASK)) + " to open in current window" : null;
    if (myDropdownPopup == null) {
      ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(myListScrollPane, myListScrollPane);
      builder.setFocusable(false).setRequestFocus(false).setCancelKeyEnabled(false).setFocusOwners(new JComponent[] {myTextField}).setBelongsToGlobalPopupStack(false)
        .setModalContext(false).setAdText(adText).setMayBeParent(true);
      builder.setCancelCallback(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          return Boolean.TRUE;
        }
      });
      myDropdownPopup = builder.createPopup();
      myDropdownPopup.setLocation(preferredBounds.getLocation());
      myDropdownPopup.setSize(preferredBounds.getSize());
      myDropdownPopup.show(layeredPane);
    } else {
      myDropdownPopup.setLocation(preferredBounds.getLocation());

      // in 'focus follows mouse' mode, to avoid focus escaping to editor, don't reduce popup size when list size is reduced
      final Dimension currentSize = myDropdownPopup.getSize();
      if (UISettings.getInstance().HIDE_NAVIGATION_ON_FOCUS_LOSS ||
          preferredBounds.width > currentSize.width || preferredBounds.height > currentSize.height) {
        myDropdownPopup.setSize(preferredBounds.getSize());
      }
    }
  }

  protected void hideList() {
    if (myDropdownPopup != null) {
      myDropdownPopup.cancel();
      myDropdownPopup = null;
    }
  }

  protected void close(final boolean isOk) {
    if (checkDisposed()){
      return;
    }

    if (isOk){
      myModel.saveInitialCheckBoxState(myCheckBox.isSelected());

      final List<Object> chosenElements = getChosenElements();
      if (chosenElements != null) {
        if (myActionListener instanceof MultiElementsCallback) {
          ((MultiElementsCallback)myActionListener).elementsChosen(chosenElements);
        } else {
          for (Object element : chosenElements) {
            myActionListener.elementChosen(element);
            String text = myModel.getFullName(element);
            if (text != null) {
              StatisticsManager.getInstance().incUseCount(new StatisticsInfo(statisticsContext(), text));
            }
          }
        }
      } else {
        return;
      }

      if (!chosenElements.isEmpty()){
        final String enteredText = getEnteredText();
        if (enteredText.indexOf('*') >= 0) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.wildcards");
        }
        else {
          for (Object element : chosenElements) {
            final String name = myModel.getElementName(element);
            if (name != null) {
              if (!StringUtil.startsWithIgnoreCase(name, enteredText)) {
                FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.camelprefix");
                break;
              }
            }
          }
        }
      }
      else{
        return;
      }
    }

    setDisposed(true);
    myAlarm.cancelAllRequests();
    if (myProject != null) {
      myProject.putUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, null);
    }

    //LaterInvocator.leaveModal(myTextFieldPanel);

    cleanupUI(isOk);
    myActionListener.onClose ();
  }

  @Nullable
  public static ChooseByNamePopup getActivePopup(@NotNull final Project project) {
    return CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY.get(project);
  }

  private void cleanupUI(boolean ok) {
    if (myTextPopup != null) {
      if (ok) {
        myTextPopup.closeOk(null);
      } else {
        myTextPopup.cancel();
      }
      myTextPopup = null;
    }

    if (myDropdownPopup != null) {
      if (ok) {
        myDropdownPopup.closeOk(null);
      } else {
        myDropdownPopup.cancel();
      }
      myDropdownPopup = null;
    }
  }

  public static ChooseByNamePopup createPopup(final Project project, final ChooseByNameModel model, final PsiElement context) {
    return createPopup(project, model, new DefaultChooseByNameItemProvider(context), null);
  }

  public static ChooseByNamePopup createPopup(final Project project, final ChooseByNameModel model, final PsiElement context,
                                              @Nullable final String predefinedText) {
    return createPopup(project, model, new DefaultChooseByNameItemProvider(context), predefinedText, false, 0);
  }

  public static ChooseByNamePopup createPopup(final Project project, final ChooseByNameModel model, final PsiElement context,
                                              @Nullable final String predefinedText,
                                              boolean mayRequestOpenInCurrentWindow, final int initialIndex) {
     return createPopup(project,model,new DefaultChooseByNameItemProvider(context),predefinedText,mayRequestOpenInCurrentWindow,initialIndex);
  }

  public static ChooseByNamePopup createPopup(final Project project, final ChooseByNameModel model, final ChooseByNameItemProvider provider) {
    return createPopup(project, model, provider, null);
  }

  public static ChooseByNamePopup createPopup(final Project project, final ChooseByNameModel model, final ChooseByNameItemProvider provider,
                                              @Nullable final String predefinedText) {
    return createPopup(project, model, provider, predefinedText, false, 0);
  }

  public static ChooseByNamePopup createPopup(final Project project, final ChooseByNameModel model, final ChooseByNameItemProvider provider,
                                              @Nullable final String predefinedText,
                                              boolean mayRequestOpenInCurrentWindow, final int initialIndex) {
    final ChooseByNamePopup oldPopup = project != null ? project.getUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY) : null;
    if (oldPopup != null) {
      oldPopup.close(false);
    }
    ChooseByNamePopup newPopup = new ChooseByNamePopup(project, model, provider, oldPopup, predefinedText, mayRequestOpenInCurrentWindow,
                                                       initialIndex);

    if (project != null) {
      project.putUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, newPopup);
    }
    return newPopup;
  }

  private static final Pattern patternToDetectLinesAndColumns = Pattern.compile("(.+)(?::|@|,|#)(\\d+)?(?:(?:\\D)(\\d+)?)?");
  private static final Pattern patternToDetectAnonymousClasses = Pattern.compile("([\\.\\w]+)((\\$[\\d]+)*(\\$)?)");

  public String transformPattern(String pattern) {
    Pattern regex = null;
    if (pattern.indexOf(':') != -1 ||
        pattern.indexOf(',') != -1 ||
        pattern.indexOf(';') != -1 ||
        pattern.indexOf('#') != -1 ||
        pattern.indexOf('@') != -1) { // quick test if reg exp should be used
      regex = patternToDetectLinesAndColumns;
    }

    if (pattern.indexOf('$') != -1) {
      regex = patternToDetectAnonymousClasses;
    }

    if (regex != null) {
      final Matcher matcher = regex.matcher(pattern);
      if (matcher.matches()) {
        pattern = matcher.group(1);
      }
    }

    return super.transformPattern(pattern);
  }

  public int getLinePosition() {
    return getLineOrColumn(true);
  }

  private int getLineOrColumn(final boolean line) {
    final Matcher matcher = patternToDetectLinesAndColumns.matcher(getEnteredText());
    if (matcher.matches()) {
      final int groupNumber = line ? 2:3;
      try {
        if(groupNumber <= matcher.groupCount()) {
          final String group = matcher.group(groupNumber);
          if (group != null) return Integer.parseInt(group) - 1;
        }
        if (!line && getLineOrColumn(true) != -1) return 0;
      }
      catch (NumberFormatException ignored) {}
    }

    return -1;
  }
  
  @Nullable
  public String getPathToAnonymous() {
    final Matcher matcher = patternToDetectAnonymousClasses.matcher(getEnteredText());
    if (matcher.matches()) {
      String path = matcher.group(2);
      if (path != null) {
        path = path.trim();
        if (path.endsWith("$")) {
          path = path.substring(0, path.length() - 2);
        }
        if (!path.isEmpty()) return path;
      }
    }

    return null;    
  }

  public int getColumnPosition() {
    return getLineOrColumn(false);
  }
}
