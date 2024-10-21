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

package org.apache.tsfile.read.common.block.column;

import org.apache.tsfile.block.column.Column;
import org.apache.tsfile.block.column.ColumnEncoding;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.utils.Binary;
import org.apache.tsfile.utils.Pair;
import org.apache.tsfile.utils.RamUsageEstimator;
import org.apache.tsfile.utils.TsPrimitiveType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;
import static org.apache.tsfile.read.common.block.column.ColumnUtil.checkValidRegion;
import static org.apache.tsfile.utils.RamUsageEstimator.sizeOfIntArray;

public class RLEColumn implements Column {
  private static final Logger LOGGER = LoggerFactory.getLogger(RLEColumn.class);

  private static final int INSTANCE_SIZE =
      (int) RamUsageEstimator.shallowSizeOfInstance(RLEColumn.class);

  private int arrayOffset;
  private final int positionCount;
  private final Column[] values;
  // patternOffsetIndex[i] refers to the offset of values[i].getObject(0) in all data
  private final int[] patternOffsetIndex;
  // Marking the latest read column index, which can effectively save traversal time when data is
  // continuously read.
  private int curIndex;

  public RLEColumn(int positionCount, Column[] values, int[] patternOffsetIndex) {
    this(0, positionCount, values, patternOffsetIndex, 0);
  }

  public RLEColumn(int arrayOffset, int positionCount, Column[] values, int[] patternOffsetIndex) {
    this(arrayOffset, positionCount, values, patternOffsetIndex, 0);
  }

  RLEColumn(
      int arrayOffset, int positionCount, Column[] values, int[] patternOffsetIndex, int curIndex) {
    requireNonNull(values, "values is null");
    requireNonNull(patternOffsetIndex, "patternOffsetIndex is null");

    if (arrayOffset < 0) {
      throw new IllegalArgumentException("arrayOffset is negative");
    }
    this.arrayOffset = arrayOffset;

    if (positionCount < 0) {
      throw new IllegalArgumentException("positionCount is negative");
    }
    this.positionCount = positionCount;

    if (patternOffsetIndex.length != values.length + 1) {
      throw new IllegalArgumentException(
          "patternOffsetIndex length and values length do not match");
    }
    this.values = values;
    this.patternOffsetIndex = patternOffsetIndex;
    this.curIndex = curIndex;
  }

  private int getCurIndex(int position) {
    if (position > arrayOffset + positionCount) {
      throw new IllegalArgumentException(
          " position: "
              + position
              + " out of the bound of valid positions: "
              + (arrayOffset + positionCount - 1));
    } else if (position == arrayOffset + positionCount) {
      int index;
      for (index = curIndex;
          index < patternOffsetIndex.length && patternOffsetIndex[index] == 0;
          index++) ;
      curIndex = index - 1;
      return curIndex;
    }
    int index;
    if (position >= patternOffsetIndex[curIndex]) {
      /** check if curIndex hit */
      if (position < patternOffsetIndex[curIndex + 1]) {
        /** hit */
        return curIndex;
      } else {
        /** miss, traverse from curIndex + 1 and update curIndex */
        for (index = curIndex + 1;
            index < values.length && position >= patternOffsetIndex[index];
            index++) ;
        curIndex = index - 1;
        return curIndex;
      }
    }

    /** miss, traverse from scratch and reset curIndex */
    for (index = 0;
        index < patternOffsetIndex.length && position >= patternOffsetIndex[index];
        index++) ;
    curIndex = index - 1;
    return curIndex;
  }

  @Override
  public TSDataType getDataType() {
    return (values[0]).getDataType();
  }

  @Override
  public boolean getBoolean(int position) {
    int curIndex = getCurIndex(arrayOffset + position);
    return values[curIndex].getPositionCount() == 1
        ? values[curIndex].getBoolean(0)
        : values[curIndex].getBoolean(arrayOffset + position - patternOffsetIndex[curIndex]);
  }

  @Override
  public int getInt(int position) {
    int curIndex = getCurIndex(arrayOffset + position);
    return values[curIndex].getPositionCount() == 1
        ? values[curIndex].getInt(0)
        : values[curIndex].getInt(arrayOffset + position - patternOffsetIndex[curIndex]);
  }

  @Override
  public long getLong(int position) {
    int curIndex = getCurIndex(arrayOffset + position);
    return values[curIndex].getPositionCount() == 1
        ? values[curIndex].getLong(0)
        : values[curIndex].getLong(arrayOffset + position - patternOffsetIndex[curIndex]);
  }

  @Override
  public float getFloat(int position) {
    int curIndex = getCurIndex(arrayOffset + position);
    return values[curIndex].getPositionCount() == 1
        ? values[curIndex].getFloat(0)
        : values[curIndex].getFloat(arrayOffset + position - patternOffsetIndex[curIndex]);
  }

  @Override
  public double getDouble(int position) {
    int curIndex = getCurIndex(arrayOffset + position);
    return values[curIndex].getPositionCount() == 1
        ? values[curIndex].getDouble(0)
        : values[curIndex].getDouble(arrayOffset + position - patternOffsetIndex[curIndex]);
  }

  @Override
  public Binary getBinary(int position) {
    int curIndex = getCurIndex(arrayOffset + position);
    return values[curIndex].getPositionCount() == 1
        ? values[curIndex].getBinary(0)
        : values[curIndex].getBinary(arrayOffset + position - patternOffsetIndex[curIndex]);
  }

  public Pair<Column[], int[]> getVisibleColumns() {
    int startIndex = getCurIndex(arrayOffset);
    int endIndex = getCurIndex(arrayOffset + positionCount - 1);
    Column[] visibleColumns = Arrays.copyOfRange(values, startIndex, endIndex + 1);
    int[] logicPositionCount = new int[endIndex - startIndex + 1];
    if (visibleColumns[endIndex - startIndex].getPositionCount() > 1) {
      visibleColumns[endIndex - startIndex] =
          visibleColumns[endIndex - startIndex].getRegion(
              0, arrayOffset + positionCount - patternOffsetIndex[endIndex]);
    }
    if (visibleColumns[0].getPositionCount() > 1) {
      visibleColumns[0] = visibleColumns[0].subColumn(arrayOffset - patternOffsetIndex[startIndex]);
    }
    for (int i = 0, idx = startIndex; idx <= endIndex; i++, idx++) {
      logicPositionCount[i] = patternOffsetIndex[idx + 1] - patternOffsetIndex[idx];
    }
    if (endIndex != startIndex) {
      logicPositionCount[0] = patternOffsetIndex[startIndex + 1] - arrayOffset;
      logicPositionCount[endIndex - startIndex] =
          arrayOffset + positionCount - patternOffsetIndex[endIndex];
    } else {
      logicPositionCount[0] = positionCount;
    }
    return new Pair<Column[], int[]>(visibleColumns, logicPositionCount);
  }

  @Override
  public Object getObject(int position) {
    int curIndex = getCurIndex(arrayOffset + position);
    return values[curIndex].getPositionCount() == 1
        ? values[curIndex].getObject(0)
        : values[curIndex].getObject(arrayOffset + position - patternOffsetIndex[curIndex]);
  }

  @Override
  public boolean[] getBooleans() {
    boolean[] res = new boolean[patternOffsetIndex[values.length]];
    for (int i = 0; i < values.length; i++) {
      int curPatternActualPositionCount = values[i].getPositionCount();
      if (curPatternActualPositionCount == 1) {
        Arrays.fill(res, patternOffsetIndex[i], patternOffsetIndex[i + 1], values[i].getBoolean(0));
      } else {
        int startIndex = patternOffsetIndex[i];
        for (int j = 0; j < curPatternActualPositionCount; j++) {
          res[startIndex + j] = values[i].getBoolean(j);
        }
      }
    }
    return res;
  }

  @Override
  public int[] getInts() {
    int[] res = new int[patternOffsetIndex[values.length]];
    for (int i = 0; i < values.length; i++) {
      int curPatternActualPositionCount = values[i].getPositionCount();
      if (curPatternActualPositionCount == 1) {
        Arrays.fill(res, patternOffsetIndex[i], patternOffsetIndex[i + 1], values[i].getInt(0));
      } else {
        int startIndex = patternOffsetIndex[i];
        for (int j = 0; j < curPatternActualPositionCount; j++) {
          res[startIndex + j] = values[i].getInt(j);
        }
      }
    }
    return res;
  }

  @Override
  public long[] getLongs() {
    long[] res = new long[patternOffsetIndex[values.length]];
    for (int i = 0; i < values.length; i++) {
      int curPatternActualPositionCount = values[i].getPositionCount();
      if (curPatternActualPositionCount == 1) {
        Arrays.fill(res, patternOffsetIndex[i], patternOffsetIndex[i + 1], values[i].getLong(0));
      } else {
        int startIndex = patternOffsetIndex[i];
        for (int j = 0; j < curPatternActualPositionCount; j++) {
          res[startIndex + j] = values[i].getLong(j);
        }
      }
    }
    return res;
  }

  @Override
  public float[] getFloats() {
    float[] res = new float[patternOffsetIndex[values.length]];
    for (int i = 0; i < values.length; i++) {
      int curPatternActualPositionCount = values[i].getPositionCount();
      if (curPatternActualPositionCount == 1) {
        Arrays.fill(res, patternOffsetIndex[i], patternOffsetIndex[i + 1], values[i].getFloat(0));
      } else {
        int startIndex = patternOffsetIndex[i];
        for (int j = 0; j < curPatternActualPositionCount; j++) {
          res[startIndex + j] = values[i].getFloat(j);
        }
      }
    }
    return res;
  }

  @Override
  public double[] getDoubles() {
    double[] res = new double[patternOffsetIndex[values.length]];
    for (int i = 0; i < values.length; i++) {
      int curPatternActualPositionCount = values[i].getPositionCount();
      if (curPatternActualPositionCount == 1) {
        Arrays.fill(res, patternOffsetIndex[i], patternOffsetIndex[i + 1], values[i].getDouble(0));
      } else {
        int startIndex = patternOffsetIndex[i];
        for (int j = 0; j < curPatternActualPositionCount; j++) {
          res[startIndex + j] = values[i].getDouble(j);
        }
      }
    }
    return res;
  }

  @Override
  public Binary[] getBinaries() {
    Binary[] res = new Binary[patternOffsetIndex[values.length]];
    for (int i = 0; i < values.length; i++) {
      int curPatternActualPositionCount = values[i].getPositionCount();
      if (curPatternActualPositionCount == 1) {
        Arrays.fill(res, patternOffsetIndex[i], patternOffsetIndex[i + 1], values[i].getBinary(0));
      } else {
        int startIndex = patternOffsetIndex[i];
        for (int j = 0; j < curPatternActualPositionCount; j++) {
          res[startIndex + j] = values[i].getBinary(j);
        }
      }
    }
    return res;
  }

  @Override
  public Object[] getObjects() {
    Object[] res = new Object[patternOffsetIndex[values.length]];
    for (int i = 0; i < values.length; i++) {
      int curPatternActualPositionCount = values[i].getPositionCount();
      if (curPatternActualPositionCount == 1) {
        Arrays.fill(res, patternOffsetIndex[i], patternOffsetIndex[i + 1], values[i].getObject(0));
      } else {
        int startIndex = patternOffsetIndex[i];
        for (int j = 0; j < curPatternActualPositionCount; j++) {
          res[startIndex + j] = values[i].getObject(j);
        }
      }
    }
    return res;
  }

  @Override
  public ColumnEncoding getEncoding() {
    return ColumnEncoding.RLE_ARRAY;
  }

  @Override
  public TsPrimitiveType getTsPrimitiveType(int position) {
    int curIndex = getCurIndex(arrayOffset + position);
    return values[curIndex].getPositionCount() == 1
        ? values[curIndex].getTsPrimitiveType(0)
        : values[curIndex].getTsPrimitiveType(
            arrayOffset + position - patternOffsetIndex[curIndex]);
  }

  @Override
  public boolean mayHaveNull() {
    int startIndex = getCurIndex(arrayOffset);
    int endIndex = getCurIndex(arrayOffset + positionCount - 1);
    for (int i = startIndex; i <= endIndex; i++) {
      if (values[i].mayHaveNull()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isNull(int position) {
    int curIndex = getCurIndex(arrayOffset + position);
    return values[curIndex].getPositionCount() == 1
        ? values[curIndex].isNull(0)
        : values[curIndex].isNull(arrayOffset + position - patternOffsetIndex[curIndex]);
  }

  @Override
  public boolean[] isNull() {
    boolean[] res = new boolean[patternOffsetIndex[values.length]];
    for (int i = 0; i < values.length; i++) {
      int curPatternActualPositionCount = values[i].getPositionCount();
      if (curPatternActualPositionCount == 1) {
        Arrays.fill(res, patternOffsetIndex[i], patternOffsetIndex[i + 1], values[i].isNull(0));
      } else {
        int startIndex = patternOffsetIndex[i];
        for (int j = 0; j < curPatternActualPositionCount; j++) {
          res[startIndex + j] = values[i].isNull(j);
        }
      }
    }
    return res;
  }

  @Override
  public int getPositionCount() {
    return positionCount;
  }

  @Override
  public long getRetainedSizeInBytes() {
    long valuesRetainedSizeInBytes = 0;
    int startIndex = getCurIndex(arrayOffset);
    int endIndex = getCurIndex(arrayOffset + positionCount - 1);
    for (int i = startIndex; i <= endIndex; i++) {
      valuesRetainedSizeInBytes += values[i].getRetainedSizeInBytes();
    }
    return INSTANCE_SIZE + sizeOfIntArray(endIndex - startIndex + 2) + valuesRetainedSizeInBytes;
  }

  @Override
  public Column getRegion(int positionOffset, int length) {
    checkValidRegion(positionCount, positionOffset, length);
    return new RLEColumn(
        arrayOffset + positionOffset,
        length,
        values,
        patternOffsetIndex,
        getCurIndex(arrayOffset + positionOffset));
  }

  @Override
  public Column subColumn(int fromIndex) {
    if (fromIndex > positionCount) {
      throw new IllegalArgumentException("fromIndex is not valid");
    }
    return new RLEColumn(
        arrayOffset + fromIndex,
        positionCount - fromIndex,
        values,
        patternOffsetIndex,
        getCurIndex(arrayOffset + fromIndex));
  }

  @Override
  public Column getRegionCopy(int positionOffset, int length) {
    throw new UnsupportedOperationException();

    // checkValidRegion(getPositionCount(), positionOffset, length);

    // int endPositionOffset = positionOffset + length - 1;
    // int startIndex = getCurIndex(positionOffset);
    // int endIndex = getCurIndex(endPositionOffset);

    // /*reconstruct the values  */
    // RLEPattern[] valuesTmp = new RLEPattern[values.length];
    // for (int i = 0; i < patternCount; i++) {
    //   valuesTmp[arrayOffset + i] = values[arrayOffset + i].deepCopy();
    // }
    // int subFromOffset = positionOffset - patternOffsetIndex[startIndex];
    // int subToOffset = endPositionOffset - patternOffsetIndex[endIndex];
    // int[] patternOffsetIndexTmp = Arrays.copyOf(patternOffsetIndex, patternOffsetIndex.length);

    // if (startIndex == endIndex) {
    //   valuesTmp[arrayOffset + endIndex].getRegion(subFromOffset, length);

    //   patternOffsetIndexTmp[arrayOffset + endIndex] = 0;
    //   patternOffsetIndexTmp[arrayOffset + endIndex + 1] = length;
    // } else {
    //   valuesTmp[arrayOffset + startIndex].subColumn(subFromOffset);
    //   valuesTmp[arrayOffset + endIndex].getRegion(0, subToOffset + 1);

    //   patternOffsetIndexTmp[arrayOffset + startIndex] = 0;
    //   for (int i = arrayOffset + startIndex + 1; i <= arrayOffset + endIndex; i++) {
    //     patternOffsetIndexTmp[i] = patternOffsetIndexTmp[i] - positionOffset;
    //   }
    //   patternOffsetIndexTmp[arrayOffset + endIndex + 1] = length;
    // }
    // return new RLEColumn(
    //     arrayOffset + startIndex,
    //     length,
    //     endIndex - startIndex + 1,
    //     valuesTmp,
    //     patternOffsetIndexTmp);

  }

  @Override
  public Column subColumnCopy(int fromIndex) {
    throw new UnsupportedOperationException();
    // if (fromIndex > positionCount) {
    //   throw new IllegalArgumentException("fromIndex is not valid");
    // }

    // int curIndex = getCurIndex(fromIndex);
    // int curOffset = patternOffsetIndex[curIndex];
    // int[] patternOffsetIndexTmp = Arrays.copyOf(patternOffsetIndex, patternOffsetIndex.length);

    // if (curOffset == fromIndex) {
    //   for (int i = arrayOffset + curIndex; i <= arrayOffset + patternCount; i++) {
    //     patternOffsetIndexTmp[i] = patternOffsetIndexTmp[i] - curOffset;
    //   }
    //   return new RLEColumn(
    //       arrayOffset + curIndex,
    //       positionCount - fromIndex,
    //       patternCount - curIndex,
    //       values,
    //       patternOffsetIndexTmp);
    // } else {
    //   /*reconstruct the values  */
    //   int subFromIndex = fromIndex - curOffset;
    //   RLEPattern[] valuesTmp = new RLEPattern[values.length];
    //   for (int i = 0; i < patternCount; i++) {
    //     valuesTmp[arrayOffset + i] = values[arrayOffset + i].deepCopy();
    //   }
    //   valuesTmp[arrayOffset + curIndex].subColumn(subFromIndex);

    //   patternOffsetIndexTmp[arrayOffset + curIndex] = 0;
    //   for (int i = arrayOffset + curIndex + 1; i <= arrayOffset + patternCount; i++) {
    //     patternOffsetIndexTmp[i] = patternOffsetIndexTmp[i] - fromIndex;
    //   }

    //   return new RLEColumn(
    //       arrayOffset + curIndex,
    //       positionCount - fromIndex,
    //       patternCount - curIndex,
    //       valuesTmp,
    //       patternOffsetIndexTmp);
    // }
  }

  @Override
  public void reverse() {
    int startIndex = getCurIndex(arrayOffset);
    int endIndex = getCurIndex(arrayOffset + positionCount - 1);
    int leftGap = arrayOffset - patternOffsetIndex[startIndex];
    int rightGap = patternOffsetIndex[endIndex + 1] - (arrayOffset + positionCount);

    for (int i = startIndex, j = endIndex; i < j; i++, j--) {
      Column valueTmp = values[i];
      values[i] = values[j];
      values[j] = valueTmp;
      values[i].reverse();
      values[j].reverse();
    }

    if ((startIndex + endIndex) % 2 == 0) {
      values[(startIndex + endIndex) / 2].reverse();
    }
    arrayOffset = arrayOffset - leftGap + rightGap;

    // reverse patternOffsetIndex
    patternOffsetIndex[startIndex] = 0;
    int[] patternOffsetIndexTmp = Arrays.copyOf(patternOffsetIndex, patternOffsetIndex.length);
    for (int i = startIndex + 1, j = endIndex; i <= endIndex; i++, j--) {
      patternOffsetIndex[i] = patternOffsetIndex[i - 1] + (patternOffsetIndexTmp[j+1] - patternOffsetIndexTmp[j]);
    }
  }

  @Override
  public int getInstanceSize() {
    return INSTANCE_SIZE;
  }

  // public int getLogicPositionCount(int index) {
  //   int startIndex = getCurIndex(arrayOffset);
  //   int endIndex = getCurIndex(arrayOffset + positionCount -1);

  //   if (index < 0 || startIndex + index > endIndex) {
  //     throw new IllegalArgumentException(
  //         "getLogicPositionCount index: "
  //             + index
  //             + " is illegal. Only "
  //             + (endIndex - startIndex + 1)
  //             + "valid patterns");
  //   }

  //   if(startIndex == endIndex){
  //     return positionCount;
  //   }else if(index == 0){
  //     return patternOffsetIndex[startIndex + 1] - arrayOffset;
  //   }else if(index == endIndex){
  //     return arrayOffset + positionCount - patternOffsetIndex[endIndex];
  //   }else{
  //     return patternOffsetIndex[startIndex + index + 1] - patternOffsetIndex[startIndex + index];
  //   }
  // }

  // public int getPatternCount() {
  //   return getCurIndex(arrayOffset + positionCount -1) - getCurIndex(arrayOffset) + 1;
  // }
}
