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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.StandardPatterns;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class RelaxedMatchingContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, final CompletionResultSet result) {
    final Set<CompletionResult> elements = result.runRemainingContributors(parameters, true);

    if (!elements.isEmpty() && parameters.getInvocationCount() == 0) {
      Set<String> prefixes = new HashSet<String>();
      for (CompletionResult element : elements) {
        prefixes.add(element.getPrefixMatcher().getPrefix());
      }
      for (String prefix : prefixes) {
        result.withPrefixMatcher(prefix)
          .restartCompletionOnPrefixChange(StandardPatterns.string().with(new PatternCondition<String>("noneMatch") {
            @Override
            public boolean accepts(@NotNull String s, ProcessingContext context) {
              for (CompletionResult element : elements) {
                if (element.getPrefixMatcher().cloneWithPrefix(s).prefixMatches(element.getLookupElement())) {
                  return false;
                }
              }
              return true;
            }
          }));
      }
    }

    CompletionParameters relaxed;
    if (parameters.getInvocationCount() == 0 && (elements.isEmpty() || elements.size() == 1 && elements.iterator().next().getLookupElement().as(
      LiveTemplateLookupElement.class) != null)) {
      relaxed = parameters.withRelaxedMatching();
    }
    else if (parameters.getInvocationCount() >= 2) {
      relaxed = parameters.withRelaxedMatching().withInvocationCount(parameters.getInvocationCount() - 1);
    }
    else {
      return;
    }

    result.runRemainingContributors(relaxed, new Consumer<CompletionResult>() {
      @Override
      public void consume(CompletionResult element) {
        result.passResult(element);
      }
    });
  }
}
