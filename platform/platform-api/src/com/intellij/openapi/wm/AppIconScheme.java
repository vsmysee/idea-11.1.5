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
package com.intellij.openapi.wm;

import java.awt.*;

public class AppIconScheme {

  public interface Progress {

    static final Progress TESTS = new Progress() {
      public Color getOkColor() {
        return Color.green;
      }

      public Color getErrorColor() {
        return Color.red;
      }
    };

    static final Progress BUILD = new Progress() {
      public Color getOkColor() {
        return new Color(51, 102, 255);
      }

      public Color getErrorColor() {
        return Color.red;
      }
    };

    static final Progress INDEXING = new Progress() {
      public Color getOkColor() {
        return new Color(255, 153, 0);
      }

      public Color getErrorColor() {
        return Color.red;
      }
    };

    Color getOkColor();
    Color getErrorColor();

  }

}
