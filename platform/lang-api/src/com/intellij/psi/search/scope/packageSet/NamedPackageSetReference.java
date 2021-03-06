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
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.vfs.VirtualFile;

public class NamedPackageSetReference extends PackageSetBase {
  private final String myName;

  public NamedPackageSetReference(String name) {
    myName = name.startsWith("$") ? name.substring(1) : name;
  }

  public boolean contains(VirtualFile file, NamedScopesHolder holder) {
    final NamedScope scope = holder.getScope(myName);
    if (scope != null) {
      final PackageSet packageSet = scope.getValue();
      if (packageSet != null) {
        return packageSet instanceof PackageSetBase ? ((PackageSetBase)packageSet).contains(file, holder) : packageSet.contains(getPsiFile(file, holder), holder);
      }
    }
    return false;
  }

  public PackageSet createCopy() {
    return new NamedPackageSetReference(myName);
  }

  public String getText() {
    return "$" + myName;
  }

  public int getNodePriority() {
    return 0;
  }
}
