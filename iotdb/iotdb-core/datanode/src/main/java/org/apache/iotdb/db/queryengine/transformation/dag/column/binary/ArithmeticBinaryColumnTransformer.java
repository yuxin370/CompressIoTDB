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

package org.apache.iotdb.db.queryengine.transformation.dag.column.binary;

import org.apache.iotdb.db.queryengine.transformation.dag.column.ColumnTransformer;

import org.apache.tsfile.block.column.Column;
import org.apache.tsfile.block.column.ColumnBuilder;
import org.apache.tsfile.read.common.block.column.DeltaColumn;
import org.apache.tsfile.read.common.block.column.DeltaColumnBuilder;
import org.apache.tsfile.read.common.block.column.RLEColumn;
import org.apache.tsfile.read.common.block.column.RunLengthEncodedColumn;
import org.apache.tsfile.read.common.type.Type;
import org.apache.tsfile.utils.Pair;

import java.util.Collections;

import static org.apache.tsfile.read.common.block.TsBlockUtil.contructColumnBuilders;

public abstract class ArithmeticBinaryColumnTransformer extends BinaryColumnTransformer {
  protected ArithmeticBinaryColumnTransformer(
      Type returnType, ColumnTransformer leftTransformer, ColumnTransformer rightTransformer) {
    super(returnType, leftTransformer, rightTransformer);
  }

  private void doTransformBetweenRLE(
      Column leftColumn, Column rightColumn, ColumnBuilder builder, int positionCount) {
    Pair<Column[], int[]> leftPatterns = ((RLEColumn) leftColumn).getVisibleColumns();
    Pair<Column[], int[]> rightPatterns = ((RLEColumn) rightColumn).getVisibleColumns();
    int leftPatternCount = leftPatterns.getLeft().length;
    int rightPatternCount = rightPatterns.getLeft().length;
    int leftIndex = 0, rightIndex = 0;
    int curLeft = 0, curRight = 0;
    int curLeftPositionCount = 0, curRightPositionCount = 0;
    Column leftPatternColumn = leftPatterns.getLeft()[0]; // left physical value column
    Column rightPatternColumn = rightPatterns.getLeft()[0]; // right physical value column
    boolean isRLELeft = true,
        isRLERight =
            true; // mark if current Pattern is RLE mode, true for rle, false for bit-packed
    int index = 0; // the index of current calculating value
    int length = 0; // the length to process each round

    while (index < positionCount) {
      if (curLeft == curLeftPositionCount) {
        /** current leftPattern has reached end */
        if (leftIndex < leftPatternCount) {
          /** read next rlePattern */
          curLeft = 0;
          leftPatternColumn = leftPatterns.getLeft()[leftIndex];
          curLeftPositionCount = leftPatterns.getRight()[leftIndex];
          isRLELeft = leftPatternColumn.getPositionCount() == 1;
          leftIndex++;
        } else {
          /** leftRLEColumn has reached end, if rightRLEColumn didn't reach end, something wrong. */
          if (rightIndex < rightPatternCount - 1) {
            throw new RuntimeException("leftColumn and rightColumn have unequal length");
          } else {
            break;
          }
        }
      }

      if (curRight == curRightPositionCount) {
        /** current rightPattern has reached end */
        if (rightIndex < rightPatternCount) {
          /** read next rlePattern */
          curRight = 0;
          rightPatternColumn = rightPatterns.getLeft()[rightIndex];
          curRightPositionCount = rightPatterns.getRight()[rightIndex];
          isRLERight = rightPatternColumn.getPositionCount() == 1;
          rightIndex++;
        } else {
          /** rightRLEColumn has reached end, if leftRLEColumn didn't reach end, something wrong. */
          if (leftIndex < leftPatternCount - 1) {
            throw new RuntimeException("leftColumn and rightColumn have unequal length");
          } else {
            break;
          }
        }
      }
      length =
          curLeftPositionCount - curLeft > curRightPositionCount - curRight
              ? curRightPositionCount - curRight
              : curLeftPositionCount - curLeft;
      length = length > positionCount - index ? positionCount - index : length;

      if (isRLELeft && isRLERight) {
        double res =
            transform(
                leftTransformer.getType().getDouble(leftPatternColumn, 0),
                rightTransformer.getType().getDouble(rightPatternColumn, 0));
        for (int i = 0; i < length; i++) {
          returnType.writeDouble(builder, res);
        }
        index += length;
        curLeft += length;
        curRight += length;
      } else if (isRLELeft) {
        double leftValue = leftTransformer.getType().getDouble(leftPatternColumn, 0);
        for (int i = 0; i < length; i++, curRight++, index++) {
          if (!rightPatternColumn.isNull(curRight)) {
            returnType.writeDouble(
                builder,
                transform(
                    leftValue, rightTransformer.getType().getDouble(rightPatternColumn, curRight)));
          } else {
            builder.appendNull();
          }
        }
        curLeft += length;
      } else if (isRLERight) {
        double rightValue = rightTransformer.getType().getDouble(rightPatternColumn, 0);
        for (int i = 0; i < length; i++, curLeft++, index++) {
          if (!leftPatternColumn.isNull(curLeft)) {
            returnType.writeDouble(
                builder,
                transform(
                    leftTransformer.getType().getDouble(leftPatternColumn, curLeft), rightValue));
          } else {
            builder.appendNull();
          }
        }
        curRight += length;
      } else {
        for (int i = 0; i < length; i++, curLeft++, curRight++, index++) {
          if (!leftPatternColumn.isNull(curLeft) && !rightPatternColumn.isNull(curRight)) {
            returnType.writeDouble(
                builder,
                transform(
                    leftTransformer.getType().getDouble(leftPatternColumn, curLeft),
                    rightTransformer.getType().getDouble(rightPatternColumn, curRight)));
          } else {
            builder.appendNull();
          }
        }
      }
    }
  }

  private void doTransformWithLeftRLEandConstant(
      Column leftColumn, Column rightColumn, ColumnBuilder builder, int positionCount) {
    Pair<Column[], int[]> leftPatterns = ((RLEColumn) leftColumn).getVisibleColumns();
    int leftPatternCount = leftPatterns.getLeft().length;
    int leftIndex = 0, curLeft = 0, curLeftPositionCount = 0;
    Column leftPatternColumn = leftPatterns.getLeft()[0]; // left physical value column
    int index = 0; // the index of current calculating value
    int length = 0; // the length to process each round
    double value = rightTransformer.getType().getDouble(rightColumn, 0);
    while (index < positionCount) {
      // if (curLeft == curLeftPositionCount) {
      /** current leftPattern has reached end */
      if (leftIndex < leftPatternCount) {
        /** read next rlePattern */
        curLeft = 0;
        leftPatternColumn = leftPatterns.getLeft()[leftIndex];
        curLeftPositionCount = leftPatterns.getRight()[leftIndex];
        leftIndex++;
      } else {
        throw new RuntimeException(
            "The positionCount of leftColumn is less than the requested positionCount");
      }
      // }

      length =
          curLeftPositionCount - curLeft > positionCount - index
              ? positionCount - index
              : curLeftPositionCount - curLeft;
      if (leftPatternColumn.getPositionCount() == 1) {
        double res = transform(leftTransformer.getType().getDouble(leftPatternColumn, 0), value);
        for (int i = 0; i < length; i++) {
          returnType.writeDouble(builder, res);
        }
        index += length;
        curLeft += length;
      } else {
        for (int i = 0; i < length; i++, curLeft++, index++) {
          if (!leftPatternColumn.isNull(curLeft)) {
            returnType.writeDouble(
                builder,
                transform(leftTransformer.getType().getDouble(leftPatternColumn, curLeft), value));
          } else {
            builder.appendNull();
          }
        }
      }
    }
  }

  private void doTransformWithLeftDeltaandConstant(
      Column leftColumn, Column rightColumn, ColumnBuilder builder, int positionCount) {
    Column[] leftPatterns = ((DeltaColumn) leftColumn).getVisibleColumns();
    int leftPatternCount = leftPatterns.length;
    int leftIndex = 0, curLeft = 0, curLeftPositionCount = 0;
    Column leftPatternColumn = leftPatterns[0]; // left physical value column
    int index = 0; // the index of current calculating value
    int length = 0; // the length to process each round
    double value = rightTransformer.getType().getDouble(rightColumn, 0);

    DeltaColumnBuilder valueBuilder = (DeltaColumnBuilder) builder;

    while (index < positionCount) {
      // if (curLeft == curLeftPositionCount) {
      /** current leftPattern has reached end */

      ColumnBuilder valueColumnBuilder = contructColumnBuilders(Collections.singletonList(builder.getDataType()))[0];
      if (leftIndex < leftPatternCount) {
        /** read next deltaPattern */
        curLeft = 0;
        leftPatternColumn = leftPatterns[leftIndex];
        curLeftPositionCount = leftPatternColumn.getPositionCount();
        leftIndex++;
      } else {
        throw new RuntimeException(
            "The positionCount of leftColumn is less than the requested positionCount");
      }
      // }

      length =
          curLeftPositionCount - curLeft > positionCount - index
              ? positionCount - index
              : curLeftPositionCount - curLeft;

      returnType.writeDouble(
          valueColumnBuilder,
          transform(leftTransformer.getType().getDouble(leftPatternColumn, curLeft), value));
      curLeft++;
      index++;
      for (int i = 1; i < length; i++, curLeft++, index++) {
        if (!leftPatternColumn.isNull(curLeft)) {
          returnType.writeDouble(
              valueColumnBuilder, leftTransformer.getType().getDouble(leftPatternColumn, curLeft));
        } else {
          valueColumnBuilder.appendNull();
        }
      }
      valueBuilder.writeDeltaPattern(valueColumnBuilder.build());
    }
  }

  private void doTransformWithRightRLEandConstant(
      Column leftColumn, Column rightColumn, ColumnBuilder builder, int positionCount) {
    Pair<Column[], int[]> rightPatterns = ((RLEColumn) rightColumn).getVisibleColumns();
    int rightPatternCount = rightPatterns.getLeft().length;
    int rightIndex = 0, curRight = 0, curRightPositionCount = 0;
    Column rightPatternColumn = rightPatterns.getLeft()[0]; // right physical value column
    int index = 0; // the index of current calculating value
    int length = 0; // the length to process each round

    double value = leftTransformer.getType().getDouble(leftColumn, 0);
    while (index < positionCount) {
      // if (curRight == curRightPositionCount) {
      /** current rightPattern has reached end */
      if (rightIndex < rightPatternCount) {
        /** read next rlePattern */
        curRight = 0;
        rightPatternColumn = rightPatterns.getLeft()[rightIndex];
        curRightPositionCount = rightPatterns.getRight()[rightIndex];
        rightIndex++;
      } else {
        throw new RuntimeException(
            "The positionCount of rightColumn is less than the requested positionCount");
      }
      // }

      length =
          curRightPositionCount - curRight > positionCount - index
              ? positionCount - index
              : curRightPositionCount - curRight;
      if (rightPatternColumn.getPositionCount() == 1) {
        double res = transform(value, rightTransformer.getType().getDouble(rightPatternColumn, 0));
        for (int i = 0; i < length; i++) {
          returnType.writeDouble(builder, res);
        }
        index += length;
        curRight += length;
      } else {
        for (int i = 0; i < length; i++, curRight++, index++) {
          if (!rightPatternColumn.isNull(curRight)) {
            returnType.writeDouble(
                builder,
                transform(
                    value, rightTransformer.getType().getDouble(rightPatternColumn, curRight)));
          } else {
            builder.appendNull();
          }
        }
      }
    }
  }

  private void doTransformWithRightDeltaandConstant(
      Column leftColumn, Column rightColumn, ColumnBuilder builder, int positionCount) {
    Column[] rightPatterns = ((DeltaColumn) rightColumn).getVisibleColumns();
    int rightPatternCount = rightPatterns.length;
    int rightIndex = 0, curRight = 0, curRightPositionCount = 0;
    Column rightPatternColumn = rightPatterns[0]; // right physical value column
    int index = 0; // the index of current calculating value
    int length = 0; // the length to process each round

    DeltaColumnBuilder valueBuilder = (DeltaColumnBuilder) builder;

    double value = leftTransformer.getType().getDouble(leftColumn, 0);
    while (index < positionCount) {
      // if (curRight == curRightPositionCount) {
      /** current rightPattern has reached end */
      ColumnBuilder valueColumnBuilder =  contructColumnBuilders(Collections.singletonList(builder.getDataType()))[0];
      if (rightIndex < rightPatternCount) {
        /** read next rlePattern */
        curRight = 0;
        rightPatternColumn = rightPatterns[rightIndex];
        curRightPositionCount = rightPatternColumn.getPositionCount();
        rightIndex++;
      } else {
        throw new RuntimeException(
            "The positionCount of rightColumn is less than the requested positionCount");
      }
      // }
      length =
          curRightPositionCount - curRight > positionCount - index
              ? positionCount - index
              : curRightPositionCount - curRight;

      returnType.writeDouble(
          valueColumnBuilder,
          transform(value, rightTransformer.getType().getDouble(rightPatternColumn, curRight)));
      curRight++;
      index++;
      for (int i = 1; i < length; i++, curRight++, index++) {
        if (!rightPatternColumn.isNull(curRight)) {
          returnType.writeDouble(
              valueColumnBuilder,
              transform(value, rightTransformer.getType().getDouble(rightPatternColumn, curRight)));
        } else {
          valueColumnBuilder.appendNull();
        }
      }
      valueBuilder.writeDeltaPattern(valueColumnBuilder.build());
    }
  }

  @Override
  protected void doTransform(
      Column leftColumn, Column rightColumn, ColumnBuilder builder, int positionCount) {
    if (leftColumn instanceof RLEColumn && rightColumn instanceof RLEColumn) {
      doTransformBetweenRLE(leftColumn, rightColumn, builder, positionCount);
      return;
    } else if (leftColumn instanceof RLEColumn && rightColumn instanceof RunLengthEncodedColumn) {
      doTransformWithLeftRLEandConstant(leftColumn, rightColumn, builder, positionCount);
      return;
    } else if (rightColumn instanceof RLEColumn && leftColumn instanceof RunLengthEncodedColumn) {
      doTransformWithRightRLEandConstant(leftColumn, rightColumn, builder, positionCount);
      return;
    } else if ((this instanceof ArithmeticAdditionColumnTransformer
            || this instanceof ArithmeticSubtractionColumnTransformer)
        && (leftColumn instanceof DeltaColumn && rightColumn instanceof RunLengthEncodedColumn)) {
      doTransformWithLeftDeltaandConstant(leftColumn, rightColumn, builder, positionCount);
      return;
    } else if ((this instanceof ArithmeticAdditionColumnTransformer
            || this instanceof ArithmeticSubtractionColumnTransformer)
        && (rightColumn instanceof DeltaColumn && leftColumn instanceof RunLengthEncodedColumn)) {
      doTransformWithRightDeltaandConstant(leftColumn, rightColumn, builder, positionCount);
      return;
    }
    for (int i = 0; i < positionCount; i++) {
      if (!leftColumn.isNull(i) && !rightColumn.isNull(i)) {
        returnType.writeDouble(
            builder,
            transform(
                leftTransformer.getType().getDouble(leftColumn, i),
                rightTransformer.getType().getDouble(rightColumn, i)));
      } else {
        builder.appendNull();
      }
    }
  }

  @Override
  protected void checkType() {
    if (!leftTransformer.isReturnTypeNumeric() || !rightTransformer.isReturnTypeNumeric()) {
      throw new UnsupportedOperationException("Unsupported Type");
    }
  }

  protected abstract double transform(double d1, double d2);
}
