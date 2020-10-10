package org.jetbrains.plugins.gradle.ui;

import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;

import javax.swing.*;
import java.awt.*;

/**
 * Flow layout calculates necessary size assuming that all components are laid out within a single row.
 * That may cause troubles during initial space calculation, i.e. panel with flow layout is represented as a
 * single wide row inside a scroll pane. This panel fixes that by calculating necessary size on its own.
 * It fixes the width to use as a minimum between a width offered by default flow layout and customizable value
 * and calculates the height. If the user has an ability to manually change panel size (e.g. indirectly via changing
 * size of the dialog that serves as a container for the panel), that width is used as a maximum width.
 *
 * @author: Denis Zhdanov
 * @since: Jul 28, 2008
 */
public class MultiRowFlowPanel extends JPanel {

  private final int maximumWidth = Toolkit.getDefaultToolkit().getScreenSize().width / 2;

  public MultiRowFlowPanel(@JdkConstants.FlowLayoutAlignment int align, int hGap, int vGap) {
    super(new FlowLayout(align, getGapToUse(hGap), getGapToUse(vGap)));
  }

  private static int getGapToUse(int gap) {
    // There is a problem with flow layout controls paint under Alloy LAF - it looks like it doesn't take given gaps into consideration.
    // Alloy LAF sources are closed, so we use this dirty hack here.
    return UIUtil.isUnderAlloyLookAndFeel() ? gap - 4 : gap;
  }
  
  /**
   * Calculates a preferred size assuming that the maximum row width is a value returned from
   * {@link #getMaxRowWidth()}.
   */
  @Override
  public Dimension getPreferredSize() {
    return calculateSize(getMaxRowWidth());
  }

  /**
   * Calculates a preferred size assuming that the maximum row width is a value returned from
   * {@link #getMaxRowWidth()}.
   */
  @Override
  public Dimension getMinimumSize() {
    return calculateSize(getMaxRowWidth());
  }

  /**
   * @return      current representation width if the component is already showed; minimum of default preferred
   *              width (when all components are layed in a single row) and half screen width
   */
  private int getMaxRowWidth() {
    int result = getSize().width;
    if (result == 0) {
      result = Math.min(super.getPreferredSize().width, maximumWidth);
    }
    return result;
  }

  /**
   * Iterates all child components and calculates the space enough to keep all of them assuming that the width is
   * fixed.
   */
  private Dimension calculateSize(int maxRowWidth) {
    FlowLayout layout = (FlowLayout)getLayout();
    int height = 0;
    int currentRowWidth = 0;
    int currentRowHeight = 0;
    for (int i = 0, count = getComponentCount(); i < count; ++i) {
      Component comp = getComponent(i);
      Dimension bounds = comp.getPreferredSize();
      if (!comp.isVisible()) {
        continue;
      }
      currentRowHeight = Math.max(currentRowHeight, bounds.height);
      if (currentRowWidth + layout.getHgap() + bounds.width <= maxRowWidth) {
        if (bounds.width != 0) {
          currentRowWidth += bounds.width + layout.getHgap();
        }
        continue;
      }
      height += currentRowHeight + layout.getVgap();
      currentRowWidth = bounds.width;
      currentRowHeight = bounds.height;
    }
    return new Dimension(maxRowWidth, height + currentRowHeight + 2 * layout.getVgap());
  }
}