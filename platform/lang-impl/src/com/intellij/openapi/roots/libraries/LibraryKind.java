/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.libraries;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class LibraryKind<P extends LibraryProperties> {
  private final String myKindId;

  /**
   * @param kindId must be unique among all {@link LibraryType} and {@link LibraryPresentationProvider} implementations
   */
  public LibraryKind(@NotNull @NonNls String kindId) {
    myKindId = kindId;
  }

  public final String getKindId() {
    return myKindId;
  }

  @Override
  public String toString() {
    return "LibraryKind:" + myKindId;
  }

  /**
   * @param kindId must be unique among all {@link LibraryType} and {@link LibraryPresentationProvider} implementations
   * @return new {@link LibraryKind} instance
   */
  public static <P extends LibraryProperties> LibraryKind<P> create(@NotNull @NonNls String kindId) {
    return new LibraryKind<P>(kindId);
  }
}
