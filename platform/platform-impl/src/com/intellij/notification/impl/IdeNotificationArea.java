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
package com.intellij.notification.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.notification.EventLog;
import com.intellij.notification.LogModel;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.IconLikeCustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class IdeNotificationArea extends JLabel implements CustomStatusBarWidget, IconLikeCustomStatusBarWidget {
  private static final Icon EMPTY_ICON = IconLoader.getIcon("/ide/notifications.png");
  private static final Icon ERROR_ICON = IconLoader.getIcon("/ide/error_notifications.png");
  private static final Icon WARNING_ICON = IconLoader.getIcon("/ide/warning_notifications.png");
  private static final Icon INFO_ICON = IconLoader.getIcon("/ide/info_notifications.png");
  public static final String WIDGET_ID = "Notifications";

  private StatusBar myStatusBar;
  private final Alarm myLogAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  public IdeNotificationArea() {
    Disposer.register(this, myLogAlarm);
    UISettings.getInstance().addUISettingsListener(new UISettingsListener() {
      @Override
      public void uiSettingsChanged(UISettings source) {
        updateStatus();
      }
    }, this);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        EventLog.toggleLog(getProject());
      }
    });
  }

  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  public void dispose() {
  }

  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;

    new Runnable() {
      @Override
      public void run() {
        updateStatus();
        myLogAlarm.addRequest(this, 100, true);
      }
    }.run();

  }

  @Nullable
  private Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext((Component) myStatusBar));
  }

  @NotNull
  public String ID() {
    return WIDGET_ID;
  }

  private void updateStatus() {
    final Project project = getProject();
    LogModel logModel = EventLog.getLogModel(project);
    ToolWindow eventLog = EventLog.getEventLog(project);
    if (eventLog != null && eventLog.isVisible()) {
      logModel.logShown();
    }
    boolean stripesVisible = !UISettings.getInstance().HIDE_TOOL_STRIPES;
    ArrayList<Notification> notifications = logModel.getNotifications();
    LayeredIcon icon = new LayeredIcon(2);
    Icon statusIcon = getPendingNotificationsIcon(EMPTY_ICON, getMaximumType(notifications));
    icon.setIcon(statusIcon, 0);
    final int count = notifications.size();
    if (count > 0) {
      icon.setIcon(new TextIcon(this, String.valueOf(count)), 1, statusIcon.getIconWidth() - 2, 0);
    }
    if (stripesVisible && eventLog != null) {
      eventLog.setIcon(icon);
      setIcon(null);
    } else {
      setIcon(icon);
    }
    setToolTipText(count > 0 ? String.format("%s notification%s pending", count, count == 1 ? "" : "s") : "No new notifications");

    myStatusBar.updateWidget(ID());
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  private static Icon getPendingNotificationsIcon(Icon defIcon, final NotificationType maximumType) {
    if (maximumType != null) {
      switch (maximumType) {
        case WARNING: return WARNING_ICON;
        case ERROR: return ERROR_ICON;
        case INFORMATION: return INFO_ICON;
      }
    }
    return defIcon;
  }

  @Nullable
  private static NotificationType getMaximumType(List<Notification> notifications) {
    NotificationType result = null;
    for (Notification notification : notifications) {
      if (NotificationType.ERROR == notification.getType()) {
        return NotificationType.ERROR;
      }

      if (NotificationType.WARNING == notification.getType()) {
        result = NotificationType.WARNING;
      }
      else if (result == null && NotificationType.INFORMATION == notification.getType()) {
        result = NotificationType.INFORMATION;
      }
    }

    return result;
  }

  private static class TextIcon implements Icon {
    private final String myStr;
    private final JComponent myComponent;
    private final int myWidth;

    public TextIcon(IdeNotificationArea component, String str) {
      myStr = str;
      myComponent = component;
      myWidth = myComponent.getFontMetrics(calcFont()).stringWidth(myStr) + 1;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Font originalFont = g.getFont();
      Color originalColor = g.getColor();
      g.setFont(calcFont());
      y += getIconHeight() - g.getFontMetrics().getDescent();

      g.setColor(Color.BLACK);
      g.drawString(myStr, x, y);

      g.setFont(originalFont);
      g.setColor(originalColor);
    }

    private Font calcFont() {
      return myComponent.getFont().deriveFont(Font.BOLD).deriveFont((float) getIconHeight() * 3 / 5);
    }

    @Override
    public int getIconWidth() {
      return myWidth;
    }

    @Override
    public int getIconHeight() {
      return EMPTY_ICON.getIconHeight();
    }
  }
}
