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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.util.Consumer;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/28/12
 * Time: 6:06 PM
 */
public class TodoForBaseRevision extends TodoForRanges {
  private final Getter<Object> myGetter;
  private final Consumer<Object> mySaver;

  public TodoForBaseRevision(Project project,
                              List<TextRange> ranges,
                              int additionalOffset,
                              String name,
                              String text,
                              boolean revision, FileType type, final Getter<Object> cacheGetter,
                              final Consumer<Object> cacheSaver) {
    super(project, ranges, additionalOffset, name, text, revision, type);
    myGetter = cacheGetter;
    mySaver = cacheSaver;
  }

  @Override
  protected TodoItem[] getTodoItems() {
    final TodoItem[] items = (TodoItem[])myGetter.get();
    if (items != null) return items;
    final TodoItem[] todoItems = getTodoForText(PsiSearchHelper.SERVICE.getInstance(myProject));
    if (todoItems != null) {
      mySaver.consume(todoItems);
    }
    return todoItems;
  }
}
