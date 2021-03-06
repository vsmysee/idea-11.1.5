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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.actionholder.ActionRef;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.SizedIcon;
import com.intellij.ui.plaf.beg.BegMenuItemUI;
import com.intellij.ui.plaf.gtk.GtkMenuItemUI;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.plaf.MenuItemUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;

public class ActionMenuItem extends JCheckBoxMenuItem {
  private static final Icon ourCheckedIcon = new SizedIcon(PlatformIcons.CHECK_ICON, 18, 18);
  private static final Icon ourUncheckedIcon = EmptyIcon.ICON_18;

  private final ActionRef<AnAction> myAction;
  private final Presentation myPresentation;
  private final String myPlace;
  private DataContext myContext;
  private AnActionEvent myEvent;
  private MenuItemSynchronizer myMenuItemSynchronizer;
  private final boolean myEnableMnemonics;
  private final boolean myToggleable;
  private boolean myToggled;

  public ActionMenuItem(final AnAction action,
                        final Presentation presentation,
                        final String place,
                        final DataContext context,
                        final boolean enableMnemonics,
                        final boolean prepareNow) {
    myAction = ActionRef.fromAction(action);
    myPresentation = presentation;
    myPlace = place;
    myContext = context;
    myEnableMnemonics = enableMnemonics;
    myToggleable = action instanceof Toggleable;

    myEvent = new AnActionEvent(null, context, place, myPresentation, ActionManager.getInstance(), 0);
    addActionListener(new ActionTransmitter());
    setBorderPainted(false);

    updateUI();

    if (prepareNow) {
      init();
    }
    else {
      setText("loading...");
    }
  }

  public void prepare() {
    init();
    installSynchronizer();
  }

  /**
   * We have to make this method public to allow BegMenuItemUI to invoke it.
   */
  @Override
  public void fireActionPerformed(ActionEvent event) {
    super.fireActionPerformed(event);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    installSynchronizer();
    init();
  }

  @Override
  public void removeNotify() {
    uninstallSynchronizer();
    super.removeNotify();
  }

  private void installSynchronizer() {
    if (myMenuItemSynchronizer == null) {
      myMenuItemSynchronizer = new MenuItemSynchronizer();
    }
  }

  private void uninstallSynchronizer() {
    if (myMenuItemSynchronizer != null) {
      Disposer.dispose(myMenuItemSynchronizer);
      myMenuItemSynchronizer = null;
    }
  }

  private void init() {
    setVisible(myPresentation.isVisible());
    setEnabled(myPresentation.isEnabled());
    setMnemonic(myEnableMnemonics ? myPresentation.getMnemonic() : 0);
    setText(myPresentation.getText());
    final int mnemonicIndex = myEnableMnemonics ? myPresentation.getDisplayedMnemonicIndex() : -1;

    if (getText() != null && mnemonicIndex >= 0 && mnemonicIndex < getText().length()) {
      setDisplayedMnemonicIndex(mnemonicIndex);
    }

    AnAction action = myAction.getAction();
    updateIcon(action);
    String id = ActionManager.getInstance().getId(action);
    if (id != null) {
      Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(id);
      setAcceleratorFromShortcuts(shortcuts);
    }
    else {
      final ShortcutSet shortcutSet = action.getShortcutSet();
      if (shortcutSet != null) {
        setAcceleratorFromShortcuts(shortcutSet.getShortcuts());
      }
    }
  }

  private void setAcceleratorFromShortcuts(final Shortcut[] shortcuts) {
    for (Shortcut shortcut : shortcuts) {
      if (shortcut instanceof KeyboardShortcut) {
        final KeyStroke firstKeyStroke = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
        //If action has Enter shortcut, do not add it. Otherwise, user won't be able to chose any ActionMenuItem other than that
        if (!isEnterKeyStroke(firstKeyStroke)) {
          setAccelerator(firstKeyStroke);
        }
        break;
      }
    }
  }

  private static boolean isEnterKeyStroke(KeyStroke keyStroke) {
    return keyStroke.getKeyCode() == KeyEvent.VK_ENTER && keyStroke.getModifiers() == 0;
  }

  @Override
  public void updateUI() {
    if (UIUtil.isStandardMenuLAF()) {
      super.updateUI();
    }
    else {
      setUI(BegMenuItemUI.createUI(this));
    }
  }

  @Override
  public void setUI(final MenuItemUI ui) {
    final MenuItemUI newUi = UIUtil.isUnderGTKLookAndFeel() && GtkMenuItemUI.isUiAcceptable(ui) ? new GtkMenuItemUI(ui) : ui;
    super.setUI(newUi);
  }

  /**
   * Updates long description of action at the status bar.
   */
  @Override
  public void menuSelectionChanged(boolean isIncluded) {
    super.menuSelectionChanged(isIncluded);
    ActionMenu.showDescriptionInStatusBar(isIncluded, this, myPresentation.getDescription());
  }

  public String getFirstShortcutText() {
    return KeymapUtil.getFirstKeyboardShortcutText(myAction.getAction());
  }

  public void updateContext(DataContext context) {
    myContext = context;
    myEvent = new AnActionEvent(null, context, myPlace, myPresentation, ActionManager.getInstance(), 0);
  }

  private final class ActionTransmitter implements ActionListener {
    /**
     * @param component component
     * @return whether the component in Swing tree or not. This method is more
     *         weak then {@link Component#isShowing() }
     */
    private boolean isInTree(final Component component) {
      if (component instanceof Window) {
        return component.isShowing();
      }
      else {
        Window windowAncestor = SwingUtilities.getWindowAncestor(component);
        return windowAncestor != null && windowAncestor.isShowing();
      }
    }

    public void actionPerformed(final ActionEvent e) {
      final IdeFocusManager fm = IdeFocusManager.findInstanceByContext(myContext);
      final ActionCallback typeahead = new ActionCallback();
      fm.typeAheadUntil(typeahead);
      fm.runOnOwnContext(myContext, new Runnable() {
        @Override
        public void run() {
          final AnActionEvent event = new AnActionEvent(
            new MouseEvent(ActionMenuItem.this, MouseEvent.MOUSE_PRESSED, 0, e.getModifiers(), getWidth() / 2, getHeight() / 2, 1, false),
            myContext, myPlace, myPresentation, ActionManager.getInstance(), e.getModifiers()
          );
          final AnAction action = myAction.getAction();
          if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
            ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
            actionManager.fireBeforeActionPerformed(action, myContext, event);
            Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(event.getDataContext());
            if (component != null && !isInTree(component)) {
              typeahead.setDone();
              return;
            }

            SimpleTimer.getInstance().setUp(new Runnable() {
              @Override
              public void run() {
                SwingUtilities.invokeLater(new Runnable() {
                  @Override
                  public void run() {
                    fm.doWhenFocusSettlesDown(new Runnable() {
                      @Override
                      public void run() {
                        typeahead.setDone();
                      }
                    });
                  }
                });
              }
            }, Registry.intValue("actionSystem.typeAheadTimeAfterPopupAction"));

            action.actionPerformed(event);
            actionManager.queueActionPerformedEvent(action, myContext, event);
          } else {
            typeahead.setDone();
          }
        }
      });
    }
  }

  private void updateIcon(AnAction action) {
    if (isToggleable() && myPresentation.getIcon() == null) {
      action.update(myEvent);
      myToggled = Boolean.TRUE.equals(myEvent.getPresentation().getClientProperty(Toggleable.SELECTED_PROPERTY));
      if ((ActionPlaces.MAIN_MENU.equals(myPlace) && SystemInfo.isMacSystemMenu)
          || UIUtil.isUnderNimbusLookAndFeel()
          || (UIUtil.isUnderWindowsLookAndFeel() && !SystemInfo.isWindowsXP)) {
        setState(myToggled);
      }
      else if (!(getUI() instanceof GtkMenuItemUI)) {
        if (myToggled) {
          setIcon(ourCheckedIcon);
          setDisabledIcon(IconLoader.getDisabledIcon(ourCheckedIcon));
        }
        else {
          setIcon(ourUncheckedIcon);
          setDisabledIcon(IconLoader.getDisabledIcon(ourUncheckedIcon));
        }
      }
    }
    else {
      if (UISettings.getInstance().SHOW_ICONS_IN_MENUS) {
        Icon icon = myPresentation.getIcon();
        setIcon(icon);
        if (myPresentation.getDisabledIcon() != null) {
          setDisabledIcon(myPresentation.getDisabledIcon());
        }
        else {
          setDisabledIcon(IconLoader.getDisabledIcon(icon));
        }
      }
    }
  }

  public boolean isToggleable() {
    return myToggleable;
  }

  @Override
  public boolean isSelected() {
    return myToggled;
  }

  private final class MenuItemSynchronizer implements PropertyChangeListener, Disposable {
    @NonNls private static final String SELECTED = "selected";

    private final Set<String> mySynchronized = new HashSet<String>();

    private MenuItemSynchronizer() {
      myPresentation.addPropertyChangeListener(this);
    }

    public void dispose() {
      myPresentation.removePropertyChangeListener(this);
    }

    public void propertyChange(PropertyChangeEvent e) {
      boolean queueForDispose = getParent() == null;

      String name = e.getPropertyName();
      if (mySynchronized.contains(name)) return;

      mySynchronized.add(name);

      try {
        if (Presentation.PROP_VISIBLE.equals(name)) {
          final boolean visible = myPresentation.isVisible();
          if (!visible && SystemInfo.isMacSystemMenu && myPlace.equals(ActionPlaces.MAIN_MENU)) {
            setEnabled(false);
          }
          else {
            setVisible(visible);
          }
        }
        else if (Presentation.PROP_ENABLED.equals(name)) {
          setEnabled(myPresentation.isEnabled());
          updateIcon(myAction.getAction());
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
        else if (Presentation.PROP_ICON.equals(name) || Presentation.PROP_DISABLED_ICON.equals(name) || SELECTED.equals(name)) {
          updateIcon(myAction.getAction());
        }
      }
      finally {
        mySynchronized.remove(name);
        if (queueForDispose) {
          // later since we cannot remove property listeners inside event processing
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (getParent() == null) {
                uninstallSynchronizer();
              }
            }
          });
        }
      }
    }
  }
}
