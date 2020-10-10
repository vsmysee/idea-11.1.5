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

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntElementVisitor;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.psi.PsiLock;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AntTargetImpl extends AntStructuredElementImpl implements AntTarget {

  private AntTarget[] myDependsTargets;

  public AntTargetImpl(AntElement parent, final XmlTag tag) {
    super(parent, tag);
    myDefinition = getAntFile().getTargetDefinition();
  }

  @NonNls
  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntTarget:[");
      builder.append(getName());
      if (getDescription() != null) {
        builder.append(" :");
        builder.append(getDescription());
      }
      builder.append("]");
      final AntTarget[] targets = getDependsTargets();
      if (targets.length > 0) {
        builder.append(" -> [");
        for (AntTarget target : targets) {
          builder.append(' ');
          builder.append(target.getName());
        }
        builder.append(" ]");
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntElementRole getRole() {
    return AntElementRole.TARGET_ROLE;
  }

  public void acceptAntElementVisitor(@NotNull final AntElementVisitor visitor) {
    visitor.visitAntTarget(this);
  }

  @NotNull
  public String getQualifiedName() {
    final AntProject project = getAntProject();
    final String projectName = (project != null) ? project.getName() : null;
    final String name = getName();
    final String result = (projectName == null || projectName.length() == 0) ? name : projectName + '.' + name;
    return (result == null) ? "" : result;
  }

  @Nullable
  public String getDescription() {
    return getSourceElement().getAttributeValue(AntFileImpl.DESCRIPTION_ATTR);
  }

  @Nullable
  public String getConditionalPropertyName(final ConditionalAttribute attrib) {
    final XmlAttribute propNameAttribute = getSourceElement().getAttribute(attrib.getXmlName(), null);
    if (propNameAttribute != null) {
      final XmlAttributeValue valueElement = propNameAttribute.getValueElement();
      if (valueElement != null) {
        return computeAttributeValue(valueElement.getValue());
      }
    }
    return null;
  }

  @NotNull
  public AntTarget[] getDependsTargets() {
    synchronized (PsiLock.LOCK) {
      if (myDependsTargets == null) {
        final String depends = getSourceElement().getAttributeValue(AntFileImpl.DEPENDS_ATTR);
        if (depends == null || depends.length() == 0) {
          myDependsTargets = AntTarget.EMPTY_ARRAY;
        }
        else {
          final AntProject project = getAntProject();
          final List<AntTarget> targets = new ArrayList<AntTarget>();
          for (final String name : depends.split(",|[\\s]+")) {
            final AntTarget antTarget = project.getTarget(name);
            if (antTarget != null) {
              targets.add(antTarget);
            }
          }
          myDependsTargets = targets.toArray(new AntTarget[targets.size()]);
        }
      }
      return myDependsTargets;
    }
  }

  public void setDependsTargets(@NotNull AntTarget[] targets) {
    synchronized (PsiLock.LOCK) {
      myDependsTargets = targets;
    }
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      super.clearCaches();
      myDependsTargets = null;
    }
  }

}
