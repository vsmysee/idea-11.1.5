/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.navigation;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: 3/29/11
 * Time: 4:21 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlGotoRelatedProvider extends GotoRelatedProvider {
  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull PsiElement context) {
    final PsiFile file = context.getContainingFile();
    if (file == null || !isAvailable(file)) {
      return Collections.emptyList();
    }

    HashSet<PsiFile> resultSet = new HashSet<PsiFile>();
    fillRelatedFiles(file, resultSet);

    return GotoRelatedItem.createItems(resultSet);
  }

  private static boolean isAvailable(@NotNull PsiFile psiFile) {
    for (PsiFile file : psiFile.getViewProvider().getAllFiles()) {
      Language language = file.getLanguage();
      if (language.isKindOf(HTMLLanguage.INSTANCE) || language.isKindOf(XHTMLLanguage.INSTANCE)) {
        return true;
      }
    }
    return false;
  }

  private static void fillRelatedFiles(@NotNull PsiFile file, @NotNull Set<PsiFile> resultSet) {
    for (PsiFile psiFile : file.getViewProvider().getAllFiles()) {
      if (psiFile instanceof XmlFile) {
        final XmlFile xmlFile = (XmlFile)psiFile;

        for (RelatedToHtmlFilesContributor contributor : RelatedToHtmlFilesContributor.EP_NAME.getExtensions()) {
          contributor.fillRelatedFiles(xmlFile, resultSet);
        }
      }
    }
  }
}
