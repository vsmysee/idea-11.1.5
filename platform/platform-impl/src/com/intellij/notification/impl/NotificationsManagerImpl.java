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
package com.intellij.notification.impl;

import com.intellij.ide.FrameStateManager;
import com.intellij.notification.*;
import com.intellij.notification.impl.ui.NotificationsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.BalloonImpl;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author spleaner
 */
public class NotificationsManagerImpl extends NotificationsManager implements Notifications, ApplicationComponent {

  @NotNull
  public String getComponentName() {
    return "NotificationsManager";
  }

  public void initComponent() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(TOPIC, this);
  }

  public static NotificationsManagerImpl getNotificationsManagerImpl() {
    return (NotificationsManagerImpl) ApplicationManager.getApplication().getComponent(NotificationsManager.class);
  }

  public void notify(@NotNull Notification notification) {
    doNotify(notification, NotificationDisplayType.BALLOON, null);
  }

  @Override
  public void register(@NotNull String groupDisplayName, @NotNull NotificationDisplayType defaultDisplayType) {
  }

  @Override
  public void register(@NotNull String groupDisplayName,
                       @NotNull NotificationDisplayType defaultDisplayType,
                       boolean shouldLog) {
  }

  @Override
  public void expire(@NotNull Notification notification) {
    EventLog.expire(notification);
  }

  @Override
  public <T extends Notification> T[] getNotificationsOfType(Class<T> klass, @Nullable final Project project) {
    final List<T> result = new ArrayList<T>();
    if (project == null || !project.isDefault()) {
      for (Notification notification : EventLog.getLogModel(project).getNotifications()) {
        if (klass.isInstance(notification)) {
          //noinspection unchecked
          result.add((T) notification);
        }
      }
    }
    return ArrayUtil.toObjectArray(result, klass);
  }

  public void disposeComponent() {
  }

  public static void doNotify(@NotNull final Notification notification,
                              @Nullable NotificationDisplayType displayType,
                              @Nullable final Project project) {
    final NotificationsConfigurationImpl configuration = NotificationsConfigurationImpl.getNotificationsConfigurationImpl();
    if (!configuration.isRegistered(notification.getGroupId())) {
      configuration.register(notification.getGroupId(), displayType == null ? NotificationDisplayType.BALLOON : displayType);
    }

    final NotificationSettings settings = NotificationsConfigurationImpl.getSettings(notification.getGroupId());
    boolean shouldLog = settings.isShouldLog();
    boolean displayable = settings.getDisplayType() != NotificationDisplayType.NONE;

    boolean willBeShown = displayable && NotificationsConfigurationImpl.getNotificationsConfigurationImpl().SHOW_BALLOONS;
    if (!shouldLog && !willBeShown) {
      notification.expire();
    }

    if (NotificationsConfigurationImpl.getNotificationsConfigurationImpl().SHOW_BALLOONS) {
      final Runnable runnable = new Runnable() {
        @Override
        public void run() {
          showNotification(notification, project);
        }
      };
      if (project == null) {
        runnable.run();
      } else if (!project.isDisposed()) {
        StartupManager.getInstance(project).runWhenProjectIsInitialized(runnable);
      }
    }
  }

  private static void showNotification(final Notification notification, @Nullable final Project project) {
    String groupId = notification.getGroupId();
    final NotificationSettings settings = NotificationsConfigurationImpl.getSettings(groupId);

    NotificationDisplayType type = settings.getDisplayType();
    String toolWindowId = NotificationsConfigurationImpl.getNotificationsConfigurationImpl().getToolWindowId(groupId);
    if (type == NotificationDisplayType.TOOL_WINDOW &&
        (toolWindowId == null || project == null || !Arrays.asList(ToolWindowManager.getInstance(project).getToolWindowIds()).contains(toolWindowId))) {
      type = NotificationDisplayType.BALLOON;
    }

    switch (type) {
      case NONE:
        return;
      //case EXTERNAL:
      //  notifyByExternal(notification);
      //  break;
      case STICKY_BALLOON:
      case BALLOON:
      default:
        Balloon balloon = notifyByBalloon(notification, type, project);
        if (!settings.isShouldLog() || type == NotificationDisplayType.STICKY_BALLOON) {
          if (balloon == null) {
            notification.expire();
          } else {
            balloon.addListener(new JBPopupAdapter() {
              @Override
              public void onClosed(LightweightWindowEvent event) {
                notification.expire();
              }
            });
          }
        }
        break;
      case TOOL_WINDOW:
        MessageType messageType = notification.getType() == NotificationType.ERROR
                            ? MessageType.ERROR
                            : notification.getType() == NotificationType.WARNING ? MessageType.WARNING : MessageType.INFO;
        final NotificationListener notificationListener = notification.getListener();
        HyperlinkListener listener = notificationListener == null ? null : new HyperlinkListener() {
          @Override
          public void hyperlinkUpdate(HyperlinkEvent e) {
            notificationListener.hyperlinkUpdate(notification, e);
          }
        };
        assert toolWindowId != null;
        String msg = notification.getTitle();
        if (StringUtil.isNotEmpty(notification.getContent())) {
          if (StringUtil.isNotEmpty(msg)) {
            msg += "<br>";
          }
          msg += notification.getContent();
        }

        //noinspection SSBasedInspection
        ToolWindowManager.getInstance(project).notifyByBalloon(toolWindowId, messageType, msg, notification.getIcon(), listener);
    }
  }

  @Nullable
  private static Balloon notifyByBalloon(final Notification notification,
                                      final NotificationDisplayType displayType,
                                      @Nullable final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;

    Window window = findWindowForBalloon(project);
    if (window instanceof IdeFrameImpl) {
      final ProjectManager projectManager = ProjectManager.getInstance();
      final boolean noProjects = projectManager.getOpenProjects().length == 0;
      final boolean sticky = NotificationDisplayType.STICKY_BALLOON == displayType || noProjects;
      final Balloon balloon = createBalloon(notification, false, false);
      Disposer.register(project != null ? project : ApplicationManager.getApplication(), balloon);

      if (notification.isExpired()) {
        return null;
      }

      ((IdeFrameImpl)window).getBalloonLayout().add(balloon);
      if (NotificationDisplayType.BALLOON == displayType) {
        FrameStateManager.getInstance().getApplicationActive().doWhenDone(new Runnable() {
          @Override
          public void run() {
            if (balloon.isDisposed()) {
              return;
            }

            if (!sticky) {
              ((BalloonImpl)balloon).startFadeoutTimer(3000);
            }
            else //noinspection ConstantConditions
              if (noProjects) {
              projectManager.addProjectManagerListener(new ProjectManagerAdapter() {
                @Override
                public void projectOpened(Project project) {
                  projectManager.removeProjectManagerListener(this);
                  if (!balloon.isDisposed()) {
                    ((BalloonImpl)balloon).startFadeoutTimer(300);
                  }
                }
              });
            }
          }
        });
      }
      return balloon;
    }
    return null;
  }

  @Nullable
  public static Window findWindowForBalloon(Project project) {
    final JFrame frame = WindowManager.getInstance().getFrame(project);
    if (frame == null && project == null) {
      return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    }
    return frame;
  }

  public static Balloon createBalloon(final Notification notification, final boolean showCallout, final boolean hideOnClickOutside) {
    final JEditorPane text = new JEditorPane();
    text.setEditorKit(UIUtil.getHTMLEditorKit());

    final HyperlinkListener listener = NotificationsUtil.wrapListener(notification);
    if (listener != null) {
      text.addHyperlinkListener(listener);
    }

    final JLabel label = new JLabel(NotificationsUtil.buildHtml(notification, null));
    text.setText(NotificationsUtil.buildHtml(notification, "width:" + Math.min(400, label.getPreferredSize().width) + "px;"));
    text.setEditable(false);
    text.setOpaque(false);

    if (UIUtil.isUnderNimbusLookAndFeel()) {
      text.setBackground(UIUtil.TRANSPARENT_COLOR);
    }

    text.setBorder(null);

    final JPanel content = new NonOpaquePanel(new BorderLayout((int)(label.getIconTextGap() * 1.5), (int)(label.getIconTextGap() * 1.5)));

    final NonOpaquePanel textWrapper = new NonOpaquePanel(new GridBagLayout());
    textWrapper.add(text);
    content.add(textWrapper, BorderLayout.CENTER);

    final NonOpaquePanel north = new NonOpaquePanel(new BorderLayout());
    north.add(new JLabel(NotificationsUtil.getIcon(notification)), BorderLayout.NORTH);
    content.add(north, BorderLayout.WEST);

    content.setBorder(new EmptyBorder(2, 4, 2, 4));

    final BalloonBuilder builder = JBPopupFactory.getInstance().createBalloonBuilder(content);
    builder.setFillColor(NotificationsUtil.getBackground(notification)).setCloseButtonEnabled(true).setShowCallout(showCallout)
      .setHideOnClickOutside(hideOnClickOutside)
      .setHideOnAction(hideOnClickOutside)
      .setHideOnKeyOutside(hideOnClickOutside).setHideOnFrameResize(false);

    final Balloon balloon = builder.createBalloon();
    notification.setBalloon(balloon);
    return balloon;
  }

}
