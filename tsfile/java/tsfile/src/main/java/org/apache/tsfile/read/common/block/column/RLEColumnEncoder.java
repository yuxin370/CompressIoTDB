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
import org.apache.tsfile.utils.Pair;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class RLEColumnEncoder implements ColumnEncoder {

  @Override
  public Column readColumn(ByteBuffer input, TSDataType dataType, int positionCount) {
    // Serialized data layout:
    //
    // +---------+--------------+-------------+------------------------+-------------------------+
    // |encoding |pattern count |offset index |physical positionCounts |serialized inner columns |
    // +---------+--------------+-------------+------------------------+-------------------------+
    // |byte     |int           |list[int]    |list[int]               |list[bytes]              |
    // +---------+--------------+-------------+------------------------+-------------------------+
    ColumnEncoder columnEncoder = ColumnEncoderFactory.get(ColumnEncoding.deserializeFrom(input));
    int patternCount = input.getInt();
    int[] patternOffsetIndex = new int[patternCount + 1];
    int[] physicalPositionCount = new int[patternCount];
    patternOffsetIndex[0] = 0;
    for (int i = 1; i <= patternCount; i++) {
      patternOffsetIndex[i] = input.getInt();
    }
    for (int i = 0; i < patternCount; i++) {
      physicalPositionCount[i] = input.getInt();
    }

    Column[] values = new Column[patternCount];
    for (int i = 0; i < patternCount; i++) {
      values[i] = columnEncoder.readColumn(input, dataType, physicalPositionCount[i]);
    }

    return new RLEColumn(positionCount, values, patternOffsetIndex);
  }

  @Override
  public void writeColumn(DataOutputStream output, Column column) throws IOException {
    if (!(column instanceof RLEColumn)) {
      throw new IllegalArgumentException("Unable to write column that not a RLEColumn");
    }

    Pair<Column[], int[]> rlePatterns = ((RLEColumn) column).getVisibleColumns();
    Column[] columns = rlePatterns.getLeft();
    int[] logicPositionCounts = rlePatterns.getRight();
    int patternCount = columns.length;

    // serialize encoding
    columns[0].getEncoding().serializeTo(output);

    // serialize patternCount
    output.writeInt(patternCount);

    // reconstruct and serialize patternOffsetIndex
    int curOffset = 0;
    for (int i = 0; i < patternCount; i++) {
      curOffset += logicPositionCounts[i];
      output.writeInt(curOffset);
    }

    // serialize physical positioncount
    for (int i = 0; i < patternCount; i++) {
      output.writeInt(columns[i].getPositionCount());
    }

    // serialize inner columns
    ColumnEncoder columnEncoder = ColumnEncoderFactory.get(columns[0].getEncoding());
    for (int i = 0; i < patternCount; i++) {
      columnEncoder.writeColumn(output, columns[i]);
    }
  }
}
