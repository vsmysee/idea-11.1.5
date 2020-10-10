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

import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class IdeaActionButtonLook extends ActionButtonLook {
  public void paintBackground(Graphics g, JComponent component, int state) {
    Dimension dimension = component.getSize();
    if (state != ActionButtonComponent.NORMAL) {
      if (!UIUtil.isUnderAquaLookAndFeel()) {
        if (state == ActionButtonComponent.POPPED) {
          g.setColor(new Color(181, 190, 214));
          g.fillRect(0, 0, dimension.width, dimension.height);
        }
        else {
          g.setColor(new Color(130, 146, 185));
          g.fillRect(0, 0, dimension.width, dimension.height);
        }
      } else if (state == ActionButtonComponent.POPPED) {
        ((Graphics2D)g).setPaint(new GradientPaint(0, 0, Gray._235, 0, dimension.height, Gray._200));
        g.fillRect(1, 1, dimension.width - 3, dimension.height - 3);
      }
    }
    if (state == ActionButtonComponent.PUSHED) {
      if (UIUtil.isUnderAquaLookAndFeel()) {
        ((Graphics2D)g).setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 40), dimension.width, dimension.height, new Color(0, 0, 0, 20)));
        g.fillRect(0, 0, dimension.width - 1, dimension.height - 1);
        
        g.setColor(new Color(0, 0, 0, 120));
        g.drawLine(0, 0, 0, dimension.height - 2);
        g.drawLine(1, 0, dimension.width - 2, 0);
        
        g.setColor(new Color(0, 0, 0, 30));
        g.drawRect(1, 1, dimension.width - 3, dimension.height - 3);
      }
      else {
        g.setColor(new Color(130, 146, 185));
        g.fillRect(0, 0, dimension.width, dimension.height);
      }
    }
  }

  public void paintBorder(Graphics g, JComponent component, int state) {
    if (state == ActionButtonComponent.NORMAL) return;
    Rectangle r = new Rectangle(component.getWidth(), component.getHeight());

    if (UIUtil.isUnderAquaLookAndFeel()) {
      if (state == ActionButtonComponent.POPPED) {
        g.setColor(new Color(0, 0, 0, 30));
        g.drawRoundRect(r.x, r.y, r.width - 2, r.height - 2, 4, 4);
      }
    } else {
      Color color = new Color(8, 36, 107);
      g.setColor(color);
      UIUtil.drawLine(g, r.x, r.y, r.x, (r.y + r.height) - 1);
      UIUtil.drawLine(g, r.x, r.y, (r.x + r.width) - 1, r.y);
      UIUtil.drawLine(g, (r.x + r.width) - 1, r.y, (r.x + r.width) - 1,
                      (r.y + r.height) - 1);
      UIUtil.drawLine(g, r.x, (r.y + r.height) - 1, (r.x + r.width) - 1,
                      (r.y + r.height) - 1);
    }
  }

  public void paintIcon(Graphics g, ActionButtonComponent actionButton, Icon icon) {
    int width = icon.getIconWidth();
    int height = icon.getIconHeight();
    int x = (int)Math.ceil((actionButton.getWidth() - width) / 2);
    int y = (int)Math.ceil((actionButton.getHeight() - height) / 2);
    paintIconAt(g, actionButton, icon, x, y);
  }

  public void paintIconAt(Graphics g, ActionButtonComponent button, Icon icon, int x, int y) {
    if (button.getPopState() == ActionButtonComponent.PUSHED && !UIUtil.isUnderAquaLookAndFeel()) {
      x++;
      y++;
    }
    icon.paintIcon(null, g, x, y);
  }
}
