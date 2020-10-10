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
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElementVisitor;
import com.intellij.lang.ant.psi.AntPresetDef;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.psi.PsiLock;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class AntPresetDefImpl extends AntAllTasksContainerImpl implements AntPresetDef {

  @NonNls public static final String ANT_PRESETDEF_NAME = "AntPresetDef";

  private AntTypeDefinitionImpl myPresetDefinition;

  public AntPresetDefImpl(final AntStructuredElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    invalidatePresetDefinition();
  }

  public String toString() {
    return createPresetDefClassName(getName());
  }

  public void acceptAntElementVisitor(@NotNull final AntElementVisitor visitor) {
    visitor.visitAntPresetDef(this);
  }

  public static String createPresetDefClassName(final String presetDefName) {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(ANT_PRESETDEF_NAME);
      builder.append("[");
      builder.append(presetDefName);
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntTypeDefinition getPresetDefinition() {
    return myPresetDefinition;
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      super.clearCaches();
      clearClassesCache();
      getAntFile().clearCaches();
    }
  }

  public void clearClassesCache() {
    synchronized (PsiLock.LOCK) {
      if (myPresetDefinition != null) {
        final AntStructuredElement parent = getAntProject();
        if (parent != null) {
          parent.unregisterCustomType(myPresetDefinition);
        }
        myPresetDefinition = null;
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void invalidatePresetDefinition() {
    myPresetDefinition = null;
    if (!hasNameElement()) return;
    final String thisClassName = createPresetDefClassName(getName());
    final AntStructuredElementImpl extented = PsiTreeUtil.getChildOfType(this, AntStructuredElementImpl.class);
    final AntTypeId typeId = new AntTypeId(getName());
    if (extented != null) {
      final AntTypeDefinitionImpl extentedDef = (AntTypeDefinitionImpl)extented.getTypeDefinition();
      if (extentedDef != null) {
        myPresetDefinition = new AntTypeDefinitionImpl(typeId, thisClassName, extentedDef.isTask(), false, extentedDef.getAttributesMap(),
                                                       extentedDef.getNestedElementsMap(), this);
      }
    }
    if (myPresetDefinition == null) {
      myPresetDefinition = new AntTypeDefinitionImpl(typeId, thisClassName, false, false, new HashMap<String, AntAttributeType>(),
                                                     new HashMap<AntTypeId, String>(), this);
    }
    final AntStructuredElement parent = getAntProject();
    if (parent != null) {
      parent.registerCustomType(myPresetDefinition);
    }
  }
}
