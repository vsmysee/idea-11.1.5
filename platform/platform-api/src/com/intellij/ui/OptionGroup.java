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
package com.intellij.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.border.IdeaTitledBorder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class creates a nicely formatted panel with components.  Useful for option panels.
 */
public class OptionGroup implements PanelWithAnchor {
  private String myTitle;
  private List myOptions;
  private List myIsShifted;
  private boolean boldTitle;
  private JComponent anchor;

  public OptionGroup(String title) {
    this(title, false);
  }

  public OptionGroup(String title, boolean boldTitle) {
    myTitle = title;
    myOptions = new ArrayList();
    myIsShifted = new ArrayList();
    this.boldTitle = boldTitle;
  }

  /**
   * Create panel without border
   */
  public OptionGroup() {
    this(null);
  }

  public void add(JComponent component) {
    add(component, false);
  }

  public void add(JComponent component, boolean indented) {
    myOptions.add(component);
    myIsShifted.add(Boolean.valueOf(indented));
  }

  public void add(JComponent leftComponent, JComponent rightComponent) {
    add(leftComponent, rightComponent, false);
  }

  public void add(JComponent leftComponent, JComponent rightComponent, boolean indented) {
    myOptions.add(new Pair(leftComponent, rightComponent));
    myIsShifted.add(Boolean.valueOf(indented));
  }

  public JPanel createPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    for (int i = 0; i < myOptions.size(); i++) {
      int leftInset = Boolean.TRUE.equals(myIsShifted.get(i)) ? 15 : 5;
      Object option = myOptions.get(i);
      if (option instanceof JComponent) {
        JComponent component = (JComponent)option;
        int verticalInset = component instanceof JLabel || component instanceof JTextField ? 2 : 0;
        panel.add(component,
                  new GridBagConstraints(0, i, GridBagConstraints.REMAINDER, 1, 1, 0, GridBagConstraints.WEST, getFill(component),
                                         new Insets(verticalInset, leftInset, verticalInset, 5), 0, 0));
      }
      else {
        Pair pair = (Pair)option;
        JComponent firstComponent = (JComponent)pair.first;
        int verticalInset = firstComponent instanceof JLabel || firstComponent instanceof JTextField ? 1 : 0;
        panel.add(firstComponent,
                  new GridBagConstraints(0, i, 1, 1, 1, 0, GridBagConstraints.WEST, getFill(firstComponent),
                                         new Insets(verticalInset, leftInset, verticalInset, 5), 0, 0));
        JComponent secondComponent = (JComponent)pair.second;
        verticalInset = secondComponent instanceof JLabel || secondComponent instanceof JTextField ? 2 : 0;
        panel.add(secondComponent,
                  new GridBagConstraints(1, i, 1, 1, 1, 0, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL,
                                         new Insets(verticalInset, 5, verticalInset, 5), 0, 0));
      }
    }
    JPanel p = new JPanel();
    p.setPreferredSize(new Dimension(0, 0));
    panel.add(p,
              new GridBagConstraints(0, myOptions.size(), GridBagConstraints.REMAINDER, 1, 0, 1,
                                     GridBagConstraints.NORTH, GridBagConstraints.NONE,
                                     new Insets(0, 0, 0, 0), 0, 0));

    if (myTitle != null) {
      IdeaTitledBorder titledBorder = IdeBorderFactory.createTitledBorder(myTitle, true);
      panel.setBorder(titledBorder);
      titledBorder.acceptMinimumSize(panel);
    }

    return panel;
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    this.anchor = anchor;
    for (Object o : myOptions) {
      if (o instanceof Pair &&
          ((Pair)o).getFirst() instanceof AnchorableComponent) {
        ((AnchorableComponent)((Pair)o).getFirst()).setAnchor(anchor);
      }
    }
  }

  private static int getFill(JComponent component) {
    if (component instanceof JCheckBox) {
      return GridBagConstraints.NONE;
    }
    return GridBagConstraints.HORIZONTAL;
  }

  public JComponent[] getComponents() {
    ArrayList<JComponent> components = new ArrayList<JComponent>();
    for (Object o : myOptions) {
      if (o instanceof Pair) {
        components.add((JComponent)((Pair)o).first);
        components.add((JComponent)((Pair)o).second);
      }
      else {
        components.add((JComponent)o);
      }
    }
    return components.toArray(new JComponent[components.size()]);
  }

  @Nullable
  public JComponent findAnchor() {
    double maxWidth = -1;
    JComponent ans = null;
    for (Object o : myOptions) {
      if (o instanceof Pair &&
          ((Pair)o).getFirst() instanceof AnchorableComponent &&
          ((JComponent)((Pair)o).getFirst()).getPreferredSize().getWidth() > maxWidth) {
        maxWidth = ((JComponent)((Pair)o).getFirst()).getPreferredSize().getWidth();
        ans = (JComponent)((Pair)o).getFirst();
      }
    }
    return ans;
  }
}
