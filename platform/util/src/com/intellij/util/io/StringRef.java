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
 * @author max
 */
package com.intellij.util.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class StringRef {
  public static final StringRef[] EMPTY_ARRAY = new StringRef[0];
  
  private int id;
  private String name;
  private final PersistentStringEnumerator store;

  private StringRef(final String name) {
    this.name = name;
    id = -1;
    store = null;
  }

  private StringRef(final int id, final PersistentStringEnumerator store) {
    this.id = id;
    this.store = store;
    name = null;
  }

  public String getString() {
    if (name == null) {
      try {
        name = store.valueOf(id);
      }
      catch (IOException e) {
        store.markCorrupted();
        throw new RuntimeException(e);
      }
    }
    return name;
  }

  public void writeTo(DataOutput out, PersistentStringEnumerator store) throws IOException {
    int nameId = getId(store);
    out.writeByte(nameId & 0xFF);
    DataInputOutputUtil.writeINT(out, (nameId >> 8));
  }

  public int getId(PersistentStringEnumerator store) {
    if (id == -1) {
      try {
        id = store.enumerate(name);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return id;
  }

  public String toString() {
    return getString();
  }

  public int length() {
    return getString().length();
  }

  public int hashCode() {
    return toString().hashCode();
  }

  public boolean equals(final Object that) {
    return that == this || that instanceof StringRef && toString().equals(that.toString());
  }

  public static String toString(StringRef ref) {
    return ref != null ? ref.getString(): null;
  }

  public static StringRef fromString(String source) {
    return source == null ? null : new StringRef(source);
  }

  public static StringRef fromStream(DataInput in, PersistentStringEnumerator store) throws IOException {
    final int low = in.readUnsignedByte();
    final int nameId = (DataInputOutputUtil.readINT(in) << 8) | low;

    return nameId != 0 ? new StringRef(nameId, store) : null;
  }

  public static StringRef[] createArray(int count) {
    return count == 0 ? EMPTY_ARRAY : new StringRef[count];
  }

}
