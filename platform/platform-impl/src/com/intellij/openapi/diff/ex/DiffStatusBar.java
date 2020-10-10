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
package com.intellij.openapi.diff.ex;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Yura Cangea
 */
public class DiffStatusBar extends JPanel {
  private final Collection<JComponent> myLabels = new ArrayList<JComponent>();

  private final JLabel myTextLabel = new JLabel("");
  private static final int COMP_HEIGHT = 30;
  private EditorColorsScheme myColorScheme = null;

  public <T extends LegendTypeDescriptor> DiffStatusBar(List<T> types) {
    for (T differenceType : types) {
      addDiffType(differenceType);
    }
    initGui();
  }

  private void addDiffType(final LegendTypeDescriptor diffType){
    addComponent(diffType);
  }

  private void addComponent(final LegendTypeDescriptor diffType) {
    JComponent component = new JPanel() {
      public void paint(Graphics g) {
        setBackground(UIUtil.getPanelBackground());
        super.paint(g);
        FontMetrics metrics = getFontMetrics(getFont());

        EditorColorsScheme colorScheme = myColorScheme != null
                                         ? myColorScheme
                                         : EditorColorsManager.getInstance().getGlobalScheme();
        g.setColor(diffType.getLegendColor(colorScheme));
        g.fill3DRect(10, (getHeight() - 10) / 2, 35, 10, true);

        Font font = g.getFont();
        if (font.getStyle() != Font.PLAIN) {
          font = font.deriveFont(Font.PLAIN);
        }
        g.setFont(font);
        g.setColor(UIUtil.getLabelForeground());
        int textBaseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(diffType.getDisplayName(), 67, textBaseline);
      }

      @Override
      public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        return new Dimension((int)(70 + metrics.getStringBounds(diffType.getDisplayName(), getGraphics()).getWidth()), COMP_HEIGHT);
      }

      @Override
      public Dimension getMinimumSize() {
        return getPreferredSize();
      }
    };
    myLabels.add(component);
  }

  public Dimension getMinimumSize() {
    Dimension p = super.getPreferredSize();
    Dimension m = super.getMinimumSize();
    return new Dimension(m.width, p.height);
  }

  public Dimension getMaximumSize() {
    Dimension p = super.getPreferredSize();
    Dimension m = super.getMaximumSize();
    return new Dimension(m.width, p.height);
  }

  public void setText(String text) {
    myTextLabel.setText(text);
  }

  private void initGui() {
    JComponent filler = new JComponent() {
      @Override
      public Dimension getPreferredSize() {
        return myTextLabel.getPreferredSize();
      }
    };
    setLayout(new BorderLayout());
    setBorder(BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(),
                                                 BorderFactory.createEmptyBorder(3, 20, 3, 20)));

    add(myTextLabel, BorderLayout.WEST);
    Box box = Box.createHorizontalBox();
    box.add(Box.createHorizontalGlue());
    JPanel panel = new JPanel(new GridLayout(1, myLabels.size(), 0, 0));
    for (final JComponent myLabel : myLabels) {
      panel.add(myLabel);
    }
    panel.setMaximumSize(panel.getPreferredSize());
    box.add(panel);
    box.add(Box.createHorizontalGlue());
    add(box, BorderLayout.CENTER);

    add(filler, BorderLayout.EAST);
  }

  public void setColorScheme(EditorColorsScheme colorScheme) {
    EditorColorsScheme oldScheme = myColorScheme;
    myColorScheme = colorScheme;
    if (oldScheme != colorScheme) repaint();
  }

  public interface LegendTypeDescriptor {
    String getDisplayName();
    @Nullable
    Color getLegendColor(EditorColorsScheme colorScheme);
  }
}
