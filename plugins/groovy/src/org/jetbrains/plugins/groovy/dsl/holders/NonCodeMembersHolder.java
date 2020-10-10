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
package org.jetbrains.plugins.groovy.dsl.holders;

import com.google.common.collect.Maps;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.plugins.groovy.dsl.CustomMembersGenerator;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class NonCodeMembersHolder implements CustomMembersHolder {
  public static final Key<String> DOCUMENTATION = Key.create("GdslDocumentation");
  public static final Key<String> DOCUMENTATION_URL = Key.create("GdslDocumentationUrl");
  private final List<GrLightMethodBuilder> myMethods = new ArrayList<GrLightMethodBuilder>();
  private static final Key<CachedValue<ConcurrentFactoryMap<Set<Map>, NonCodeMembersHolder>>> CACHED_HOLDERS = Key.create("CACHED_HOLDERS");

  public static NonCodeMembersHolder generateMembers(Set<Map> methods, final PsiFile place) {
    return CachedValuesManager.getManager(place.getProject()).getCachedValue(place, CACHED_HOLDERS, new CachedValueProvider<ConcurrentFactoryMap<Set<Map>, NonCodeMembersHolder>>() {
      public Result<ConcurrentFactoryMap<Set<Map>, NonCodeMembersHolder>> compute() {
        final ConcurrentFactoryMap<Set<Map>, NonCodeMembersHolder> map = new ConcurrentFactoryMap<Set<Map>, NonCodeMembersHolder>() {
          @Override
          protected NonCodeMembersHolder create(Set<Map> key) {
            return new NonCodeMembersHolder(key, place);
          }
        };
        return Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
      }
    }, false).get(methods);
  }

  public NonCodeMembersHolder(Set<Map> data, PsiElement place) {
    final PsiManager manager = place.getManager();
    for (Map prop : data) {
      String name = String.valueOf(prop.get("name"));

      final GrLightMethodBuilder method = new GrLightMethodBuilder(manager, name).addModifier(PsiModifier.PUBLIC);

      if (Boolean.TRUE.equals(prop.get("constructor"))) {
        method.setConstructor(true);
      } else {
        method.setReturnType(convertToPsiType(String.valueOf(prop.get("type")), place));
      }

      final Object params = prop.get("params");
      if (params instanceof Map) {
        boolean first = true;
        for (Object paramName : ((Map)params).keySet()) {
          Object value = ((Map)params).get(paramName);
          boolean isNamed = first && value instanceof List;
          first = false;
          String typeName = isNamed ? CommonClassNames.JAVA_UTIL_MAP : String.valueOf(value);
          method.addParameter(String.valueOf(paramName), convertToPsiType(typeName, place), false);

          if (isNamed) {
            Map<String, NamedArgumentDescriptor> namedParams = Maps.newHashMap();
            for (Object o : (List)value) {
              if (o instanceof CustomMembersGenerator.ParameterDescriptor) {
                namedParams.put(((CustomMembersGenerator.ParameterDescriptor)o).name,
                                ((CustomMembersGenerator.ParameterDescriptor)o).descriptor);
              }
            }
            method.setNamedParameters(namedParams);
          }
        }
      }

      if (Boolean.TRUE.equals(prop.get("isStatic"))) {
        method.addModifier(PsiModifier.STATIC);
      }

      final Object bindsTo = prop.get("bindsTo");
      if (bindsTo instanceof PsiElement) {
        method.setNavigationElement((PsiElement)bindsTo);
      }

      final Object toThrow = prop.get(CustomMembersGenerator.THROWS);
      if (toThrow instanceof List) {
        for (Object o : ((List)toThrow)) {
          final PsiType psiType = convertToPsiType(String.valueOf(o), place);
          if (psiType instanceof PsiClassType) {
            method.addException((PsiClassType)psiType);
          }
        }
      }

      Object doc = prop.get("doc");
      if (doc instanceof String) {
        method.putUserData(DOCUMENTATION, (String)doc);
      }

      Object docUrl = prop.get("docUrl");
      if (docUrl instanceof String) {
        method.putUserData(DOCUMENTATION_URL, (String)docUrl);
      }

      myMethods.add(method);
    }
  }

  private static PsiType convertToPsiType(String type, PsiElement place) {
    return JavaPsiFacade.getElementFactory(place.getProject()).createTypeFromText(type, place);
  }

  public boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor processor, ResolveState state) {
    for (PsiMethod method : myMethods) {
      if (!processor.execute(method, state)) {
        return false;
      }
    }
    return true;
  }
}
