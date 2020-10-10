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
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.Language;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.xml.breadcrumbs.BreadcrumbsInfoProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 22, 2007
 */
public class AntBreadcrumbsInfoProvider extends BreadcrumbsInfoProvider {
  private static final Language[] ourSupportedLanguages = new Language[] {AntSupport.getLanguage()};

  public Language[] getLanguages() {
    return ourSupportedLanguages;
  }

  public boolean acceptElement(@NotNull final PsiElement e) {
    return e instanceof AntStructuredElement && e.isValid();
  }

  @NotNull
  public String getElementInfo(@NotNull final PsiElement e) {
    AntStructuredElement se = (AntStructuredElement)e;
    final AntTypeDefinition typeDef = se.getTypeDefinition();
    if (typeDef == null) {
      return se.getSourceElement().getName();
    }
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      final AntTypeId typeId = typeDef.getTypeId();
      final String nsPrefix = typeId.getNamespacePrefix();
      if (nsPrefix != null) {
        builder.append(nsPrefix);
        builder.append(":");
      }
      
      builder.append(typeId.getName());
      
      final String name = se.getName();
      if (name != null) {
        builder.append(" ");
        builder.append(name);
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @Nullable
  public String getElementTooltip(@NotNull final PsiElement e) {
    final XmlTag tag = ((AntStructuredElement)e).getSourceElement();
    final StringBuilder result = StringBuilderSpinAllocator.alloc();
    try {
      result.append("<");
      result.append(tag.getName());
      
      for (final XmlAttribute attrib : tag.getAttributes()) {
        result.append(" ").append(attrib.getText());
      }
      if (tag.isEmpty()) {
        result.append("/>");
      }
      else {
        result.append(">...</");
        result.append(tag.getName());
        result.append(">");
      }
      return result.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(result);
    }
  }
}
