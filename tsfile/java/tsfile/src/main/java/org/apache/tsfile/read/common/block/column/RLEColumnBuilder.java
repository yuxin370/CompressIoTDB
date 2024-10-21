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
import java.util.Collections;

import static java.lang.Math.max;
import static org.apache.tsfile.read.common.block.TsBlockUtil.contructColumnBuilders;
import static org.apache.tsfile.read.common.block.column.ColumnUtil.calculateBlockResetSize;

public class RLEColumnBuilder implements ColumnBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(RLEColumnBuilder.class);

  private static final int INSTANCE_SIZE =
      (int) RamUsageEstimator.shallowSizeOfInstance(RLEColumn.class);

  private final ColumnBuilderStatus columnBuilderStatus;
  private boolean initialized;
  private final int initialEntryCount;

  private int positionCount;
  private int patternCount; // count of valid RlePatterns
  private TSDataType dataType;
  private boolean hasNonNullValue;
  // private int nullBuf;

  // variables for dynamic encoding
  private ColumnBuilder valueBuf;
  // private int valueCount; // logic value count
  private int bufCount; // physical value count
  private int
      repeatCount; // repeat value count int the end of value buf (only one physical value wrote
  // into buf)
  private Object lastValue; // last value in buf

  // it is assumed that patternOffsetIndex.length = values.length + 1
  private Column[] values = new Column[0];
  private int[] patternOffsetIndex =
      new int[] {
        0
      }; // patternOffsetIndex[i] refers to the offset of values[i].getObject(0) in all data.

  private long retainedSizeInBytes;

  public RLEColumnBuilder(
      ColumnBuilderStatus columnBuilderStatus, int expectedEntries, TSDataType type) {
    this.columnBuilderStatus = columnBuilderStatus;
    this.initialEntryCount = max(expectedEntries, 1);
    this.dataType = type;
    this.valueBuf = contructColumnBuilders(Collections.singletonList(dataType))[0];
    this.repeatCount = 0;
    this.bufCount = 0;
    updateDataSize();
  }

  public RLEColumnBuilder(
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
      case BOOLEAN:
        this.dataType = TSDataType.BOOLEAN;
        break;
      case BINARY:
        this.dataType = TSDataType.TEXT;
        break;
      default:
        throw new UnSupportedDataTypeException("RLEColumn builder do not support " + type);
    }
    updateDataSize();
  }

  public RLEColumnBuilder writeRLEPattern(Column value, int logicPositionCount) {
    if (!value.getDataType().equals(dataType)) {
      throw new UnSupportedDataTypeException(
          " only " + dataType + " supported, but get " + value.getDataType());
    }

    if (values.length <= patternCount) {
      growCapacity();
    }

    if (bufCount != 0) {
      writeBufValue();
    }

    values[patternCount] = value;
    patternOffsetIndex[patternCount + 1] = patternOffsetIndex[patternCount] + logicPositionCount;
    hasNonNullValue = true;
    patternCount++;
    positionCount += logicPositionCount;
    if (columnBuilderStatus != null) {
      columnBuilderStatus.addBytes((int) value.getRetainedSizeInBytes());
    }
    return this;
  }

  @Override
  public ColumnBuilder write(Column column, int index) {
    Object value = column.getObject(index);
    appendSingleValue(value);
    return this;
  }

  @Override
  public ColumnBuilder appendNull() {
    appendSingleValue(null);
    return this;
  }

  @Override
  public Column build() {

    if (bufCount != 0) {
      writeBufValue();
    }

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
              "Unsupported DataType for RLEColumn:" + getDataType());
      }
    }

    return new RLEColumn(positionCount, values, patternOffsetIndex);
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
    return new RLEColumnBuilder(
        columnBuilderStatus, calculateBlockResetSize(positionCount), dataType);
  }

  private void writeBufValue() {
    // valueBuf is not null, push buf value to builder
    if (repeatCount > 8) {
      // write bit-packed column and run-length column separatly
      if (bufCount > 1) {
        writeRLEPatternWithoutCheckBuf(valueBuf.build(), bufCount);
      }
      // there are repeat values.
      ColumnBuilder tpColumn = contructColumnBuilders(Collections.singletonList(dataType))[0];
      if (lastValue == null) {
        tpColumn.appendNull();
      } else {
        tpColumn.writeObject(lastValue);
      }
      writeRLEPatternWithoutCheckBuf(tpColumn.build(), repeatCount - (bufCount > 1 ? 1 : 0));
    } else if (repeatCount > 1) {
      for (int i = 1; i < repeatCount; i++) {
        // pack repeat values into bit-packed values
        if (lastValue == null) {
          valueBuf.appendNull();
        } else {
          valueBuf.writeObject(lastValue);
        }
      }
      writeRLEPatternWithoutCheckBuf(valueBuf.build(), bufCount + repeatCount - 1);
    } else {
      writeRLEPatternWithoutCheckBuf(valueBuf.build(), bufCount);
    }
    valueBuf = valueBuf.newColumnBuilderLike(null);
    bufCount = 0;
    repeatCount = 0;
  }

  private void appendSingleValue(Object value) {
    if (bufCount == 0) {
      // empty buf, just write it.
      if (value == null) {
        valueBuf.appendNull();
      } else {
        valueBuf.writeObject(value);
      }
      bufCount = 1;
      repeatCount = 1;
      lastValue = value;
      return;
    }
    if ((lastValue != null && lastValue.equals(value)) || (lastValue == null && value == null)) {
      // get repeat value
      repeatCount += 1;
    } else {
      // get different value
      if (repeatCount < 8) {
        // degenerate into bitpacked mode
        for (int i = 1; i < repeatCount; i++) {
          if (lastValue == null) {
            valueBuf.appendNull();
          } else {
            valueBuf.writeObject(lastValue);
          }
        }
        bufCount += repeatCount;
      } else {
        // save current column and construct a new empty columnbuilder for the following values
        if (bufCount > 1) {
          writeRLEPatternWithoutCheckBuf(valueBuf.build(), bufCount);
        }
        if (repeatCount > 1) {
          // there are repeat values.
          ColumnBuilder tpColumn = contructColumnBuilders(Collections.singletonList(dataType))[0];
          if (lastValue == null) {
            tpColumn.appendNull();
          } else {
            tpColumn.writeObject(lastValue);
          }
          writeRLEPatternWithoutCheckBuf(tpColumn.build(), repeatCount - (bufCount > 1 ? 1 : 0));
        }
        valueBuf = valueBuf.newColumnBuilderLike(null);
        bufCount = 1;
      }
      if (value == null) {
        valueBuf.appendNull();
      } else {
        valueBuf.writeObject(value);
      }
      repeatCount = 1;
      lastValue = value;
    }
  }

  private RLEColumnBuilder writeRLEPatternWithoutCheckBuf(Column value, int logicPositionCount) {
    if (!value.getDataType().equals(dataType)) {
      throw new UnSupportedDataTypeException(
          " only " + dataType + " supported, but get " + value.getDataType());
    }

    if (values.length <= patternCount) {
      growCapacity();
    }

    values[patternCount] = value;
    patternOffsetIndex[patternCount + 1] = patternOffsetIndex[patternCount] + logicPositionCount;
    hasNonNullValue = true;
    patternCount++;
    positionCount += logicPositionCount;
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
