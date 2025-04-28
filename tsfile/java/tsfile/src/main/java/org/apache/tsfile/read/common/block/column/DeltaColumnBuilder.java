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
import org.apache.tsfile.block.column.ColumnBuilder;
import org.apache.tsfile.block.column.ColumnBuilderStatus;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.read.common.type.TypeEnum;
import org.apache.tsfile.utils.RamUsageEstimator;
import org.apache.tsfile.write.UnSupportedDataTypeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static java.lang.Math.max;
import static org.apache.tsfile.read.common.block.column.ColumnUtil.calculateBlockResetSize;

public class DeltaColumnBuilder implements ColumnBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(DeltaColumnBuilder.class);

  private static final int INSTANCE_SIZE =
      (int) RamUsageEstimator.shallowSizeOfInstance(DeltaColumn.class);

  private final ColumnBuilderStatus columnBuilderStatus;
  private boolean initialized;
  private final int initialEntryCount;

  private int positionCount;
  private int patternCount; // count of valid DeltaPatterns
  private TSDataType dataType;
  private boolean hasNonNullValue;
  // private int nullBuf;

  // it is assumed that patternOffsetIndex.length = values.length + 1
  private Column[] values = new Column[0];
  private int[] patternOffsetIndex =
      new int[] {
        0
      }; // patternOffsetIndex[i] refers to the offset of values[i].getObject(0) in all data.

  private long retainedSizeInBytes;

  public DeltaColumnBuilder(
      ColumnBuilderStatus columnBuilderStatus, int expectedEntries, TSDataType type) {
    this.columnBuilderStatus = columnBuilderStatus;
    this.initialEntryCount = max(expectedEntries, 1);
    this.dataType = type;
    updateDataSize();
  }

  public DeltaColumnBuilder(
      ColumnBuilderStatus columnBuilderStatus, int expectedEntries, TypeEnum type) {
    this.columnBuilderStatus = columnBuilderStatus;
    this.initialEntryCount = max(expectedEntries, 1);
    switch (type) {
      case INT32:
        this.dataType = TSDataType.INT32;
        break;
      case INT64:
        this.dataType = TSDataType.INT64;
        break;
      case FLOAT:
        this.dataType = TSDataType.FLOAT;
        break;
      case DOUBLE:
        this.dataType = TSDataType.DOUBLE;
        break;
      default:
        throw new UnSupportedDataTypeException("DeltaColumn builder do not support " + type);
    }
    updateDataSize();
  }

  @Override
  public ColumnBuilder write(Column column, int index) {
    throw new UnsupportedOperationException();
    // Object value = column.getObject(index);
    // appendSingleValue(value);
    // return this;

  }

  @Override
  public ColumnBuilder appendNull() {
    throw new UnsupportedOperationException();
    // appendSingleValue(null);
    // return this;
  }

  @Override
  public Column build() {
    if (!hasNonNullValue) {
      switch (getDataType()) {
        case INT32:
          return new RunLengthEncodedColumn(
              new IntColumn(0, 1, new boolean[] {true}, new int[1]), positionCount);
        case INT64:
          return new RunLengthEncodedColumn(
              new LongColumn(0, 1, new boolean[] {true}, new long[1]), positionCount);
        case FLOAT:
          return new RunLengthEncodedColumn(
              new FloatColumn(0, 1, new boolean[] {true}, new float[1]), positionCount);
        case DOUBLE:
          return new RunLengthEncodedColumn(
              new DoubleColumn(0, 1, new boolean[] {true}, new double[1]), positionCount);
        case BOOLEAN:
          return new RunLengthEncodedColumn(
              new BooleanColumn(0, 1, new boolean[] {true}, new boolean[1]), positionCount);
        default:
          throw new UnSupportedDataTypeException(
              "Unsupported DataType for DeltaColumn:" + getDataType());
      }
    }

    return new DeltaColumn(positionCount, values, patternOffsetIndex);
  }

  @Override
  public TSDataType getDataType() {
    return dataType;
  }

  @Override
  public long getRetainedSizeInBytes() {
    return retainedSizeInBytes;
  }

  @Override
  public ColumnBuilder newColumnBuilderLike(ColumnBuilderStatus columnBuilderStatus) {
    return new DeltaColumnBuilder(
        columnBuilderStatus, calculateBlockResetSize(positionCount), dataType);
  }

  public DeltaColumnBuilder writeDeltaPattern(Column value) {
    if (!value.getDataType().equals(dataType)) {
      throw new UnSupportedDataTypeException(
          " only " + dataType + " supported, but get " + value.getDataType());
    }

    if (values.length <= patternCount) {
      growCapacity();
    }

    int count = value.getPositionCount();
    values[patternCount] = value;
    patternOffsetIndex[patternCount + 1] = patternOffsetIndex[patternCount] + count;
    hasNonNullValue = true;
    patternCount++;
    positionCount += count;
    if (columnBuilderStatus != null) {
      columnBuilderStatus.addBytes((int) value.getRetainedSizeInBytes());
    }
    return this;
  }

  private void growCapacity() {
    int newSize;
    if (initialized) {
      newSize = ColumnUtil.calculateNewArraySize(values.length);
    } else {
      newSize = initialEntryCount;
      initialized = true;
    }

    patternOffsetIndex = Arrays.copyOf(patternOffsetIndex, newSize + 1);
    values = Arrays.copyOf(values, newSize);
    updateDataSize();
  }

  private void updateDataSize() {
    retainedSizeInBytes = INSTANCE_SIZE + RamUsageEstimator.sizeOf(patternOffsetIndex);
    for (int i = 0; i < patternCount; i++) {
      retainedSizeInBytes += values[i].getRetainedSizeInBytes();
    }
    if (columnBuilderStatus != null) {
      retainedSizeInBytes += ColumnBuilderStatus.INSTANCE_SIZE;
    }
  }
}
