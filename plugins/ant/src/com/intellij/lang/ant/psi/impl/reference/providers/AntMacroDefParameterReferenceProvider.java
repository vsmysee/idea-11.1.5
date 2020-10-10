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

import com.intellij.lang.ant.misc.PsiReferenceListSpinAllocator;
import com.intellij.lang.ant.psi.AntAllTasksContainer;
import com.intellij.lang.ant.psi.AntMacroDef;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.reference.AntMacroDefParameterReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AntMacroDefParameterReferenceProvider extends PsiReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    if (!(element instanceof AntStructuredElement)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final AntStructuredElement antElement = (AntStructuredElement)element;
    final AntAllTasksContainer sequential = PsiTreeUtil.getParentOfType(antElement, AntAllTasksContainer.class, true);
    if (sequential == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final AntMacroDef macrodef = PsiTreeUtil.getParentOfType(sequential, AntMacroDef.class, true);
    if (macrodef == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final List<PsiReference> refs = PsiReferenceListSpinAllocator.alloc();
    try {
      for (XmlAttribute attr : antElement.getSourceElement().getAttributes()) {
        getXmlElementReferences(attr.getValueElement(), refs, antElement);
      }
      getXmlElementReferences(antElement.getSourceElement(), refs, antElement);
      return (refs.size() > 0) ? refs.toArray(new PsiReference[refs.size()]) : PsiReference.EMPTY_ARRAY;
    }
    finally {
      PsiReferenceListSpinAllocator.dispose(refs);
    }
  }

  private static void getXmlElementReferences(final XmlElement element, final List<PsiReference> refs, final AntStructuredElement antElement) {
    if (element == null) return;
    final String text = ElementManipulators.getValueText(element);
    final int offsetInPosition = ElementManipulators.getValueTextRange(element).getStartOffset() + element.getTextRange().getStartOffset() - antElement.getTextRange().getStartOffset();
    int startIndex;
    int endIndex = -1;
    while ((startIndex = text.indexOf("@{", endIndex + 1)) > endIndex) {
      startIndex += 2;
      endIndex = startIndex;
      int nestedBrackets = 0;
      while (text.length() > endIndex) {
        final char ch = text.charAt(endIndex);
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
      if(nestedBrackets > 0 || endIndex == text.length()) return;
      if (endIndex >= startIndex) {
        final String name = text.substring(startIndex, endIndex);
        refs.add(new AntMacroDefParameterReference(antElement, name,
                                                   new TextRange(offsetInPosition + startIndex, offsetInPosition + endIndex), element));
      }
      endIndex = startIndex; 
    }
  }

}
