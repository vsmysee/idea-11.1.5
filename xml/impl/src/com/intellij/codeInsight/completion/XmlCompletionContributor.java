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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.InsertHandlerDecorator;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlExtension;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class XmlCompletionContributor extends CompletionContributor {
  public static final Key<Boolean> WORD_COMPLETION_COMPATIBLE = Key.create("WORD_COMPLETION_COMPATIBLE");

  @NonNls public static final String TAG_NAME_COMPLETION_FEATURE = "tag.name.completion";
  private static final InsertHandlerDecorator<LookupElement> QUOTE_EATER = new InsertHandlerDecorator<LookupElement>() {
    public void handleInsert(InsertionContext context, LookupElementDecorator<LookupElement> item) {
      final char completionChar = context.getCompletionChar();
      if (completionChar == '\'' || completionChar == '\"') {
        context.setAddCompletionChar(false);
        item.getDelegate().handleInsert(context);

        final Editor editor = context.getEditor();
        final Document document = editor.getDocument();
        int tailOffset = editor.getCaretModel().getOffset();
        if (document.getTextLength() > tailOffset) {
          final char c = document.getCharsSequence().charAt(tailOffset);
          if (c == completionChar || completionChar == '\'') {
            editor.getCaretModel().moveToOffset(tailOffset + 1);
          }
        }
      } else {
        item.getDelegate().handleInsert(context);
      }
    }
  };

  public XmlCompletionContributor() {
    extend(CompletionType.BASIC,
           PlatformPatterns.psiElement().inside(XmlPatterns.xmlAttributeValue()),
           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull final CompletionResultSet result) {
               final XmlAttributeValue attributeValue = PsiTreeUtil.getParentOfType(parameters.getPosition(), XmlAttributeValue.class, false);
               if (attributeValue == null) {
                 // we are injected, only getContext() returns attribute value
                 return;
               }

               final Set<String> usedWords = new THashSet<String>();
               final Ref<Boolean> addWordVariants = Ref.create(true);
               result.runRemainingContributors(parameters, new Consumer<CompletionResult>() {
                 public void consume(CompletionResult r) {
                   if (r.getLookupElement().getUserData(WORD_COMPLETION_COMPATIBLE) == null) {
                     addWordVariants.set(false);
                   }
                   usedWords.add(r.getLookupElement().getLookupString());
                   result.passResult(r.withLookupElement(LookupElementDecorator.withInsertHandler(r.getLookupElement(), QUOTE_EATER)));
                 }
               });
               if (addWordVariants.get().booleanValue()) {
                 addWordVariants.set(attributeValue.getReferences().length == 0);
               }

               if (addWordVariants.get().booleanValue() && parameters.getInvocationCount() > 0) {
                 WordCompletionContributor.addWordCompletionVariants(result, parameters, usedWords);
               }
             }
           });
  }

  public static boolean isXmlNameCompletion(final CompletionParameters parameters) {
    final ASTNode node = parameters.getPosition().getNode();
    return node != null && node.getElementType() == XmlTokenType.XML_NAME;
  }

  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    super.fillCompletionVariants(parameters, result);
    if (result.isStopped()) {
      return;
    }

    final PsiElement element = parameters.getPosition();

    if (parameters.getCompletionType() == CompletionType.CLASS_NAME) {
      completeTagName(parameters, result);
    }

    else if (parameters.getCompletionType() == CompletionType.SMART) {
      new XmlSmartCompletionProvider().complete(parameters, result, element);
    }
  }

  static void completeTagName(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement element = parameters.getPosition();
    if (!isXmlNameCompletion(parameters)) return;
    result.stopHere();
    PsiElement parent = element.getParent();
    if (!(parent instanceof XmlTag) ||
        !(parameters.getOriginalFile() instanceof XmlFile)) {
      return;
    }
    final XmlTag tag = (XmlTag)parent;
    final String namespace = tag.getNamespace();
    final String prefix = result.getPrefixMatcher().getPrefix();
    final int pos = prefix.indexOf(':');

    final PsiReference reference = tag.getReference();
    String namespacePrefix = tag.getNamespacePrefix();

    if (reference != null && !namespace.isEmpty() && !namespacePrefix.isEmpty()) {
      // fallback to simple completion
      final Set<LookupElement> set = new HashSet<LookupElement>();
      new XmlCompletionData().completeReference(reference, set, element, parameters.getOriginalFile(), parameters.getOffset());
      for (final LookupElement item : set) {
        result.addElement(item);
      }
    }
    else {

      final CompletionResultSet newResult = result.withPrefixMatcher(pos >= 0 ? prefix.substring(pos + 1) : prefix);

      final XmlFile file = (XmlFile)parameters.getOriginalFile();
      final List<Pair<String,String>> names = XmlExtension.getExtension(file).getAvailableTagNames(file, tag);
      for (Pair<String, String> pair : names) {
        final String name = pair.getFirst();
        final String ns = pair.getSecond();
        final LookupElement item = createLookupElement(name, ns, ns, namespacePrefix.isEmpty() ? null : namespacePrefix);
        newResult.addElement(item);
      }
    }
  }

  public static LookupElement createLookupElement(final String name,
                                                  final String namespace,
                                                  final String tailText, @Nullable String namespacePrefix) {
    LookupElementBuilder builder =
      LookupElementBuilder.create(Pair.create(name, namespace), name).setInsertHandler(new ExtendedTagInsertHandler(name, namespace, namespacePrefix));
    if (!StringUtil.isEmpty(namespace)) {
      builder = builder.setTypeText(tailText, true);
    }
    return builder;
  }

  @Override
  public String advertise(@NotNull final CompletionParameters parameters) {
    if (isXmlNameCompletion(parameters) && parameters.getCompletionType() == CompletionType.BASIC) {
      if (FeatureUsageTracker.getInstance().isToBeAdvertisedInLookup(TAG_NAME_COMPLETION_FEATURE, parameters.getPosition().getProject())) {
        final String shortcut = getActionShortcut(IdeActions.ACTION_CLASS_NAME_COMPLETION);
        if (shortcut != null) {
          return XmlBundle.message("tag.name.completion.hint", shortcut);
        }

      }
    }
    return super.advertise(parameters);
  }

  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    final int offset = context.getStartOffset();
    final XmlAttributeValue attributeValue = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), offset, XmlAttributeValue.class, true);
    if (attributeValue != null && offset == attributeValue.getTextRange().getStartOffset()) {
      context.setDummyIdentifier("");
    }
  }

}
