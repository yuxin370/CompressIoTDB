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

package org.apache.iotdb.db.queryengine.transformation.dag.column.unary;

import org.apache.iotdb.db.queryengine.transformation.dag.column.ColumnTransformer;

import org.apache.tsfile.block.column.Column;
import org.apache.tsfile.block.column.ColumnBuilder;
import org.apache.tsfile.common.conf.TSFileConfig;
import org.apache.tsfile.read.common.block.column.RLEColumn;
import org.apache.tsfile.read.common.block.column.RLEColumnBuilder;
import org.apache.tsfile.read.common.type.Type;
import org.apache.tsfile.read.common.type.TypeEnum;
import org.apache.tsfile.utils.Pair;

import java.util.regex.Pattern;

public class RegularColumnTransformer extends UnaryColumnTransformer {
  private final Pattern pattern;

  public RegularColumnTransformer(
      Type returnType, ColumnTransformer childColumnTransformer, Pattern pattern) {
    super(returnType, childColumnTransformer);
    this.pattern = pattern;
  }

  @Override
  protected void doTransform(Column column, ColumnBuilder columnBuilder) {
    if (column instanceof RLEColumn) {
      Pair<Column[], int[]> rlePatterns = ((RLEColumn) column).getVisibleColumns();
      Column[] columns = rlePatterns.getLeft();
      int[] logicPositionCounts = rlePatterns.getRight();
      int patternCount = columns.length;

      for (int i = 0, n = patternCount; i < n; i++) {
        if (columns[i].getPositionCount() == 1) {
          ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(1);
          if (!columns[i].isNull(0)) {
            returnType.writeBoolean(
                columnBuilderTmp,
                pattern
                    .matcher(
                        childColumnTransformer
                            .getType()
                            .getBinary(columns[i], 0)
                            .getStringValue(TSFileConfig.STRING_CHARSET))
                    .find());
          } else {
            columnBuilderTmp.appendNull();
          }
          ((RLEColumnBuilder) columnBuilder)
              .writeRLEPattern(columnBuilderTmp.build(), logicPositionCounts[i]);
        } else {
          ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(logicPositionCounts[i]);
          for (int j = 0; j < logicPositionCounts[i]; j++) {
            if (!columns[i].isNull(j)) {
              returnType.writeBoolean(
                  columnBuilderTmp,
                  pattern
                      .matcher(
                          childColumnTransformer
                              .getType()
                              .getBinary(columns[i], j)
                              .getStringValue(TSFileConfig.STRING_CHARSET))
                      .find());
            } else {
              columnBuilderTmp.appendNull();
            }
          }
          ((RLEColumnBuilder) columnBuilder)
              .writeRLEPattern(columnBuilderTmp.build(), logicPositionCounts[i]);
        }
      }
      return;
    }
    for (int i = 0, n = column.getPositionCount(); i < n; i++) {
      if (!column.isNull(i)) {
        returnType.writeBoolean(
            columnBuilder,
            pattern
                .matcher(
                    childColumnTransformer
                        .getType()
                        .getBinary(column, i)
                        .getStringValue(TSFileConfig.STRING_CHARSET))
                .find());
      } else {
        columnBuilder.appendNull();
      }
    }
  }

  @Override
  protected void checkType() {
    if (!childColumnTransformer.typeEquals(TypeEnum.BINARY)) {
      throw new UnsupportedOperationException(
          "Unsupported Type: " + childColumnTransformer.getType().getTypeEnum());
    }
  }
}
