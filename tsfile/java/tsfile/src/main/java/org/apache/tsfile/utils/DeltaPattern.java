/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tsfile.utils;

import org.apache.tsfile.block.column.Column;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeltaPattern {
  protected static final Logger LOGGER = LoggerFactory.getLogger(DeltaPattern.class);

  protected Column value;

  public DeltaPattern(Column value) {
    this.value = value;
  }

  public void setValue(Column value) {
    this.value = value;
  }

  public Column getValue() {
    return value;
  }

  public int getPositionCount() {
    return value.getPositionCount();
  }

  public void reverseValue() {
    this.value.reverse();
  }

  public void subColumn(int fromIndex) {
    if (fromIndex > value.getPositionCount()) {
      throw new IllegalArgumentException("fromIndex > logicPositionOffset");
    }
    if (value.getPositionCount() > 1) {
      value = value.subColumn(fromIndex);
    }
  }

  public void getRegion(int positionOffset, int length) {
    if (length > value.getPositionCount()) {
      throw new IllegalArgumentException("length > logicPositionOffset");
    }
    if (value.getPositionCount() > 1) {
      value = value.getRegion(positionOffset, length);
    }
  }

  public String toString() {
    return value.toString();
  }

  public static class IntDeltaPattern extends DeltaPattern {
    private int minDeltaBase;

    public IntDeltaPattern(Column value, int minDeltaBase) {
      super(value);
      this.minDeltaBase = minDeltaBase;
    }

    public int getMinDeltaBase() {
      return minDeltaBase;
    }

    public IntDeltaPattern deepCopy() {
      return new IntDeltaPattern(value, minDeltaBase);
    }
  }

  public static class LongDeltaPattern extends DeltaPattern {
    private long minDeltaBase;

    public LongDeltaPattern(Column value, long minDeltaBase) {
      super(value);
      this.minDeltaBase = minDeltaBase;
    }

    public long getMinDeltaBase() {
      return minDeltaBase;
    }

    public LongDeltaPattern deepCopy() {
      return new LongDeltaPattern(value, minDeltaBase);
    }
  }
}
