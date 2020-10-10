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
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.StringSetSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class AntPropertyImpl extends AntTaskImpl implements AntProperty {

  private PsiElement myPropertiesFile;
  private static final Set<String> ourPropertyDefiningTags = new HashSet<String>(Arrays.asList(
    AntFileImpl.PROPERTY,
    "param",
    "condition",
    "input",
    "available"
  ));

  public AntPropertyImpl(final AntElement parent,
                         final XmlTag sourceElement,
                         final AntTypeDefinition definition,
                         @NonNls final String nameElementAttribute) {
    super(parent, sourceElement, definition, nameElementAttribute);
  }

  public AntPropertyImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    this(parent, sourceElement, definition, AntFileImpl.NAME_ATTR);
  }

  public PsiElement setName(@NotNull final String name) throws IncorrectOperationException {
    final AntProperty element = (AntProperty)super.setName(name);
    final AntFile antFile = getAntFile();
    antFile.invalidateProperties();
    return element;
  }

  public void acceptAntElementVisitor(@NotNull final AntElementVisitor visitor) {
    visitor.visitAntProperty(this);
  }

  public String toString() {
    final @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntProperty[");
      if (getName() != null) {
        builder.append(getName());
        builder.append(" = ");
        builder.append(getValue(null));
      }
      else {
        final String propFile = getFileName();
        if (propFile != null) {
          builder.append("file: ");
          builder.append(propFile);
        }
        else {
          builder.append(getSourceElement().getName());
        }
      }
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public String getName() {
    final XmlAttributeValue[] values = getTstampPropertyAttributeValues();
    return (values.length == 1) ? values[0].getValue() : super.getName();
  }

  public AntElementRole getRole() {
    return AntElementRole.PROPERTY_ROLE;
  }

  public boolean canRename() {
    return super.canRename() && (!isTstamp() || getTstampPropertyAttributeValues().length > 0);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  public String getValue(final String propName) {
    synchronized (PsiLock.LOCK) {
      final XmlTag se = getSourceElement();
      final String tagName = se.getName();
      if (ourPropertyDefiningTags.contains(tagName)) { // todo: support conditions separately
        String value = getPropertyValue();
        if (value == null && propName != null) {
          final PropertiesFile propertiesFile = getPropertiesFile();
          if (propertiesFile != null) {
            final IProperty fileProperty = propertiesFile.findPropertyByKey(cutPrefix(propName));
            if (fileProperty != null) {
              value = fileProperty.getValue();
            }
          }
        }
        return value;
      }
      else if ("dirname".equals(tagName)) {
        return getDirnameValue();
      }
      else if (isTstamp()) {
        return getTstampValue(propName);
      }
      return null;
    }
  }

  private String cutPrefix(@NotNull final String propName) {
    final String prefix = getPrefix();
    if (prefix != null && propName.startsWith(prefix) && prefix.length() < propName.length() && propName.charAt(prefix.length()) == '.') {
      return propName.substring(prefix.length() + 1);
    }
    return propName;
  }

  @Nullable
  public String getFileName() {
    return computeAttributeValue(getSourceElement().getAttributeValue(AntFileImpl.FILE_ATTR));
  }

  @Nullable
  public PropertiesFile getPropertiesFile() {
    synchronized (PsiLock.LOCK) {
      if (myPropertiesFile == null) {
        myPropertiesFile = AntElementImpl.ourNull;
        final String name = getFileName();
        if (name != null) {
          final PsiFile psiFile = findFileByName(name, null);
          if (psiFile instanceof PropertiesFile) {
            myPropertiesFile = psiFile;
          }
        }
      }
      return (myPropertiesFile == AntElementImpl.ourNull) ? null : (PropertiesFile)myPropertiesFile;
    }
  }

  @Nullable
  public String getPrefix() {
    return computeAttributeValue(getSourceElement().getAttributeValue(AntFileImpl.PREFIX_ATTR));
  }

  @Nullable
  public String getEnvironment() {
    return computeAttributeValue(getSourceElement().getAttributeValue("environment"));
  }

  @Nullable
  public String[] getNames() {
    if (isTstamp()) {
      return getTstampNames();
    }
    final PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile != null) {
      final List<IProperty> propList = propertiesFile.getProperties();
      final String prefix = getPrefix();
      final String[] names = ArrayUtil.newStringArray(propList.size());
      int idx = 0;
      for (final IProperty importedProp : propList) {
        names[idx++] = prefix != null? prefix + "." + importedProp.getName() : importedProp.getName();
      }
      return names;
    }
    
    final String name = getName();
    if (name != null) {
      if (getAntFile().isEnvironmentProperty(name)) {
        return getEnvironmentNames(name);
      }
      return new String[]{name};
    }
    return null;
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      super.clearCaches();
      final AntFile antFile = getAntFile();
      if (antFile != null) {
        antFile.clearCaches();
      }
      myPropertiesFile = null;
    }
  }

  public int getTextOffset() {
    final XmlAttributeValue[] values = getTstampPropertyAttributeValues();
    return (values.length == 1) ? values[0].getTextOffset() : super.getTextOffset();
  }

  /**
   * @return <format> element for the <tstamp> property
   * @param propName
   */
  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public AntElement getFormatElement(final String propName) {
    for (final AntElement child : getChildren()) {
      if (child instanceof AntStructuredElement) {
        final AntStructuredElement se = (AntStructuredElement)child;
        final XmlTag tag = se.getSourceElement();
        if (AntFileImpl.FORMAT_TAG.equals(tag.getName())) {
          if (propName.equals(tag.getAttributeValue(AntFileImpl.PROPERTY))) {
            return child;
          }
        }
      }
    }
    return this;
  }

  @Nullable
  private String getPropertyValue() {
    final XmlTag sourceElement = getSourceElement();
    final String tagName = sourceElement.getName();
    if ("available".equals(tagName)) {
      // check only 'file' tag because others could be examined only at runtime
      final String filePath = sourceElement.getAttributeValue("file");
      if (filePath != null) {
        final String _filePath = computeAttributeValue(filePath);
        if (_filePath != null) {
          if (!new File(_filePath).exists()) {
            return null;
          }
        }
      }
    }

    String value = sourceElement.getAttributeValue("value");
    if (value == null) {
      value = sourceElement.getAttributeValue("location");
      if (value == null) {
        value = sourceElement.getAttributeValue("defaultvalue");
      }
    }
    return value;/*computeAttributeValue(value);*/
  }

  @Nullable
  private String getDirnameValue() {
    final XmlTag sourceElement = getSourceElement();
    final String value = computeAttributeValue(sourceElement.getAttributeValue(AntFileImpl.FILE_ATTR));
    if (value != null) {
      return new File(value).getParent();
    }
    return value;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private String getTstampValue(final String propName) {
    final XmlTag se = getSourceElement();
    Date d = new Date();
    final XmlTag formatTag = se.findFirstSubTag(AntFileImpl.FORMAT_TAG);
    if (formatTag != null) {
      final String offsetStr = formatTag.getAttributeValue("offset");
      int offset;
      if (offsetStr != null) {
        try {
          offset = Integer.parseInt(offsetStr);
        }
        catch (NumberFormatException e) {
          offset = 0;
        }
        final String unitStr = formatTag.getAttributeValue("unit");
        int unit = 0;
        if (unitStr != null) {
          if ("millisecond".equals(unitStr)) {
            unit = Calendar.MILLISECOND;
          }
          else if ("second".equals(unitStr)) {
            unit = Calendar.SECOND;
          }
          else if ("minute".equals(unitStr)) {
            unit = Calendar.MINUTE;
          }
          else if ("hour".equals(unitStr)) {
            unit = Calendar.HOUR_OF_DAY;
          }
          else if ("day".equals(unitStr)) {
            unit = Calendar.DAY_OF_MONTH;
          }
          else if ("week".equals(unitStr)) {
            unit = Calendar.WEEK_OF_YEAR;
          }
          else if ("year".equals(unitStr)) {
            unit = Calendar.YEAR;
          }
        }
        if (offset != 0 && unit != 0) {
          final Calendar cal = Calendar.getInstance();
          cal.setTime(d);
          cal.add(unit, offset);
          d = cal.getTime();
        }
      }
    }
    final String _propName = propName != null? cutPrefix(propName) : null;
    if (_propName != null) {
      if (_propName.equals("DSTAMP")) {
        return new SimpleDateFormat("yyyyMMdd").format(d);
      }
      else if (_propName.equals("TSTAMP")) {
        return new SimpleDateFormat("HHmm").format(d);
      }
      else if (_propName.equals("TODAY")) {
        return new SimpleDateFormat("MMMM d yyyy", Locale.US).format(d);
      }
    }

    for (XmlAttributeValue value : getTstampPropertyAttributeValues()) {
      if (value != null && (_propName == null || _propName.equals(value.getValue()))) {
        if (formatTag != null) {
          final String pattern = formatTag.getAttributeValue(TSTAMP_PATTERN_ATTRIBUTE_NAME);
          try {
            final DateFormat format = (pattern != null) ? new SimpleDateFormat(pattern) : DateFormat.getTimeInstance();
            final String tz = formatTag.getAttributeValue(TSTAMP_TIMEZONE_ATTRIBUTE_NAME);
            if (tz != null) {
              format.setTimeZone(TimeZone.getTimeZone(tz));
            }
            return format.format(d);
          }
          catch (IllegalArgumentException ignored) {
            return null;
          }
        }
      }
    }
    return null;
  }

  private XmlAttributeValue[] getTstampPropertyAttributeValues() {
    if (isTstamp()) {
      final List<XmlAttributeValue> elements = new ArrayList<XmlAttributeValue>();
      for (XmlTag formatTag : getSourceElement().findSubTags(AntFileImpl.FORMAT_TAG)) {
        final XmlAttribute propAttr = formatTag.getAttribute(AntFileImpl.PROPERTY, null);
        if (propAttr != null) {
          final XmlAttributeValue value = propAttr.getValueElement();
          if (value != null) {
            elements.add(value);
          }
        }
      }
      return elements.toArray(new XmlAttributeValue[elements.size()]);
    }
    return new XmlAttributeValue[0];
  }

  public boolean isTstamp() {
    return TSTAMP_TAG.equals(getSourceElement().getName());
  }

  private String[] getTstampNames() {
    @NonNls final Set<String> strings = StringSetSpinAllocator.alloc();
    try {
      String prefix = getSourceElement().getAttributeValue(AntFileImpl.PREFIX_ATTR);
      if (prefix == null) {
        strings.add("DSTAMP");
        strings.add("TSTAMP");
        strings.add("TODAY");
      }
      else {
        prefix += '.';
        strings.add(prefix + "DSTAMP");
        strings.add(prefix + "TSTAMP");
        strings.add(prefix + "TODAY");
      }
      for (XmlAttributeValue value : getTstampPropertyAttributeValues()) {
        if (value != null && value.getValue() != null) {
          final String additionalProperty = value.getValue();
          if (prefix == null) {
            strings.add(additionalProperty);
          }
          else {
            strings.add(prefix + additionalProperty);
          }
        }
      }
      return ArrayUtil.toStringArray(strings);
    }
    finally {
      StringSetSpinAllocator.dispose(strings);
    }
  }

  private String[] getEnvironmentNames(final String name) {
    @NonNls final Set<String> strings = StringSetSpinAllocator.alloc();
    try {
      final String sourceName = name.substring(AntFileImpl.DEFAULT_ENVIRONMENT_PREFIX.length());
      for (final String prefix : getAntFile().getEnvironmentPrefixes()) {
        strings.add(prefix + sourceName);
      }
      return ArrayUtil.toStringArray(strings);
    }
    finally {
      StringSetSpinAllocator.dispose(strings);
    }
  }
}
