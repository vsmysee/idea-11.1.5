/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.components;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.NotNullFunction;
import org.jdom.Attribute;
import org.jdom.Comment;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 * @since Dec 6, 2004
 */
public abstract class PathMacroMap {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.PathMacroMap");

  protected final Map<String, String> myMacroMap;

  protected PathMacroMap() {
    myMacroMap = new LinkedHashMap<String,String>();
  }

  public void putAll(PathMacroMap pathMacroMap) {
    putAll(pathMacroMap.myMacroMap);
  }

  public void putAll(Map<String, String> macroMap) {
    myMacroMap.putAll(macroMap);
  }

  public void put(String fromText, String toText) {
    myMacroMap.put(fromText, toText);
  }

  public abstract String substitute(String text, boolean caseSensitive);

  public final void substitute(Element e, boolean caseSensitive) {
    substitute(e, caseSensitive, false);
  }

  public final void substitute(Element e, boolean caseSensitive, final boolean recursively,
                               @Nullable final NotNullFunction<Object, Boolean> filter,
                               @Nullable final NotNullFunction<Object, Boolean> recursiveFilter) {
    List content = e.getContent();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, contentSize = content.size(); i < contentSize; i++) {
      Object child = content.get(i);
      if (child instanceof Element) {
        Element element = (Element)child;
        substitute(element, caseSensitive, recursively, filter, recursiveFilter);
      }
      else if (child instanceof Text) {
        Text t = (Text)child;
        if (filter == null || filter.fun(t)) {
          t.setText((recursively || (recursiveFilter != null && recursiveFilter.fun(t)))
                    ? substituteRecursively(t.getText(), caseSensitive)
                    : substitute(t.getText(), caseSensitive));
        }
      }
      else if (!(child instanceof Comment)) {
        LOG.error("Wrong content: " + child.getClass());
      }
    }

    List attributes = e.getAttributes();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, attributesSize = attributes.size(); i < attributesSize; i++) {
      Object attribute1 = attributes.get(i);
      Attribute attribute = (Attribute)attribute1;
      if (filter == null || filter.fun(attribute)) {
        final String value = (recursively || (recursiveFilter != null && recursiveFilter.fun(attribute)))
                             ? substituteRecursively(attribute.getValue(), caseSensitive)
                             : substitute(attribute.getValue(), caseSensitive);
        attribute.setValue(value);
      }
    }
  }

  public final void substitute(Element e, boolean caseSensitive, final boolean recursively) {
    substitute(e, caseSensitive, recursively, null, null);
  }

  public String substituteRecursively(String text, boolean caseSensitive) {
    return substitute(text, caseSensitive);
  }

  public int size() {
    return myMacroMap.size();
  }

  protected Set<Map.Entry<String, String>> entries() {
    return myMacroMap.entrySet();
  }

  protected Set<String> keySet() {
    return myMacroMap.keySet();
  }

  public String get(String key) {
    return myMacroMap.get(key);
  }

  public static String quotePath(String path) {
    return FileUtil.toSystemIndependentName(path);
  }

  public int hashCode() {
    return myMacroMap.hashCode();
  }
}
