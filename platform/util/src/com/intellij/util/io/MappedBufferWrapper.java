/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;
import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * @author max
 */
public abstract class MappedBufferWrapper extends ByteBufferWrapper {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.util.io.MappedBufferWrapper");

  private static final int MAX_FORCE_ATTEMPTS = 10;

  private volatile MappedByteBuffer myBuffer;

  protected MappedBufferWrapper(final File file, final long pos, final long length) {
    super(file, pos, length);
  }

  protected abstract MappedByteBuffer map() throws IOException;

  @Override
  public final void unmap() {
    long started = IOStatistics.DEBUG ? System.currentTimeMillis() : 0;
    MappedByteBuffer buffer = myBuffer;
    myBuffer = null;
    if (!clean(buffer)) {
      LOG.error("Unmapping failed for: " + myFile);
    }

    if (IOStatistics.DEBUG) {
      long finished = System.currentTimeMillis();
      if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
        IOStatistics.dump("Unmapped " + myFile + "," + myPosition + "," + myLength + " for " + (finished - started));
      }
    }
  }

  @Override
  public ByteBuffer getCachedBuffer() {
    return myBuffer;
  }

  @Override
  public ByteBuffer getBuffer() throws IOException {
    MappedByteBuffer buffer = myBuffer;
    if (buffer == null) {
      myBuffer = buffer = map();
    }
    return buffer;
  }

  private static boolean clean(final MappedByteBuffer buffer) {
    if (buffer == null) return true;

    if (!tryForce(buffer)) {
      return false;
    }

    return AccessController.doPrivileged(new PrivilegedAction<Object>() {
      @Nullable
      public Object run() {
        try {
          Cleaner cleaner = ((DirectBuffer)buffer).cleaner();
          if (cleaner != null) cleaner.clean(); // Already cleaned otherwise
          return null;
        }
        catch (Exception e) {
          return buffer;
        }
      }
    }) == null;
  }

  private static boolean tryForce(MappedByteBuffer buffer) {
    for (int i = 0; i < MAX_FORCE_ATTEMPTS; i++) {
      try {
        buffer.force();
        return true;
      }
      catch (Throwable e) {
        LOG.info(e);
        try {
          //noinspection BusyWait
          Thread.sleep(10);
        }
        catch (InterruptedException ignored) { }
      }
    }
    return false;
  }

  @Override
  public void flush() {
    final MappedByteBuffer buffer = myBuffer;
    if (buffer != null) {
      tryForce(buffer);
    }
  }
}
