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
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * @author max
 */
public class DebuggerColorsPage implements ColorSettingsPage, DisplayPrioritySortable {
  @NotNull
  public String getDisplayName() {
    return XDebuggerBundle.message("xdebugger.colors.page.name");
  }

  @Nullable
  public Icon getIcon() {
    return IconLoader.getIcon("/actions/startDebugger.png");
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return new AttributesDescriptor[] {
      new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.breakpoint.line"), DebuggerColors.BREAKPOINT_ATTRIBUTES),
      new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.execution.point"), DebuggerColors.EXECUTIONPOINT_ATTRIBUTES),
    };
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return new ColorDescriptor[] {
      new ColorDescriptor(OptionsBundle.message("options.java.attribute.descriptor.recursive.call"), DebuggerColors.RECURSIVE_CALL_ATTRIBUTES, ColorDescriptor.Kind.BACKGROUND)
    };
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  @NonNls
  @NotNull
  public String getDemoText() {
    return " ";
  }

  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.COMMON_SETTINGS;
  }
}
