/*
 * Copyright (c) 2013 Malhar Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.common.util;

import java.util.Arrays;

/**
 *
 */
public class Slice
{
  public byte[] buffer;
  public int offset;
  public int length;

  public Slice(byte[] array, int offset, int length)
  {
    buffer = array;
    this.offset = offset;
    this.length = length;
  }

  @Override
  public int hashCode()
  {
    int hash = 5;
    hash = 59 * hash + Arrays.hashCode(this.buffer);
    hash = 59 * hash + this.offset;
    hash = 59 * hash + this.length;
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Slice other = (Slice)obj;
    if (!Arrays.equals(this.buffer, other.buffer)) {
      return false;
    }
    if (this.offset != other.offset) {
      return false;
    }
    if (this.length != other.length) {
      return false;
    }
    return true;
  }

  @Override
  public String toString()
  {
    return "Slice{" + (length > 256 ? "buffer=" + buffer + ", offset=" + offset + ", length=" + length : Arrays.toString(Arrays.copyOfRange(buffer, offset, offset + length))) + '}';
  }

}
