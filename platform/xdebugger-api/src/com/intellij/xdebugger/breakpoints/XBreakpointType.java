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

package com.intellij.xdebugger.breakpoints;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.ui.DebuggerIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Implement this class to support new type of breakpoints. An implementation should be registered in a plugin.xml:
 * <p>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;xdebugger.breakpointType implementation="qualified-class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p><p>
 *
 * Use this class only for breakpoints like exception breakpoints in Java. If a breakpoint will be put on some line in a file use
 * {@link XLineBreakpointType} instead 
 *
 * @author nik
 */
public abstract class XBreakpointType<B extends XBreakpoint<P>, P extends XBreakpointProperties> {
  public static final ExtensionPointName<XBreakpointType> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.xdebugger.breakpointType");
  private final @NonNls @NotNull String myId;
  private final @Nls @NotNull String myTitle;
  private final boolean mySuspendThreadSupported;

  /**
   * @param id an unique id of breakpoint type
   * @param title title of tab in the breakpoints dialog
   */
  protected XBreakpointType(@NonNls @NotNull final String id, @Nls @NotNull final String title) {
    this(id, title, false);
  }

  /**
   * @param id an unique id of breakpoint type
   * @param title title of tab in the breakpoints dialog
   * @param suspendThreadSupported <code>true</code> if suspending only one thread is supported for this type of breakpoints
   */
  protected XBreakpointType(@NonNls @NotNull final String id, @Nls @NotNull final String title, boolean suspendThreadSupported) {
    myId = id;
    myTitle = title;
    mySuspendThreadSupported = suspendThreadSupported;
  }

  @Nullable
  public P createProperties() {
    return null;
  }

  public final boolean isSuspendThreadSupported() {
    return mySuspendThreadSupported;
  }

  @NotNull
  public final String getId() {
    return myId;
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }

  @NotNull 
  public Icon getEnabledIcon() {
    return DebuggerIcons.ENABLED_BREAKPOINT_ICON;
  }

  @NotNull
  public Icon getDisabledIcon() {
    return DebuggerIcons.DISABLED_BREAKPOINT_ICON;
  }

  @NotNull
  public Icon getDisabledDependentIcon() {
    return DebuggerIcons.DISABLED_DEPENDENT_BREAKPOINT_ICON;
  }

  public abstract String getDisplayText(B breakpoint);

  @Nullable 
  public XBreakpointCustomPropertiesPanel<B> createCustomConditionsPanel() {
    return null;
  }

  @Nullable
  public XBreakpointCustomPropertiesPanel<B> createCustomPropertiesPanel() {
    return null;
  }

  /**
   * @deprecated override {@link #getEditorsProvider(B, com.intellij.openapi.project.Project)} instead
   */
  @Nullable
  public XDebuggerEditorsProvider getEditorsProvider() {
    return null;
  }

  @Nullable
  public XDebuggerEditorsProvider getEditorsProvider(@NotNull B breakpoint, @NotNull Project project) {
    return getEditorsProvider();
  }

  public List<XBreakpointGroupingRule<B, ?>> getGroupingRules() {
    return Collections.emptyList();
  }

  @NotNull 
  public Comparator<B> getBreakpointComparator() {
    return XDebuggerUtil.getInstance().getDefaultBreakpointComparator(this);
  }

  /**
   * Return <code>true</code> from this method in order to allow adding breakpoints from the "Breakpoints" dialog. Also override
   * {@link XBreakpointType#addBreakpoint(com.intellij.openapi.project.Project,javax.swing.JComponent)} method.
   * @return <code>true</code> if "Add" button should be visible in "Breakpoints" dialog
   */
  public boolean isAddBreakpointButtonVisible() {
    return false;
  }

  /**
   * This method is called then "Add" button is pressed in the "Breakpoints" dialog 
   * @param project
   * @param parentComponent
   * @return the created breakpoint or <code>null</code> if breakpoint wasn't created
   */
  @Nullable
  public B addBreakpoint(final Project project, JComponent parentComponent) {
    return null;
  }

  /**
   * Returns properties of the default breakpoint. The default breakpoints cannot be deleted and is always shown on top of the breakpoints
   * list in the dialog.
   *
   * @return a default breakpoint or {@code null} if default breakpoint isn't supported
   */
  @Nullable
  public XBreakpoint<P> createDefaultBreakpoint(@NotNull XBreakpointCreator<P> creator) {
    return null;
  }

  public boolean shouldShowInBreakpointsDialog(@NotNull Project project) {
    return true;
  }

  @Nullable @NonNls
  public String getBreakpointsDialogHelpTopic() {
    return null;
  }

  /**
   * Override this method to define source position for a breakpoint. It will be used e.g. by 'Go To' and 'View Source' buttons in 'Breakpoints' dialog
   */
  @Nullable
  public XSourcePosition getSourcePosition(@NotNull XBreakpoint<P> breakpoint) {
    return null;
  }

  public interface XBreakpointCreator<P extends XBreakpointProperties> {
    @NotNull
    XBreakpoint<P> createBreakpoint(@Nullable P properties);
  }
}