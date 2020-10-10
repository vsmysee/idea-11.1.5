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

package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrMultiTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.io.IOException;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.06.2009
 */
public class GrStubUtils {


  private GrStubUtils() {
  }

  public static String[] getMultiTypes(GrMultiTypeParameter psi) {
    final GrTypeElement[] typeElements = psi.getTypeElements();
    final String[] types = new String[typeElements.length];
    for (int i = 0; i < typeElements.length; i++) {
      types[i] = typeElements[i].getText();
    }
    return types;
  }

  public static void writeStringArray(StubOutputStream dataStream, String[] array) throws IOException {
    dataStream.writeByte(array.length);
    for (String s : array) {
      dataStream.writeName(s);
    }
  }

  public static String[] readStringArray(StubInputStream dataStream) throws IOException {
    final byte b = dataStream.readByte();
    final String[] annNames = new String[b];
    for (int i = 0; i < b; i++) {
      annNames[i] = dataStream.readName().toString();
    }
    return annNames;
  }

  public static void writeNullableString(StubOutputStream dataStream, @Nullable String typeText) throws IOException {
    dataStream.writeBoolean(typeText != null);
    if (typeText != null) {
      dataStream.writeUTFFast(typeText);
    }
  }

  @Nullable
  public static String readNullableString(StubInputStream dataStream) throws IOException {
    final boolean hasTypeText = dataStream.readBoolean();
    return hasTypeText ? dataStream.readUTFFast() : null;
  }

  @Nullable
  public static String getTypeText(GrVariable psi) {
    final GrTypeElement typeElement = psi.getTypeElementGroovy();
    return typeElement == null ? null : typeElement.getText();
  }

  public static String[] getAnnotationNames(PsiModifierListOwner psi) {
    List<String> annoNames = CollectionFactory.arrayList();
    final PsiModifierList modifierList = psi.getModifierList();
    if (modifierList instanceof GrModifierList) {
      for (GrAnnotation annotation : ((GrModifierList)modifierList).getAnnotations()) {
        final GrCodeReferenceElement element = annotation.getClassReference();
        final String annoShortName = StringUtil.getShortName(element.getText()).trim();
        if (StringUtil.isNotEmpty(annoShortName)) {
          annoNames.add(annoShortName);
        }
      }
    }
    return ArrayUtil.toStringArray(annoNames);
  }
}
