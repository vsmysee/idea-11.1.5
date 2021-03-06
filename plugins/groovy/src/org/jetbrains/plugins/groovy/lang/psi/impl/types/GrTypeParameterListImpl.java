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

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.EmptyStub;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;

/**
 * @author ilyas
 */
public class GrTypeParameterListImpl extends GrStubElementBase<EmptyStub> implements GrTypeParameterList, StubBasedPsiElement<EmptyStub> {
  private static final ArrayFactory<GrTypeParameter> ARRAY_FACTORY = new ArrayFactory<GrTypeParameter>() {
    @Override
    public GrTypeParameter[] create(int count) {
      return new GrTypeParameter[count];
    }
  };

  public GrTypeParameterListImpl(EmptyStub stub) {
    super(stub, GroovyElementTypes.TYPE_PARAMETER_LIST);
  }

  public GrTypeParameterListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiElement getParent() {
    return getParentByStub();
  }

  public String toString() {
    return "Type parameter list";
  }

  public GrTypeParameter[] getTypeParameters() {
    return getStubOrPsiChildren(GroovyElementTypes.TYPE_PARAMETER, ARRAY_FACTORY);
  }

  public int getTypeParameterIndex(PsiTypeParameter typeParameter) {
    final GrTypeParameter[] typeParameters = getTypeParameters();
    for (int i = 0; i < typeParameters.length; i++) {
      if (typeParameters[i].equals(typeParameter)) return i;
    }

    return -1;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTypeParameterList(this);
  }
}
