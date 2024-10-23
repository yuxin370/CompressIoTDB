/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.queryengine.execution.aggregation;

import org.apache.tsfile.block.column.Column;
import org.apache.tsfile.block.column.ColumnBuilder;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.file.metadata.statistics.IntegerStatistics;
import org.apache.tsfile.file.metadata.statistics.Statistics;
import org.apache.tsfile.read.common.block.column.RLEColumn;
import org.apache.tsfile.utils.BitMap;
import org.apache.tsfile.utils.Pair;
import org.apache.tsfile.write.UnSupportedDataTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;

public class AvgAccumulator implements Accumulator {
  private static final Logger LOGGER = LoggerFactory.getLogger(AvgAccumulator.class);

  private final TSDataType seriesDataType;
  private long countValue;
  private double sumValue;
  private boolean initResult = false;

  public AvgAccumulator(TSDataType seriesDataType) {
    this.seriesDataType = seriesDataType;
  }

  @Override
  public void addInput(Column[] columns, BitMap bitMap) {
    switch (seriesDataType) {
      case INT32:
        addIntInput(columns, bitMap);
        return;
      case INT64:
        addLongInput(columns, bitMap);
        return;
      case FLOAT:
        addFloatInput(columns, bitMap);
        return;
      case DOUBLE:
        addDoubleInput(columns, bitMap);
        return;
      case TEXT:
      case BOOLEAN:
      default:
        throw new UnSupportedDataTypeException(
            String.format("Unsupported data type in aggregation AVG : %s", seriesDataType));
    }
  }

  // partialResult should be like: | countValue1 | sumValue1 |
  @Override
  public void addIntermediate(Column[] partialResult) {
    checkArgument(partialResult.length == 2, "partialResult of Avg should be 2");
    if (partialResult[0].isNull(0)) {
      return;
    }
    initResult = true;
    countValue += partialResult[0].getLong(0);
    sumValue += partialResult[1].getDouble(0);
    if (countValue == 0) {
      initResult = false;
    }
  }

  @Override
  public void removeIntermediate(Column[] input) {
    checkArgument(input.length == 2, "partialResult of Avg should be 2");
    if (input[0].isNull(0)) {
      return;
    }
    countValue -= input[0].getLong(0);
    sumValue -= input[1].getDouble(0);
    if (countValue == 0) {
      initResult = false;
    }
  }

  @Override
  public void addStatistics(Statistics statistics) {
    if (statistics == null) {
      return;
    }
    initResult = true;
    countValue += statistics.getCount();
    if (statistics instanceof IntegerStatistics) {
      sumValue += statistics.getSumLongValue();
    } else {
      sumValue += statistics.getSumDoubleValue();
    }
    if (countValue == 0) {
      initResult = false;
    }
  }

  // Set sumValue to finalResult and keep countValue equals to 1
  @Override
  public void setFinal(Column finalResult) {
    reset();
    if (finalResult.isNull(0)) {
      return;
    }
    initResult = true;
    countValue = 1;
    sumValue = finalResult.getDouble(0);
  }

  @Override
  public void outputIntermediate(ColumnBuilder[] columnBuilders) {
    checkArgument(columnBuilders.length == 2, "partialResult of Avg should be 2");
    if (!initResult) {
      columnBuilders[0].appendNull();
      columnBuilders[1].appendNull();
    } else {
      columnBuilders[0].writeLong(countValue);
      columnBuilders[1].writeDouble(sumValue);
    }
  }

  @Override
  public void outputFinal(ColumnBuilder columnBuilder) {
    if (!initResult) {
      columnBuilder.appendNull();
    } else {
      columnBuilder.writeDouble(sumValue / countValue);
    }
  }

  @Override
  public void reset() {
    initResult = false;
    this.countValue = 0;
    this.sumValue = 0.0;
  }

  @Override
  public boolean hasFinalResult() {
    return false;
  }

  @Override
  public TSDataType[] getIntermediateType() {
    return new TSDataType[] {TSDataType.INT64, TSDataType.DOUBLE};
  }

  @Override
  public TSDataType getFinalType() {
    return TSDataType.DOUBLE;
  }

  @Override
  public int getPartialResultSize() {
    return 2;
  }

  private void addIntInput(Column[] column, BitMap bitMap) {
    int count = column[0].getPositionCount();
    if (column[1] instanceof RLEColumn) {
      Pair<Column[], int[]> patterns = ((RLEColumn) column[1]).getVisibleColumns();
      int curIndex = 0, i = 0;
      while (curIndex < count) {
        Column curPattern = patterns.getLeft()[i];
        int curPatternLength = patterns.getRight()[i];
        curPatternLength =
            curIndex + curPatternLength - 1 <= count ? curPatternLength : count - curIndex + 1;
        if (curPattern.getPositionCount() == 1) {
          int validCount = 0;
          if ((bitMap == null || bitMap.getRegion(curIndex, curPatternLength).isAllMarked())) {
            initResult = true;
            validCount += curPatternLength;
            curIndex += curPatternLength;
          } else {
            for (int j = 0; j < curPatternLength; j++, curIndex++) {
              if (!bitMap.isMarked(curIndex)) {
                continue;
              }
              initResult = true;
              validCount++;
            }
          }
          countValue += validCount;
          sumValue += curPattern.getInt(0) * validCount;
        } else {
          for (int j = 0; j < curPatternLength; j++, curIndex++) {
            if (bitMap != null && !bitMap.isMarked(curIndex)) {
              continue;
            }
            if (!curPattern.isNull(j)) {
              initResult = true;
              countValue++;
              sumValue += curPattern.getInt(j);
            }
          }
        }
        i++;
      }
      return;
    }
    for (int i = 0; i < count; i++) {
      if (bitMap != null && !bitMap.isMarked(i)) {
        continue;
      }
      if (!column[1].isNull(i)) {
        initResult = true;
        countValue++;
        sumValue += column[1].getInt(i);
      }
    }
  }

  private void addLongInput(Column[] column, BitMap bitMap) {
    int count = column[0].getPositionCount();
    if (column[1] instanceof RLEColumn) {
      Pair<Column[], int[]> patterns = ((RLEColumn) column[1]).getVisibleColumns();
      int curIndex = 0, i = 0;
      while (curIndex < count) {
        Column curPattern = patterns.getLeft()[i];
        int curPatternLength = patterns.getRight()[i];
        curPatternLength =
            curIndex + curPatternLength - 1 <= count ? curPatternLength : count - curIndex + 1;

        if (curPattern.getPositionCount() == 1) {
          int validCount = 0;
          if (bitMap == null || bitMap.getRegion(curIndex, curPatternLength).isAllMarked()) {
            initResult = true;
            validCount += curPatternLength;
            curIndex += curPatternLength;
          } else {
            for (int j = 0; j < curPatternLength; j++, curIndex++) {
              if (!bitMap.isMarked(curIndex)) {
                continue;
              }
              initResult = true;
              validCount++;
            }
          }
          countValue += validCount;
          sumValue += curPattern.getLong(0) * validCount;
        } else {
          for (int j = 0; j < curPatternLength; j++, curIndex++) {
            if (bitMap != null && !bitMap.isMarked(curIndex)) {
              continue;
            }
            if (!curPattern.isNull(j)) {
              initResult = true;
              countValue++;
              sumValue += curPattern.getLong(j);
            }
          }
        }
        i++;
      }
      return;
    }
    for (int i = 0; i < count; i++) {
      if (bitMap != null && !bitMap.isMarked(i)) {
        continue;
      }
      if (!column[1].isNull(i)) {
        initResult = true;
        countValue++;
        sumValue += column[1].getLong(i);
      }
    }
  }

  private void addFloatInput(Column[] column, BitMap bitMap) {
    int count = column[0].getPositionCount();
    if (column[1] instanceof RLEColumn) {
      Pair<Column[], int[]> patterns = ((RLEColumn) column[1]).getVisibleColumns();
      int curIndex = 0, i = 0;
      while (curIndex < count) {
        Column curPattern = patterns.getLeft()[i];
        int curPatternLength = patterns.getRight()[i];
        curPatternLength =
            curIndex + curPatternLength - 1 <= count ? curPatternLength : count - curIndex + 1;
        if (curPattern.getPositionCount() == 1) {
          int validCount = 0;
          if (bitMap == null || bitMap.getRegion(curIndex, curPatternLength).isAllMarked()) {
            initResult = true;
            validCount += curPatternLength;
            curIndex += curPatternLength;
          } else {
            for (int j = 0; j < curPatternLength; j++, curIndex++) {
              if (!bitMap.isMarked(curIndex)) {
                continue;
              }
              initResult = true;
              validCount++;
            }
          }
          countValue += validCount;
          sumValue += curPattern.getFloat(0) * validCount;
        } else {
          for (int j = 0; j < curPatternLength; j++, curIndex++) {
            if (bitMap != null && !bitMap.isMarked(curIndex)) {
              continue;
            }
            if (!curPattern.isNull(j)) {
              initResult = true;
              countValue++;
              sumValue += curPattern.getFloat(j);
            }
          }
        }
        i++;
      }
      return;
    }
    for (int i = 0; i < count; i++) {
      if (bitMap != null && !bitMap.isMarked(i)) {
        continue;
      }
      if (!column[1].isNull(i)) {
        initResult = true;
        countValue++;
        sumValue += column[1].getFloat(i);
      }
    }
  }

  private void addDoubleInput(Column[] column, BitMap bitMap) {
    int count = column[0].getPositionCount();
    if (column[1] instanceof RLEColumn) {
      Pair<Column[], int[]> patterns = ((RLEColumn) column[1]).getVisibleColumns();
      int curIndex = 0, i = 0;
      while (curIndex < count) {
        Column curPattern = patterns.getLeft()[i];
        int curPatternLength = patterns.getRight()[i];
        curPatternLength =
            curIndex + curPatternLength - 1 <= count ? curPatternLength : count - curIndex + 1;
        if (curPattern.getPositionCount() == 1) {
          int validCount = 0;
          if (bitMap == null || bitMap.getRegion(curIndex, curPatternLength).isAllMarked()) {
            initResult = true;
            validCount += curPatternLength;
            curIndex += curPatternLength;
          } else {
            for (int j = 0; j < curPatternLength; j++, curIndex++) {
              if (!bitMap.isMarked(curIndex)) {
                continue;
              }
              initResult = true;
              validCount++;
            }
          }
          countValue += validCount;
          sumValue += curPattern.getDouble(0) * validCount;
        } else {
          for (int j = 0; j < curPatternLength; j++, curIndex++) {
            if (bitMap != null && !bitMap.isMarked(curIndex)) {
              continue;
            }
            if (!curPattern.isNull(j)) {
              initResult = true;
              countValue++;
              sumValue += curPattern.getDouble(j);
            }
          }
        }
        i++;
      }
      return;
    }
    for (int i = 0; i < count; i++) {
      if (bitMap != null && !bitMap.isMarked(i)) {
        continue;
      }
      if (!column[1].isNull(i)) {
        initResult = true;
        countValue++;
        sumValue += column[1].getDouble(i);
      }
    }
  }
}
