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
package com.intellij.lang.ant.psi.usages;

import com.intellij.lang.ant.HelpID;
import com.intellij.lang.ant.PsiAntElement;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntUsagesProvider implements FindUsagesProvider {

  @Nullable
  public WordsScanner getWordsScanner() {
    return null;
  }

  public boolean canFindUsagesFor(@NotNull PsiElement element) {
    if (!(element instanceof AntStructuredElement)) return false;
    AntStructuredElement se = (AntStructuredElement)element;
    return se.hasNameElement() || se.hasIdElement();
  }

  @Nullable
  public String getHelpId(@NotNull PsiElement element) {
    return HelpID.FIND_OTHER_USAGES;
  }

  @NotNull
  public String getType(@NotNull PsiElement element) {
    return ((PsiAntElement) element).getRole().getName();
  }

  @NotNull
  public String getDescriptiveName(@NotNull PsiElement element) {
    if( element instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)element).getName();
      if (name != null) {
        return name;
      }
    }
    final XmlElement sourceElement = ((AntElement)element).getSourceElement();
    if (sourceElement instanceof XmlTag) {
      return ((XmlTag)sourceElement).getName();
    }
    return sourceElement.getText();
  }

  @NotNull
  public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }
}
