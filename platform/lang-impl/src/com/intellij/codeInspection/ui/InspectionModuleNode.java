package com.intellij.codeInspection.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;

import javax.swing.*;

/**
 * User: anna
 * Date: 09-Jan-2006
 */
public class InspectionModuleNode extends InspectionTreeNode{
  private final Module myModule;
  public InspectionModuleNode(final Module module) {
    super(module);
    myModule = module;
  }

  public Icon getIcon(boolean expanded) {
    return ModuleType.get(myModule).getNodeIcon(expanded);
  }

  public String getName(){
    return myModule.getName();
  }

  public String toString() {
    return getName();
  }
}
