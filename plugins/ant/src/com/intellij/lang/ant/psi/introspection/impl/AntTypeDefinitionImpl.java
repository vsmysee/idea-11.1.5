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
package com.intellij.lang.ant.psi.introspection.impl;

import com.intellij.lang.ant.misc.AntStringInterner;
import com.intellij.lang.ant.psi.impl.AntFileImpl;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AntTypeDefinitionImpl implements AntTypeDefinition {

  private static final AntTypeId ourJavadocId = new AntTypeId(AntFileImpl.JAVADOC_TAG);
  private static final AntTypeId ourUnzipId = new AntTypeId(AntFileImpl.UNZIP_TAG);

  private AntTypeId myTypeId;
  private final Set<String> myExtensionPoints;
  private String myClassName;
  private boolean myIsTask;
  private boolean myIsAllTaskContainer;
  private boolean myIsProperty;
  private PsiElement myDefiningElement;
  private boolean myIsOutdated = false;
  /**
   * Attribute names to their types.
   */
  private final Map<String, AntAttributeType> myAttributes;
  private String[] myAttributesArray;
  /**
   * Task ids to their class names.
   */
  private final Map<AntTypeId, String> myNestedClassNames;
  private AntTypeId[] myNestedElementsArray;

  public AntTypeDefinitionImpl(final AntTypeDefinitionImpl base) {
    this(base.getTypeId(), base.getClassName(), base.isTask(), base.isAllTaskContainer(), new HashMap<String, AntAttributeType>(base.myAttributes),
         new HashMap<AntTypeId, String>(base.myNestedClassNames));
  }

  public AntTypeDefinitionImpl(final AntTypeId id, final String className, final boolean isTask, final boolean isAllTaskContainer) {
    this(id, className, isTask, isAllTaskContainer, new HashMap<String, AntAttributeType>(), new HashMap<AntTypeId, String>());
  }

  public AntTypeDefinitionImpl(final AntTypeId id,
                               final String className,
                               final boolean isTask, final boolean isAllTaskContainer, @NonNls @NotNull final Map<String, AntAttributeType> attributes,
                               final Map<AntTypeId, String> nestedElements) {
    this(id, className, isTask, isAllTaskContainer, attributes, nestedElements, null);
  }

  public AntTypeDefinitionImpl(final AntTypeId id,
                               final String className,
                               final boolean isTask, final boolean allTaskContainer, @NonNls @NotNull final Map<String, AntAttributeType> attributes,
                               final Map<AntTypeId, String> nestedElements,
                               final PsiElement definingElement) {
    this(id, className, isTask, allTaskContainer, attributes, nestedElements, Collections.<String>emptySet(), definingElement);
  }
  
  public AntTypeDefinitionImpl(final AntTypeId id,
                               final String className,
                               final boolean isTask, final boolean isAllTaskContainer, @NonNls @NotNull final Map<String, AntAttributeType> attributes,
                               final Map<AntTypeId, String> nestedElements,
                               final Set<String> extensionPoints,
                               final PsiElement definingElement) {
    myTypeId = id;
    myExtensionPoints = extensionPoints;
    setClassName(className);
    myIsTask = isTask;
    myIsAllTaskContainer = isAllTaskContainer;
    attributes.put(AntFileImpl.ID_ATTR, AntAttributeType.STRING);
    myAttributes = attributes;
    myNestedClassNames = nestedElements;
    myDefiningElement = definingElement;
  }

  public boolean isOutdated() {
    return myIsOutdated;
  }

  public void setOutdated(final boolean isOutdated) {
    myIsOutdated = isOutdated;
  }

  public final AntTypeId getTypeId() {
    return myTypeId;
  }

  public final void setTypeId(final AntTypeId id) {
    myTypeId = id;
  }

  public final String getClassName() {
    return myClassName;
  }

  public final boolean isTask() {
    return myIsTask;
  }

  public boolean isAllTaskContainer() {
    return myIsAllTaskContainer;
  }

  public boolean isProperty() {
    return myIsProperty;
  }

  @NotNull
  public final String[] getAttributes() {
    synchronized (PsiLock.LOCK) {
      if (myAttributesArray == null || myAttributesArray.length != myAttributes.size()) {
        myAttributesArray = myAttributes.keySet().toArray(new String[myAttributes.size()]);
      }
      return myAttributesArray;
    }
  }

  public final AntAttributeType getAttributeType(final String attr) {
    for (int i = 0; i < attr.length(); ++i) {
      if (!Character.isLowerCase(attr.charAt(i))) {
        return myAttributes.get(attr.toLowerCase(Locale.US));
      }
    }
    return myAttributes.get(attr);
  }

  public final Map<String, AntAttributeType> getAttributesMap() {
    return myAttributes;
  }

  @SuppressWarnings({"unchecked"})
  public final AntTypeId[] getNestedElements() {
    synchronized (PsiLock.LOCK) {
      if (myNestedElementsArray == null || myNestedElementsArray.length != myNestedClassNames.size()) {
        myNestedElementsArray = myNestedClassNames.keySet().toArray(new AntTypeId[myNestedClassNames.size()]);
      }
      return myNestedElementsArray;
    }
  }

  public final Map<AntTypeId, String> getNestedElementsMap() {
    return myNestedClassNames;
  }

  public boolean isExtensionPointType(final ClassLoader loader, final String className) {
    if ("java.lang.Object".equals(className)) {
      return false;
    }
    if (myExtensionPoints.contains(className)) {
      return true;
    }
    try {
      return isExtensionPointType(loader.loadClass(className));
    }
    catch (ClassNotFoundException e) {
      return false;
    }
  }

  private boolean isExtensionPointType(final Class<?> aClass) {
     if (aClass == null || "java.lang.Object".equals(aClass.getName())) {
       return false;
     }
     if (myExtensionPoints.contains(aClass.getName())) {
       return true;
     }
     final Class[] interfaces = aClass.getInterfaces();
     for (Class iface : interfaces) {
       if (isExtensionPointType(iface)) {
         return true;
       }
     }
     return isExtensionPointType(aClass.getSuperclass());
   }
  
  
  @Nullable
  public final String getNestedClassName(final AntTypeId id) {
    final String nsPrefix = id.getNamespacePrefix();
    if (nsPrefix == null) {
      final String name = id.getName();
      /**
       * Hardcode for <javadoc> task (IDEADEV-6731).
       */
      if (name.equals(AntFileImpl.JAVADOC2_TAG)) {
        return myNestedClassNames.get(ourJavadocId);
      }
      /**
       * Hardcode for <unwar> and <unjar> tasks (IDEADEV-6830).
       */
      if (name.equals(AntFileImpl.UNWAR_TAG) || name.equals(AntFileImpl.UNJAR_TAG)) {
        return myNestedClassNames.get(ourUnzipId);
      }
    }
    return myNestedClassNames.get(id);
  }

  public final void registerNestedType(final AntTypeId id, String taskClassName) {
    myNestedClassNames.put(id, taskClassName);
  }

  public final void unregisterNestedType(final AntTypeId typeId) {
    myNestedClassNames.remove(typeId);
  }

  public final PsiElement getDefiningElement() {
    return myDefiningElement;
  }

  public final void setDefiningElement(final PsiElement element) {
    myDefiningElement = element;
  }

  public final void setIsTask(final boolean isTask) {
    myIsTask = isTask;
  }
  
  public final void setIsProperty(final boolean isProperty) {
    myIsProperty = isProperty;
  }
  
  public final void setClassName(final String className) {
    myClassName = AntStringInterner.intern(className);
  }
}
