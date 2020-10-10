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
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.TemplateLookupSelectionHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.Set;

public class JavaTemplateUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.impl.JavaTemplateUtil");

  private JavaTemplateUtil() {
  }

  public static void updateTypeBindings(Object item, PsiFile file, final Document document, final int segmentStart, final int segmentEnd) {
    final Project project = file.getProject();
    PsiClass aClass = null;
    if (item instanceof PsiClass) {
      aClass = (PsiClass)item;
    }
    else if (item instanceof PsiType) {
      aClass = PsiUtil.resolveClassInType((PsiType)item);
    }

    if (aClass != null) {
      if (aClass instanceof PsiTypeParameter) {
        if (((PsiTypeParameter)aClass).getOwner() instanceof PsiMethod) {
          PsiElement element = file.findElementAt(segmentStart);
          PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
          if (method != null) {
            PsiTypeParameterList paramList = method.getTypeParameterList();
            PsiTypeParameter[] params = paramList.getTypeParameters();
            for (PsiTypeParameter param : params) {
              if (param.getName().equals(aClass.getName())) return;
            }
            try {
              paramList.add(aClass.copy());
              PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }  else {
        addImportForClass(document, aClass, segmentStart, segmentEnd);
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
      }
    }
  }

  public static void addImportForClass(final Document document, final PsiClass aClass, final int start, final int end) {
    final Project project = aClass.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    if (!aClass.isValid() || aClass.getQualifiedName() == null) return;

    JavaPsiFacade manager = JavaPsiFacade.getInstance(project);
    PsiResolveHelper helper = manager.getResolveHelper();

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    CharSequence chars = document.getCharsSequence();

    PsiElement element = file.findElementAt(start);
    String refText = chars.subSequence(start, end).toString();
    PsiClass refClass = helper.resolveReferencedClass(refText, element);
    if (aClass.equals(refClass)) return;

    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      while (parent != null) {
        PsiElement tmp = parent.getParent();
        if (!(tmp instanceof PsiJavaCodeReferenceElement) || tmp.getTextRange().getEndOffset() > end) break;
        parent = tmp;
      }
      if (parent instanceof PsiJavaCodeReferenceElement && !((PsiJavaCodeReferenceElement) parent).isQualified()) {
        final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement) parent;
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              ref.bindToElement(aClass);
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }
  }

  public static LookupElement addElementLookupItem(Set<LookupElement> items, PsiElement element) {
    final LookupElement item = LookupItemUtil.addLookupItem(items, element);
    if (item instanceof LookupItem) {
      ((LookupItem)item).setAttribute(TemplateLookupSelectionHandler.KEY_IN_LOOKUP_ITEM, new JavaTemplateLookupSelectionHandler());
    }
    return item;
  }

  public static LookupElement addTypeLookupItem(Set<LookupElement> items, PsiType type) {
    final LookupElement item = PsiTypeLookupItem.createLookupItem(type, null);
    items.add(item);
    if (item instanceof LookupItem) {
      ((LookupItem)item).setAttribute(TemplateLookupSelectionHandler.KEY_IN_LOOKUP_ITEM, new JavaTemplateLookupSelectionHandler());
    }
    return item;
  }
}
