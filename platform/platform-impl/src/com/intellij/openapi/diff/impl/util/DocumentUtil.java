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
package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;

public class DocumentUtil {

  public static String getText(RangeMarker range) {
    return range.getDocument().getText().substring(range.getStartOffset(), range.getEndOffset());
  }

  public static boolean isEmpty(RangeMarker rangeMarker) {
    return rangeMarker.getStartOffset() == rangeMarker.getEndOffset();
  }

  public static int getStartLine(RangeMarker range) {
    final Document doc = range.getDocument();
    if (doc.getTextLength() == 0) return 0;

    return doc.getLineNumber(range.getStartOffset());
  }

  public static int getEndLine(RangeMarker range) {
    Document document = range.getDocument();
    int endOffset = range.getEndOffset();
    if (document.getTextLength() == endOffset) return document.getLineCount();
    return document.getLineNumber(endOffset);
  }

  public static int getLength(RangeMarker rangeMarker) {
    return rangeMarker.getEndOffset() - rangeMarker.getStartOffset();
  }
}
