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
package com.intellij.lang.ant.quickfix;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.misc.AntPsiUtil;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.AntFileImpl;
import com.intellij.lang.ant.psi.impl.reference.AntTargetReference;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

public class AntCreateTargetFix extends BaseIntentionAction {

  private final AntTargetReference myRef;
  private final AntFile myFile;

  public AntCreateTargetFix(final AntTargetReference ref) {
    this(ref, null);
  }

  public AntCreateTargetFix(final AntTargetReference ref, final AntFile file) {
    myRef = ref;
    myFile = file;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @NotNull
  public String getFamilyName() {
    final String i18nName = AntBundle.message("intention.create.target.family.name");
    return (i18nName == null) ? "Create target" : i18nName;
  }

  @NotNull
  public String getText() {
    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(getFamilyName());
      builder.append(" '");
      builder.append(myRef.getCanonicalRepresentationText());
      builder.append('\'');
      if (myFile != null) {
        builder.append(' ');
        builder.append(AntBundle.message("text.in.the.file", myFile.getName()));
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final AntElement element = myRef.getElement();
    final AntProject antProject = (myFile == null) ? element.getAntProject() : myFile.getAntProject();
    final AntElement anchor =
      (myFile == null) ? AntPsiUtil.getSubProjectElement(element) : PsiTreeUtil.getChildOfType(antProject, AntStructuredElement.class);
    final XmlTag projectTag = antProject.getSourceElement();
    XmlTag targetTag = projectTag.createChildTag(AntFileImpl.TARGET_TAG, projectTag.getNamespace(), null, false);
    targetTag.setAttribute(AntFileImpl.NAME_ATTR, myRef.getCanonicalRepresentationText());
    targetTag = (XmlTag)((anchor == null) ? projectTag.add(targetTag) : projectTag.addBefore(targetTag, anchor.getSourceElement()));
    ((Navigatable)targetTag).navigate(true);
  }
}
