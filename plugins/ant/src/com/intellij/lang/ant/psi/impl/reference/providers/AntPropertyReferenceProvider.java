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
package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.misc.PsiReferenceListSpinAllocator;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.AntFileImpl;
import com.intellij.lang.ant.psi.impl.reference.AntPropertyReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AntPropertyReferenceProvider extends PsiReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    final AntStructuredElement antElement = (AntStructuredElement)element;
    final XmlTag sourceElement = antElement.getSourceElement();
    final XmlAttribute[] attributes = sourceElement.getAttributes();
    if (attributes.length > 0) {
      final List<PsiReference> refs = PsiReferenceListSpinAllocator.alloc();
      try {
        //final boolean isTarget = antElement instanceof AntTarget;
        final boolean isSet = "isset".equals(sourceElement.getName());
        for (final XmlAttribute attr : attributes) {
          @NonNls final String attName = attr.getName();
          if (/*isTarget && */(AntFileImpl.IF_ATTR.equals(attName) || AntFileImpl.UNLESS_ATTR.equals(attName))) {
            getAttributeReference(antElement, attr, refs);
          }
          else if (isSet && AntFileImpl.PROPERTY.equals(attName)) {
            getAttributeReference(antElement, attr, refs);
          }
          else {
            getAttributeReferences(antElement, attr, refs);
          }
        }
        if (refs.size() > 0) {
          return refs.toArray(new PsiReference[refs.size()]);
        }
      }
      finally {
        PsiReferenceListSpinAllocator.dispose(refs);
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  /**
   * Gets all references to the ${} properties.
   *
   * @param element
   * @param attr
   * @param refs
   */
  private static void getAttributeReferences(final AntElement element, final XmlAttribute attr, final List<PsiReference> refs) {
    final AntFile antFile = element.getAntFile();
    final String value = attr.getValue();
    final XmlAttributeValue xmlAttributeValue = attr.getValueElement();
    if (xmlAttributeValue != null && value.indexOf("@{") < 0) {
      final int offsetInPosition = xmlAttributeValue.getTextRange().getStartOffset() - element.getTextRange().getStartOffset() + 1;
      int startIndex;
      int endIndex = -1;
      while ((startIndex = value.indexOf("${", endIndex + 1)) > endIndex) {
        if (startIndex > 0 && value.charAt(startIndex - 1) == '$') {
          // the '$' is escaped
          endIndex = startIndex + 1;
          continue;
        }
        startIndex += 2;
        endIndex = startIndex;
        int nestedBrackets = 0;
        while (value.length() > endIndex) {
          final char ch = value.charAt(endIndex);
          if (ch == '}') {
            if (nestedBrackets == 0) {
              break;
            }
            --nestedBrackets;
          }
          else if (ch == '{') {
            ++nestedBrackets;
          }
          ++endIndex;
        }
        if (nestedBrackets > 0 || endIndex > value.length()) return;
        if (endIndex >= startIndex) {
          final String propName = value.substring(startIndex, endIndex);
          if (antFile.isEnvironmentProperty(propName) && antFile.getProperty(propName) == null) {
            continue;
          }
          refs.add(new AntPropertyReference(element, propName,
                                            new TextRange(offsetInPosition + startIndex, offsetInPosition + endIndex), attr));
        }
        endIndex = startIndex;
      }
    }
  }

  /**
   * Gets single reference on a property named as the attribute's and if the property is resolved.
   *
   * @param element
   * @param attr
   * @param refs
   */
  private static void getAttributeReference(final AntElement element, final XmlAttribute attr, final List<PsiReference> refs) {
    final AntFile antFile = element.getAntFile();
    final String value = attr.getValue();
    if (value == null) {
      return;
    }
    if (antFile.isEnvironmentProperty(value) && antFile.getProperty(value) == null) {
      return;
    }
    final XmlAttributeValue xmlAttributeValue = attr.getValueElement();
    if (xmlAttributeValue != null) {
      final int offsetInPosition = xmlAttributeValue.getTextRange().getStartOffset() - element.getTextRange().getStartOffset() + 1;
      refs.add(new AntPropertyReference(element, value, new TextRange(offsetInPosition, offsetInPosition + value.length()), attr) {
        public boolean shouldBeSkippedByAnnotator() {
          return getCanonicalText().length() > 0;
        }

        public String getUnresolvedMessagePattern() {
          return AntBundle.message("please.specify.a.property");
        }
      });
    }
  }

}
