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
package com.intellij.android.designer.componentTree;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.ui.ColoredTreeCellRenderer;

/**
 * @author Alexander Lobas
 */
public final class AndroidTreeDecorator extends TreeComponentDecorator {
  @Override
  public void decorate(RadComponent component, ColoredTreeCellRenderer renderer) {
    MetaModel metaModel = component.getMetaModel();

    StringBuffer fullTitle = new StringBuffer();
    String title1 = new String(metaModel.getPaletteItem().getTitle());
    fullTitle.append(title1.replaceAll("%tag%", ((RadViewComponent)component).getTag().getName()));
    renderer.append(fullTitle.toString());

    renderer.setIcon(metaModel.getIcon());
  }
}