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

public class RLEPattern {
  private static final Logger LOGGER = LoggerFactory.getLogger(RLEPattern.class);

  private Column value;
  private int logicPositionCount;

  public RLEPattern(Column value, int logicPositionCount) {
    this.value = value;
    this.logicPositionCount = logicPositionCount;
  }

  public void setValue(Column value) {
    this.value = value;
  }

  public Column getValue() {
    return value;
  }

  public void setLogicPositionCount(int logicPositionCount) {
    if (logicPositionCount < 0) {
      throw new IllegalArgumentException("logicPositioncount must larger than 0");
    }
    this.logicPositionCount = logicPositionCount;
  }

  public int getLogicPositionCount() {
    return logicPositionCount;
  }

  public void reverseValue() {
    this.value.reverse();
  }

  public void subColumn(int fromIndex) {
    if (fromIndex > logicPositionCount) {
      throw new IllegalArgumentException("fromIndex > logicPositionOffset");
    }
    if (value.getPositionCount() > 1) {
      value = value.subColumn(fromIndex);
    }
    logicPositionCount -= fromIndex;
  }

  public void getRegion(int positionOffset, int length) {
    if (length > logicPositionCount) {
      throw new IllegalArgumentException("length > logicPositionOffset");
    }
    if (value.getPositionCount() > 1) {
      value = value.getRegion(positionOffset, length);
    }
    logicPositionCount = length;
  }

  public String toString() {
    return "<" + value.toString() + "," + logicPositionCount + ">";
  }

  public RLEPattern deepCopy() {
    return new RLEPattern(value, logicPositionCount);
  }
}
