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
package com.intellij.designer.model;

import com.intellij.designer.designSurface.ComponentDecorator;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class RadLayout {
  public abstract ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection);

  @Nullable
  public EditOperation processChildOperation(OperationContext context) {
    return null;
  }

  public void addSelectionActions(DefaultActionGroup actionGroup, JComponent shortcuts, List<RadComponent> selection) {
  }
}