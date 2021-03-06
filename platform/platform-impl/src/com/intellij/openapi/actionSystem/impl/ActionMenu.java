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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.actionholder.ActionRef;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.plaf.beg.IdeaMenuUI;
import com.intellij.ui.plaf.gtk.GtkMenuUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.plaf.MenuItemUI;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public final class ActionMenu extends JMenu {
  private final String myPlace;
  private DataContext myContext;
  private final ActionRef<ActionGroup> myGroup;
  private final PresentationFactory myPresentationFactory;
  private final Presentation myPresentation;
  /**
   * Defines whether menu shows its mnemonic or not.
   */
  private boolean myMnemonicEnabled;
  private MenuItemSynchronizer myMenuItemSynchronizer;
  /**
   * This is PATCH!!!
   * Do not remove this STUPID code. Otherwise you will lose all keyboard navigation
   * at JMenuBar.
   */
  private StubItem myStubItem;
  private final boolean myTopLevel;

  public ActionMenu(final DataContext context,
                    final String place,
                    final ActionGroup group,
                    final PresentationFactory presentationFactory,
                    final boolean enableMnemonics,
                    final boolean topLevel) {
    myContext = context;
    myPlace = place;
    myGroup = ActionRef.fromAction(group);
    myPresentationFactory = presentationFactory;
    myPresentation = myPresentationFactory.getPresentation(group);
    myMnemonicEnabled = enableMnemonics;
    myTopLevel = topLevel;

    updateUI();

    init();

    // addNotify won't be called for menus in MacOS system menu
    if (SystemInfo.isMacSystemMenu) {
      installSynchronizer();
    }
  }

  public void updateContext(DataContext context) {
    myContext = context;
  }

  public void addNotify() {
    super.addNotify();
    installSynchronizer();
  }

  private void installSynchronizer() {
    if (myMenuItemSynchronizer == null) {
      myMenuItemSynchronizer = new MenuItemSynchronizer();
      myGroup.getAction().addPropertyChangeListener(myMenuItemSynchronizer);
      myPresentation.addPropertyChangeListener(myMenuItemSynchronizer);
    }
  }

  @Override
  public void removeNotify() {
    uninstallSynchronizer();
    super.removeNotify();
  }

  private void uninstallSynchronizer() {
    if (myMenuItemSynchronizer != null) {
      myGroup.getAction().removePropertyChangeListener(myMenuItemSynchronizer);
      myPresentation.removePropertyChangeListener(myMenuItemSynchronizer);
      myMenuItemSynchronizer = null;
    }
  }

  @Override
  public void updateUI() {
    if (UIUtil.isStandardMenuLAF()) {
      super.updateUI();

      if (myTopLevel && UIUtil.isUnderGTKLookAndFeel() && "Ambiance".equalsIgnoreCase(UIUtil.getGtkThemeName())) {
        setForeground(UIUtil.GTK_AMBIANCE_TEXT_COLOR);
        setBackground(UIUtil.GTK_AMBIANCE_BACKGROUND_COLOR);
      }
    }
    else {
      setUI(IdeaMenuUI.createUI(this));
      setFont(UIUtil.getMenuFont());

      JPopupMenu popupMenu = getPopupMenu();
      if (popupMenu != null) {
        popupMenu.updateUI();
      }
    }
  }

  @Override
  public void setUI(final MenuItemUI ui) {
    final MenuItemUI newUi = !myTopLevel && UIUtil.isUnderGTKLookAndFeel() && GtkMenuUI.isUiAcceptable(ui) ? new GtkMenuUI(ui) : ui;
    super.setUI(newUi);
  }

  private void init() {
    boolean macSystemMenu = SystemInfo.isMacSystemMenu && myPlace == ActionPlaces.MAIN_MENU;

    myStubItem = macSystemMenu ? null : new StubItem();
    addStubItem();
    addMenuListener(new MenuListenerImpl());
    setBorderPainted(false);

    setVisible(myPresentation.isVisible());
    setEnabled(myPresentation.isEnabled());
    setText(myPresentation.getText());
    updateIcon();

    setMnemonicEnabled(myMnemonicEnabled);
  }

  private void addStubItem() {
    if (myStubItem != null) {
      add(myStubItem);
    }
  }

  public void setMnemonicEnabled(boolean enable) {
    myMnemonicEnabled = enable;
    setMnemonic(myPresentation.getMnemonic());
    setDisplayedMnemonicIndex(myPresentation.getDisplayedMnemonicIndex());
  }

  @Override
  public void setDisplayedMnemonicIndex(final int index) throws IllegalArgumentException {
    super.setDisplayedMnemonicIndex(myMnemonicEnabled ? index : -1);
  }

  @Override
  public void setMnemonic(int mnemonic) {
    super.setMnemonic(myMnemonicEnabled ? mnemonic : 0);
  }

  private void updateIcon() {
    if (UISettings.getInstance().SHOW_ICONS_IN_MENUS) {
      final Presentation presentation = myPresentation;
      final Icon icon = presentation.getIcon();
      setIcon(icon);
      if (presentation.getDisabledIcon() != null) {
        setDisabledIcon(presentation.getDisabledIcon());
      }
      else {
        setDisabledIcon(IconLoader.getDisabledIcon(icon));
      }
    }
  }

  @Override
  public void menuSelectionChanged(boolean isIncluded) {
    super.menuSelectionChanged(isIncluded);
    showDescriptionInStatusBar(isIncluded, this, myPresentation.getDescription());
  }

  public static void showDescriptionInStatusBar(boolean isIncluded, Component component, String description) {
    IdeFrame frame = component instanceof IdeFrame
                         ? (IdeFrame)component
                         : (IdeFrame)SwingUtilities.getAncestorOfClass(IdeFrame.class, component);
    StatusBar statusBar;
    if (frame != null && (statusBar = frame.getStatusBar()) != null) {
      statusBar.setInfo(isIncluded ? description : null);
    }
  }

  private class MenuListenerImpl implements MenuListener {
    public void menuCanceled(MenuEvent e) {
      clearItems();
      addStubItem();
    }

    public void menuDeselected(MenuEvent e) {
      clearItems();
      addStubItem();
    }

    public void menuSelected(MenuEvent e) {
      fillMenu();
    }
  }

  private void clearItems() {
    if (SystemInfo.isMacSystemMenu && myPlace == ActionPlaces.MAIN_MENU) {
      for (Component menuComponent : getMenuComponents()) {
        if (menuComponent instanceof ActionMenu) {
          ((ActionMenu)menuComponent).clearItems();
          if (SystemInfo.isMacSystemMenu) {
            // hideNotify is not called on Macs
            ((ActionMenu)menuComponent).uninstallSynchronizer();
          }

        }
        else if (menuComponent instanceof ActionMenuItem) {
          ((ActionMenuItem)menuComponent).setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F24, 0));
        }
      }
    }

    removeAll();
    validate();
  }

  private void fillMenu() {
    boolean mayContextBeInvalid;
    DataContext context;

    if (myContext != null) {
      context = myContext;
      mayContextBeInvalid = false;
    }
    else {
      context = DataManager.getInstance().getDataContext();
      mayContextBeInvalid = true;
    }
    Utils.fillMenu(myGroup.getAction(), this, myMnemonicEnabled, myPresentationFactory, context, myPlace, true, mayContextBeInvalid);
  }

  private class MenuItemSynchronizer implements PropertyChangeListener {
    public void propertyChange(PropertyChangeEvent e) {
      String name = e.getPropertyName();
      if (Presentation.PROP_VISIBLE.equals(name)) {
        setVisible(myPresentation.isVisible());
        if (SystemInfo.isMacSystemMenu && myPlace.equals(ActionPlaces.MAIN_MENU)) {
          validateTree();
        }
      }
      else if (Presentation.PROP_ENABLED.equals(name)) {
        setEnabled(myPresentation.isEnabled());
      }
      else if (Presentation.PROP_MNEMONIC_KEY.equals(name)) {
        setMnemonic(myPresentation.getMnemonic());
      }
      else if (Presentation.PROP_MNEMONIC_INDEX.equals(name)) {
        setDisplayedMnemonicIndex(myPresentation.getDisplayedMnemonicIndex());
      }
      else if (Presentation.PROP_TEXT.equals(name)) {
        setText(myPresentation.getText());
      }
      else if (Presentation.PROP_ICON.equals(name) || Presentation.PROP_DISABLED_ICON.equals(name)) {
        updateIcon();
      }
    }
  }
}
