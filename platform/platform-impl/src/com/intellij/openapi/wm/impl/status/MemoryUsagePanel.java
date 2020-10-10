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
package com.intellij.openapi.wm.impl.status;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.Gray;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MemoryUsagePanel extends JButton implements CustomStatusBarWidget {
  @NonNls
  private static final String SAMPLE_STRING = "0000M of 0000M";
  private static final int MEGABYTE = 1024 * 1024;
  private static final Color ourColorFree = Gray._240;
  private static final Color ourColorUsed = new Color(112, 135, 214);
  private static final Color ourColorUsed2 = new Color(166, 181, 230);

  private static final int HEIGHT = 16;
  public static final String WIDGET_ID = "Memory";

  private long myLastTotal = -1;
  private long myLastUsed = -1;
  private ScheduledFuture<?> myFuture;
  private BufferedImage myBufferedImage;
  private boolean myWasPressed;

  public MemoryUsagePanel() {
    setOpaque(false);
    setFocusable(false);

    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        System.gc();
        updateState();
      }
    });

    setBorder(StatusBarWidget.WidgetBorder.INSTANCE);
    updateUI();
  }

  public void dispose() {
    myFuture.cancel(true);
    myFuture = null;
  }

  public void install(@NotNull StatusBar statusBar) {
  }

  @Nullable
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  @NotNull
  public String ID() {
    return WIDGET_ID;
  }

  public void setShowing(final boolean showing) {
    if (showing && !isVisible()) {
      setVisible(true);
      revalidate();
    } else if (!showing && isVisible()) {
      setVisible(showing);
      revalidate();
    }
  }

  @Override
  public void updateUI() {
    super.updateUI();
    setFont(SystemInfo.isMac ? UIUtil.getLabelFont().deriveFont(11.0f) : UIUtil.getLabelFont());
  }

  public JComponent getComponent() {
    return this;
  }

  @Override
  public void paintComponent(final Graphics g) {
    final Dimension size = getSize();

    final boolean pressed = getModel().isPressed();
    final boolean forced = myWasPressed && !pressed || !myWasPressed && pressed;
    myWasPressed = pressed;

    if (myBufferedImage == null || forced) {
      myBufferedImage = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
      final Graphics bg = myBufferedImage.getGraphics().create();

      final Runtime runtime = Runtime.getRuntime();
      final long maxMemory = runtime.maxMemory();
      final long freeMemory = maxMemory - runtime.totalMemory() + runtime.freeMemory();

      final Insets insets = SystemInfo.isMac ? getInsets() : new Insets(0, 0, 0, 0);

      final int totalBarLength = size.width - insets.left - insets.right - (SystemInfo.isMac ? 0 : 0);
      final int usedBarLength = totalBarLength - (int)(totalBarLength * freeMemory / maxMemory);
      final int allocatedBarWidth = totalBarLength - (int)(totalBarLength * (freeMemory - runtime.freeMemory()) / maxMemory);
      final int barHeight = SystemInfo.isMac ? HEIGHT : size.height - insets.top - insets.bottom;
      final Graphics2D g2 = (Graphics2D)bg;

      final int yOffset = (size.height - barHeight) / 2;
      final int xOffset = insets.left + (SystemInfo.isMac ? 0 : 0);

      g2.setPaint(new GradientPaint(0, 0, Gray._190, 0, size.height - 1, Gray._230));
      g2.fillRect(xOffset, yOffset, totalBarLength, barHeight);

      g2.setPaint(new GradientPaint(0, 0, new Gray(200, 100), 0, size.height - 1, new Gray(150, 130)));
      g2.fillRect(xOffset + 1, yOffset, allocatedBarWidth, barHeight);

      g2.setColor(Gray._175);
      g2.drawLine(xOffset + allocatedBarWidth, yOffset + 1, xOffset + allocatedBarWidth, yOffset + barHeight - 1);

      if (pressed) {
        g2.setPaint(new GradientPaint(1, 1, new Color(101, 111, 135), 0, size.height - 2, new Color(175, 185, 202)));
        g2.fillRect(xOffset + 1, yOffset, usedBarLength, barHeight);
      } else {
        g2.setPaint(new GradientPaint(1, 1, new Color(175, 185, 202), 0, size.height - 2, new Color(126, 138, 168)));
        g2.fillRect(xOffset + 1, yOffset, usedBarLength, barHeight);

        if (SystemInfo.isMac) {
          g2.setColor(new Color(194, 197, 203));
          g2.drawLine(xOffset + 1, yOffset+1, allocatedBarWidth, yOffset+1);
        }
      }

      if (SystemInfo.isMac) {
        g2.setColor(Gray._110);
        g2.drawRect(xOffset, yOffset, totalBarLength, barHeight - 1);
      }

      g2.setFont(getFont());
      final long used = (maxMemory - freeMemory) / MEGABYTE;
      final long total = maxMemory / MEGABYTE;
      final String info = UIBundle.message("memory.usage.panel.message.text", Long.toString(used), Long.toString(total));
      final FontMetrics fontMetrics = g.getFontMetrics();
      final int infoWidth = fontMetrics.charsWidth(info.toCharArray(), 0, info.length());
      final int infoHeight = fontMetrics.getHeight() - fontMetrics.getDescent();
      UIUtil.applyRenderingHints(g2);

      g2.setColor(Color.black);
      g2.drawString(info, xOffset + (totalBarLength - infoWidth) / 2, yOffset + (barHeight + infoHeight) / 2 - 1);
      bg.dispose();
    }

    g.drawImage(myBufferedImage, 0, 0, null);
  }

  public final int getPreferredWidth() {
    final Insets insets = getInsets();
    return getFontMetrics(SystemInfo.isMac ? UIUtil.getLabelFont().deriveFont(11.0f) : UIUtil.getLabelFont()).stringWidth(SAMPLE_STRING) + insets.left + insets.right + (SystemInfo.isMac ? 2 : 0);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(getPreferredWidth(), isVisible() && getParent() != null ? getParent().getSize().height : super.getPreferredSize().height);
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  public void addNotify() {
    myFuture = JobScheduler.getScheduler().scheduleAtFixedRate(new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (!isDisplayable()) return; // This runnable may be posted in event queue while calling removeNotify.
            updateState();
          }
        });
      }
    }, 1, 5, TimeUnit.SECONDS);
    super.addNotify();
  }

  private void updateState() {
    if (!isShowing()) {
      return;
    }

    final Runtime runtime = Runtime.getRuntime();
    final long total = runtime.totalMemory() / MEGABYTE;
    final long used = total - runtime.freeMemory() / MEGABYTE;
    if (total != myLastTotal || used != myLastUsed) {
      myLastTotal = total;
      myLastUsed = used;

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myBufferedImage = null;
          repaint();
        }
      });

      setToolTipText(UIBundle.message("memory.usage.panel.statistics.message", total, used));
    }
  }
}
