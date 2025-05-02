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
 import org.apache.tsfile.utils.RamUsageEstimator;
 import org.apache.tsfile.utils.TsPrimitiveType;
 
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 
 import java.util.Arrays;
 import java.util.Optional;
 
 import static java.util.Objects.requireNonNull;
 import static org.apache.tsfile.read.common.block.column.ColumnUtil.checkValidRegion;
 import static org.apache.tsfile.utils.RamUsageEstimator.sizeOfIntArray;
 
 public class DeltaColumn implements Column {
   private static final Logger LOGGER = LoggerFactory.getLogger(DeltaColumn.class);
 
   private static final int INSTANCE_SIZE =
       (int) RamUsageEstimator.shallowSizeOfInstance(Column.class);
 
   private int arrayOffset;
   private final int positionCount;
   private final Column[] values;
   // patternOffsetIndex[i] refers to the offset of values[i].getObject(0) in all data
   private final int[] patternOffsetIndex;
   // Marking the latest read column index, which can effectively save traversal time when data is
   // continuously read.
   private int hintIndex;
 
   public DeltaColumn(int positionCount, Column[] values, int[] patternOffsetIndex) {
     this(0, positionCount, values, patternOffsetIndex, 0);
   }
 
   public DeltaColumn(
       int arrayOffset, int positionCount, Column[] values, int[] patternOffsetIndex) {
     this(arrayOffset, positionCount, values, patternOffsetIndex, 0);
   }
 
   DeltaColumn(
       int arrayOffset,
       int positionCount,
       Column[] values,
       int[] patternOffsetIndex,
       int hintIndex) {
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
     this.hintIndex = hintIndex;
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
       for (index = hintIndex;
           index < patternOffsetIndex.length && patternOffsetIndex[index] == 0;
           index++) ;
       hintIndex = index - 1;
       return hintIndex;
     }
     int index;
     if (position >= patternOffsetIndex[hintIndex]) {
       /** check if hintIndex hit */
       if (position < patternOffsetIndex[hintIndex + 1]) {
         /** hit */
         return hintIndex;
       } else {
         /** miss, traverse from hintIndex + 1 and update hintIndex */
         for (index = hintIndex + 1;
             index < values.length && position >= patternOffsetIndex[index];
             index++) ;
         hintIndex = index - 1;
         return hintIndex;
       }
     }
 
     /** miss, traverse from scratch and reset hintIndex */
     for (index = 0;
         index < patternOffsetIndex.length && position >= patternOffsetIndex[index];
         index++) ;
     hintIndex = index - 1;
     return hintIndex;
   }
 
   @Override
   public TSDataType getDataType() {
     return (values[0]).getDataType();
   }
 
   @Override
   public boolean getBoolean(int position) {
     throw new UnsupportedOperationException();
   }
 
   @Override
   public int getInt(int position) {
     /* only decompress the target section */
     int curIndex = getCurIndex(arrayOffset + position);
     int res = 0;
     for (int i = 0, offset = position - patternOffsetIndex[curIndex]; i < offset; i++) {
       res += values[curIndex].getInt(i);
     }
     return res;
   }
 
   @Override
   public long getLong(int position) {
     /* only decompress the target section */
     int curIndex = getCurIndex(arrayOffset + position);
     long res = 0;
     for (int i = 0, offset = position - patternOffsetIndex[curIndex]; i < offset; i++) {
       res += values[curIndex].getLong(i);
     }
     return res;
   }
 
   @Override
   public float getFloat(int position) {
     /* only decompress the target section */
     int curIndex = getCurIndex(arrayOffset + position);
     float res = 0;
     for (int i = 0, offset = position - patternOffsetIndex[curIndex]; i < offset; i++) {
       res += values[curIndex].getFloat(i);
     }
     return res;
   }
 
   @Override
   public double getDouble(int position) {
     /* only decompress the target section */
     int curIndex = getCurIndex(arrayOffset + position);
     double res = 0;
     for (int i = 0, offset = position - patternOffsetIndex[curIndex]; i < offset; i++) {
       res += values[curIndex].getDouble(i);
     }
     return res;
   }
 
   @Override
   public Binary getBinary(int position) {
     throw new UnsupportedOperationException();
   }
 
   public static Column subDeltaPattern(Column pattern, int arrayOffset) {
     if (pattern instanceof IntColumn) {
       int[] datas = ((IntColumn) pattern).getInts();
       if (datas == null || datas.length == 0 || arrayOffset >= datas.length) {
         return new IntColumn(0, Optional.empty(), new int[0]);
       }
       int cum = datas[0];
       for (int i = 1; i <= arrayOffset; i++) {
         cum += datas[i];
       }
       int newLen = datas.length - arrayOffset;
       int[] newDatas = new int[newLen];
       newDatas[0] = cum;
       for (int i = arrayOffset + 1; i < datas.length; i++) {
         newDatas[i - arrayOffset] = datas[i];
       }
       return new IntColumn(newDatas.length, Optional.empty(), newDatas);
 
     } else if (pattern instanceof LongColumn) {
       long[] datas = ((LongColumn) pattern).getLongs();
       if (datas == null || datas.length == 0 || arrayOffset >= datas.length) {
         return new LongColumn(0, Optional.empty(), new long[0]);
       }
       long cum = datas[0];
       for (int i = 1; i <= arrayOffset; i++) {
         cum += datas[i];
       }
       int newLen = datas.length - arrayOffset;
       long[] newDatas = new long[newLen];
       newDatas[0] = cum;
       for (int i = arrayOffset + 1; i < datas.length; i++) {
         newDatas[i - arrayOffset] = datas[i];
       }
       return new LongColumn(newDatas.length, Optional.empty(), newDatas);
 
     } else if (pattern instanceof DoubleColumn) {
       double[] datas = ((DoubleColumn) pattern).getDoubles();
       if (datas == null || datas.length == 0 || arrayOffset >= datas.length) {
         return new DoubleColumn(0, Optional.empty(), new double[0]);
       }
       double cum = datas[0];
       for (int i = 1; i <= arrayOffset; i++) {
         cum += datas[i];
       }
       int newLen = datas.length - arrayOffset;
       double[] newDatas = new double[newLen];
       newDatas[0] = cum;
       for (int i = arrayOffset + 1; i < datas.length; i++) {
         newDatas[i - arrayOffset] = datas[i];
       }
       return new DoubleColumn(newDatas.length, Optional.empty(), newDatas);
 
     } else if (pattern instanceof FloatColumn) {
       float[] datas = ((FloatColumn) pattern).getFloats();
       if (datas == null || datas.length == 0 || arrayOffset >= datas.length) {
         return new FloatColumn(0, Optional.empty(), new float[0]);
       }
       float cum = datas[0];
       for (int i = 1; i <= arrayOffset; i++) {
         cum += datas[i];
       }
       int newLen = datas.length - arrayOffset;
       float[] newDatas = new float[newLen];
       newDatas[0] = cum;
       for (int i = arrayOffset + 1; i < datas.length; i++) {
         newDatas[i - arrayOffset] = datas[i];
       }
       return new FloatColumn(newDatas.length, Optional.empty(), newDatas);
 
     } else {
       throw new IllegalArgumentException("Unsupported column type: " + pattern.getClass());
     }
   }
 
   public Column[] getVisibleColumns() {
     int startIndex = getCurIndex(arrayOffset);
     int endIndex = getCurIndex(arrayOffset + positionCount - 1);
     Column[] visibleColumns = Arrays.copyOfRange(values, startIndex, endIndex + 1);
     if (visibleColumns[endIndex - startIndex].getPositionCount() > 1) {
       visibleColumns[endIndex - startIndex] =
           visibleColumns[endIndex - startIndex].getRegion(
               0, arrayOffset + positionCount - patternOffsetIndex[endIndex]);
     }
     if (visibleColumns[0].getPositionCount() > 1) {
       visibleColumns[0] =
           subDeltaPattern(visibleColumns[0], arrayOffset - patternOffsetIndex[startIndex]);
       // visibleColumns[0] = visibleColumns[0].subColumn(arrayOffset -
       // patternOffsetIndex[startIndex]);
     }
 
     return visibleColumns;
   }
 
   @Override
   public Object getObject(int position) {
     throw new UnsupportedOperationException();
   }
 
   @Override
   public boolean[] getBooleans() {
     throw new UnsupportedOperationException();
   }
 
   @Override
   public int[] getInts() {
     // Total length of reconstructed array
     int totalLength = patternOffsetIndex[values.length];
     int[] res = new int[totalLength];
 
     for (int i = 0; i < values.length; i++) {
       // Each segment is delta-encoded: [base, d1, d2, ...]
       IntColumn segment = (IntColumn) values[i];
       int segmentCount = segment.getPositionCount();
       if (segmentCount == 0) {
         continue;
       }
 
       // Decode deltas
       int[] deltas = segment.getInts();
       int cum = deltas[0];
       int startIndex = patternOffsetIndex[i];
 
       // First decoded value
       res[startIndex] = cum;
       // Subsequent decoded values
       for (int j = 1; j < segmentCount; j++) {
         cum += deltas[j];
         res[startIndex + j] = cum;
       }
     }
 
     return res;
   }
 
   @Override
   public long[] getLongs() {
     long[] res = new long[patternOffsetIndex[values.length]];
     for (int i = 0; i < values.length; i++) {
       // Each segment is delta-encoded: [base, d1, d2, ...]
       LongColumn segment = (LongColumn) values[i];
       int segmentCount = segment.getPositionCount();
       if (segmentCount == 0) {
         continue;
       }
 
       // Decode deltas
       long[] deltas = segment.getLongs();
       long cum = deltas[0];
       int startIndex = patternOffsetIndex[i];
 
       // First decoded value
       res[startIndex] = cum;
       // Subsequent decoded values
       for (int j = 1; j < segmentCount; j++) {
         cum += deltas[j];
         res[startIndex + j] = cum;
       }
     }
     return res;
   }
 
   @Override
   public float[] getFloats() {
     int totalLength = patternOffsetIndex[values.length];
     float[] res = new float[totalLength];
     for (int i = 0; i < values.length; i++) {
       FloatColumn segment = (FloatColumn) values[i];
       int count = segment.getPositionCount();
       if (count == 0) continue;
       float[] deltas = segment.getFloats();
       float cum = deltas[0];
       int start = patternOffsetIndex[i];
       res[start] = cum;
       for (int j = 1; j < count; j++) {
         cum += deltas[j];
         res[start + j] = cum;
       }
     }
     return res;
   }
 
   @Override
   public double[] getDoubles() {
     int totalLength = patternOffsetIndex[values.length];
     double[] res = new double[totalLength];
     for (int i = 0; i < values.length; i++) {
       DoubleColumn segment = (DoubleColumn) values[i];
       int count = segment.getPositionCount();
       if (count == 0) continue;
       double[] deltas = segment.getDoubles();
       double cum = deltas[0];
       int start = patternOffsetIndex[i];
       res[start] = cum;
       for (int j = 1; j < count; j++) {
         cum += deltas[j];
         res[start + j] = cum;
       }
     }
     return res;
   }
 
   @Override
   public Binary[] getBinaries() {
     throw new UnsupportedOperationException();
   }
 
   @Override
   public Object[] getObjects() {
     throw new UnsupportedOperationException();
   }
 
   @Override
   public ColumnEncoding getEncoding() {
     return ColumnEncoding.DELTA_ARRAY;
   }
 
   @Override
   public TsPrimitiveType getTsPrimitiveType(int position) {
     int curIndex = getCurIndex(arrayOffset + position);
     return values[curIndex].getTsPrimitiveType(
         arrayOffset + position - patternOffsetIndex[curIndex] + 1);
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
     return values[curIndex].isNull(arrayOffset + position - patternOffsetIndex[curIndex] + 1);
   }
 
   @Override
   public boolean[] isNull() {
     boolean[] res = new boolean[patternOffsetIndex[values.length]];
     for (int i = 0; i < values.length; i++) {
       int startIndex = patternOffsetIndex[i];
       boolean[] tp = values[i].isNull();
       System.arraycopy(tp, 0, res, startIndex, tp.length);
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
     return new DeltaColumn(
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
     return new DeltaColumn(
         arrayOffset + fromIndex,
         positionCount - fromIndex,
         values,
         patternOffsetIndex,
         getCurIndex(arrayOffset + fromIndex));
   }
 
   @Override
   public Column getRegionCopy(int positionOffset, int length) {
     throw new UnsupportedOperationException();
   }
 
   @Override
   public Column subColumnCopy(int fromIndex) {
     throw new UnsupportedOperationException();
   }
 
   @Override
   public void reverse() {
     throw new UnsupportedOperationException();
   }
 
   @Override
   public int getInstanceSize() {
     return INSTANCE_SIZE;
   }
 }
 