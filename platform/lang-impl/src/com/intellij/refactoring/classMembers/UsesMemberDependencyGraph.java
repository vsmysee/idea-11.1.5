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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 08.07.2002
 * Time: 18:22:48
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.classMembers;

import com.intellij.lang.LanguageDependentMembersRefactoringSupport;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class UsesMemberDependencyGraph<T extends NavigatablePsiElement, C extends PsiElement, M extends MemberInfoBase<T>> implements MemberDependencyGraph<T, M> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.classMembers.UsesMemberDependencyGraph");
  protected HashSet<T> mySelectedNormal;
  protected HashSet<T> mySelectedAbstract;
  protected HashSet<T> myDependencies = null;
  protected HashMap<T, HashSet<T>> myDependenciesToDependentMap = null;
  private final boolean myRecursive;
  private final MemberDependenciesStorage<T, C> myMemberDependenciesStorage;

  public UsesMemberDependencyGraph(C aClass, C superClass, boolean recursive) {
    myRecursive = recursive;
    mySelectedNormal = new HashSet<T>();
    mySelectedAbstract = new HashSet<T>();
    myMemberDependenciesStorage = new MemberDependenciesStorage<T, C>(aClass, superClass);
  }


  public Set<? extends T> getDependent() {
    if (myDependencies == null) {
      myDependencies = new HashSet<T>();
      myDependenciesToDependentMap = new HashMap<T, HashSet<T>>();
      buildDeps(null, mySelectedNormal);
    }
    return myDependencies;
  }

  public Set<? extends T> getDependenciesOf(T member) {
    final Set dependent = getDependent();
    if(!dependent.contains(member)) return null;
    return myDependenciesToDependentMap.get(member);
  }

  public String getElementTooltip(T element) {
    final Set<? extends T> dependencies = getDependenciesOf(element);
    if(dependencies == null || dependencies.size() == 0) return null;

    ArrayList<String> strings = new ArrayList<String>();
    for (T dep : dependencies) {
      strings.add(dep.getName());
    }

    if(strings.isEmpty()) return null;
    return RefactoringBundle.message("used.by.0", StringUtil.join(strings, ", "));
  }


  private void buildDeps(T sourceElement, Set<T> members) {
    if (myRecursive) {
      buildDepsRecursively(sourceElement, members);
    }
    else {
      for (final T member : members) {
        for (final T dependency : myMemberDependenciesStorage.getMemberDependencies(member)) {
          addDependency(dependency, member);
        }
      }
    }
  }

  private void buildDepsRecursively(final T sourceElement, final Set<T> members) {
    for (T member : members) {
      if (!myDependencies.contains(member)) {
        addDependency(member, sourceElement);
        if (!mySelectedAbstract.contains(member)) {
          buildDepsRecursively(member, myMemberDependenciesStorage.getMemberDependencies(member));
        }
      }
    }
  }

  private void addDependency(final T member, final T sourceElement) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(member.toString());
    }
    myDependencies.add(member);
    if (sourceElement != null) {
      HashSet<T> relations = myDependenciesToDependentMap.get(member);
      if (relations == null) {
        relations = new HashSet<T>();
        myDependenciesToDependentMap.put(member, relations);
      }
      relations.add(sourceElement);
    }
  }

  public void memberChanged(M memberInfo) {
    final ClassMembersRefactoringSupport support =
      LanguageDependentMembersRefactoringSupport.INSTANCE.forLanguage(memberInfo.getMember().getLanguage());
    if (support != null && support.isProperMember(memberInfo)) {
      myDependencies = null;
      myDependenciesToDependentMap = null;
      T member = memberInfo.getMember();
      if (!memberInfo.isChecked()) {
        mySelectedNormal.remove(member);
        mySelectedAbstract.remove(member);
      } else {
        if (memberInfo.isToAbstract()) {
          mySelectedNormal.remove(member);
          mySelectedAbstract.add(member);
        } else {
          mySelectedNormal.add(member);
          mySelectedAbstract.remove(member);
        }
      }
    }
  }
}