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
import org.apache.iotdb.db.queryengine.transformation.dag.util.TransformUtils;

import org.apache.tsfile.block.column.Column;
import org.apache.tsfile.block.column.ColumnBuilder;
import org.apache.tsfile.read.common.block.column.RLEColumn;
import org.apache.tsfile.read.common.block.column.RLEColumnBuilder;
import org.apache.tsfile.read.common.block.column.RunLengthEncodedColumn;
import org.apache.tsfile.read.common.type.Type;
import org.apache.tsfile.read.common.type.TypeEnum;
import org.apache.tsfile.utils.Binary;
import org.apache.tsfile.utils.Pair;

public abstract class CompareBinaryColumnTransformer extends BinaryColumnTransformer {

  protected CompareBinaryColumnTransformer(
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
        ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(1);
        boolean flag = false;
        if (!leftPatternColumn.isNull(0) && !rightPatternColumn.isNull(0)) {
          // compare binary type
          if (TypeEnum.BINARY.equals(leftTransformer.getType().getTypeEnum())) {
            flag =
                transform(
                    TransformUtils.compare(
                        leftTransformer.getType().getBinary(leftPatternColumn, 0),
                        rightTransformer.getType().getBinary(rightPatternColumn, 0)));
          } else if (TypeEnum.BOOLEAN.equals(leftTransformer.getType().getTypeEnum())) {
            flag =
                transform(
                    Boolean.compare(
                        leftTransformer.getType().getBoolean(leftPatternColumn, 0),
                        rightTransformer.getType().getBoolean(rightPatternColumn, 0)));
          } else {
            final double left = leftTransformer.getType().getDouble(leftPatternColumn, 0);
            final double right = rightTransformer.getType().getDouble(rightPatternColumn, 0);
            if (!Double.isNaN(left) && !Double.isNaN(right)) {
              flag = transform(Double.compare(left, right));
            }
          }
          returnType.writeBoolean(columnBuilderTmp, flag);
        } else {
          columnBuilderTmp.appendNull();
        }
        ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), length);
        index += length;
        curLeft += length;
        curRight += length;

      } else if (isRLELeft) {
        boolean flag = false;
        ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(length);
        // compare binary type
        if (TypeEnum.BINARY.equals(leftTransformer.getType().getTypeEnum())) {
          if (leftPatternColumn.isNull(0)) {
            columnBuilderTmp.appendNull();
          } else {
            Binary leftvalue = leftTransformer.getType().getBinary(leftPatternColumn, 0);
            for (int i = 0; i < length; i++, curRight++, index++) {
              if (!rightPatternColumn.isNull(curRight)) {
                flag =
                    transform(
                        TransformUtils.compare(
                            leftvalue,
                            rightTransformer.getType().getBinary(rightPatternColumn, curRight)));
              } else {
                columnBuilderTmp.appendNull();
              }
              returnType.writeBoolean(columnBuilderTmp, flag);
            }
          }
        } else if (TypeEnum.BOOLEAN.equals(leftTransformer.getType().getTypeEnum())) {
          if (leftPatternColumn.isNull(0)) {
            columnBuilderTmp.appendNull();
          } else {
            Boolean leftvalue = leftTransformer.getType().getBoolean(leftPatternColumn, 0);
            for (int i = 0; i < length; i++, curRight++, index++) {
              if (!rightPatternColumn.isNull(curRight)) {
                flag =
                    transform(
                        Boolean.compare(
                            leftvalue,
                            rightTransformer.getType().getBoolean(rightPatternColumn, curRight)));
              } else {
                columnBuilderTmp.appendNull();
              }
              returnType.writeBoolean(columnBuilderTmp, flag);
            }
          }
        } else {
          final double leftvalue = leftTransformer.getType().getDouble(leftPatternColumn, 0);
          if (leftPatternColumn.isNull(0)) {
            columnBuilderTmp.appendNull();
          } else if (Double.isNaN(leftvalue)) {
            returnType.writeBoolean(columnBuilderTmp, false);
          } else {
            for (int i = 0; i < length; i++, curRight++, index++) {
              final double rightvalue =
                  rightTransformer.getType().getDouble(rightPatternColumn, curRight);
              if (!rightPatternColumn.isNull(curRight)) {
                if (!Double.isNaN(rightvalue)) {
                  flag = transform(Double.compare(leftvalue, rightvalue));
                }
              } else {
                columnBuilderTmp.appendNull();
              }
              returnType.writeBoolean(columnBuilderTmp, flag);
            }
          }
        }
        ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), length);
        curLeft += length;
      } else if (isRLERight) {
        boolean flag = false;
        ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(length);
        // compare binary type
        if (TypeEnum.BINARY.equals(leftTransformer.getType().getTypeEnum())) {
          if (rightPatternColumn.isNull(0)) {
            columnBuilderTmp.appendNull();
          } else {
            Binary rightvalue = rightTransformer.getType().getBinary(rightPatternColumn, 0);
            for (int i = 0; i < length; i++, curLeft++, index++) {
              if (!leftPatternColumn.isNull(curLeft)) {
                flag =
                    transform(
                        TransformUtils.compare(
                            leftTransformer.getType().getBinary(leftPatternColumn, curLeft),
                            rightvalue));
              } else {
                columnBuilderTmp.appendNull();
              }
              returnType.writeBoolean(columnBuilderTmp, flag);
            }
          }
        } else if (TypeEnum.BOOLEAN.equals(leftTransformer.getType().getTypeEnum())) {
          if (rightPatternColumn.isNull(0)) {
            columnBuilderTmp.appendNull();
          } else {
            Boolean rightvalue = rightTransformer.getType().getBoolean(rightPatternColumn, 0);
            for (int i = 0; i < length; i++, curLeft++, index++) {
              if (!leftPatternColumn.isNull(curLeft)) {
                flag =
                    transform(
                        Boolean.compare(
                            leftTransformer.getType().getBoolean(leftPatternColumn, curLeft),
                            rightvalue));
              } else {
                columnBuilderTmp.appendNull();
              }
              returnType.writeBoolean(columnBuilderTmp, flag);
            }
          }
        } else {
          final double rightvalue = rightTransformer.getType().getDouble(rightPatternColumn, 0);
          if (rightPatternColumn.isNull(0)) {
            columnBuilderTmp.appendNull();
          } else if (Double.isNaN(rightvalue)) {
            returnType.writeBoolean(columnBuilderTmp, false);
          } else {
            for (int i = 0; i < length; i++, curLeft++, index++) {
              final double leftvalue =
                  leftTransformer.getType().getDouble(leftPatternColumn, curLeft);
              if (!leftPatternColumn.isNull(curLeft)) {
                if (!Double.isNaN(leftvalue)) {
                  flag = transform(Double.compare(leftvalue, rightvalue));
                }
              } else {
                columnBuilderTmp.appendNull();
              }
              returnType.writeBoolean(columnBuilderTmp, flag);
            }
          }
        }
        ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), length);
        curRight += length;
      } else {
        ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(1);
        boolean flag = false;

        for (int i = 0; i < length; i++, curLeft++, curRight++, index++) {
          if (!leftPatternColumn.isNull(curLeft) && !rightPatternColumn.isNull(curRight)) {
            // compare binary type
            if (TypeEnum.BINARY.equals(leftTransformer.getType().getTypeEnum())) {
              flag =
                  transform(
                      TransformUtils.compare(
                          leftTransformer.getType().getBinary(leftPatternColumn, curLeft),
                          rightTransformer.getType().getBinary(rightPatternColumn, curRight)));
            } else if (TypeEnum.BOOLEAN.equals(leftTransformer.getType().getTypeEnum())) {
              flag =
                  transform(
                      Boolean.compare(
                          leftTransformer.getType().getBoolean(leftPatternColumn, curLeft),
                          rightTransformer.getType().getBoolean(rightPatternColumn, curRight)));
            } else {
              final double left = leftTransformer.getType().getDouble(leftPatternColumn, curLeft);
              final double right =
                  rightTransformer.getType().getDouble(rightPatternColumn, curRight);
              if (!Double.isNaN(left) && !Double.isNaN(right)) {
                flag = transform(Double.compare(left, right));
              }
            }
            returnType.writeBoolean(columnBuilderTmp, flag);
          } else {
            columnBuilderTmp.appendNull();
          }
        }
        ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), length);
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

    if (rightColumn.isNull(0)) {
      ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(1);
      columnBuilderTmp.appendNull();
      ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), positionCount);
      return;
    }

    while (index < positionCount) {
      if (curLeft == curLeftPositionCount) {
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
      }
      length =
          curLeftPositionCount - curLeft > positionCount - index
              ? positionCount - index
              : curLeftPositionCount - curLeft;
      if (leftPatternColumn.getPositionCount() == 1) {
        ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(1);
        boolean flag = false;
        if (!leftPatternColumn.isNull(0)) {
          // compare binary type
          if (TypeEnum.BINARY.equals(leftTransformer.getType().getTypeEnum())) {
            flag =
                transform(
                    TransformUtils.compare(
                        leftTransformer.getType().getBinary(leftPatternColumn, 0),
                        rightTransformer.getType().getBinary(rightColumn, 0)));
          } else if (TypeEnum.BOOLEAN.equals(leftTransformer.getType().getTypeEnum())) {
            flag =
                transform(
                    Boolean.compare(
                        leftTransformer.getType().getBoolean(leftPatternColumn, 0),
                        rightTransformer.getType().getBoolean(rightColumn, 0)));
          } else {
            final double left = leftTransformer.getType().getDouble(leftPatternColumn, 0);
            final double right = rightTransformer.getType().getDouble(rightColumn, 0);
            if (!Double.isNaN(left) && !Double.isNaN(right)) {
              flag = transform(Double.compare(left, right));
            }
          }
          returnType.writeBoolean(columnBuilderTmp, flag);
        } else {
          columnBuilderTmp.appendNull();
        }
        ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), length);
        index += length;
        curLeft += length;
      } else {
        boolean flag = false;
        ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(length);
        // compare binary type
        if (TypeEnum.BINARY.equals(leftTransformer.getType().getTypeEnum())) {

          Binary rightvalue = rightTransformer.getType().getBinary(rightColumn, 0);
          for (int i = 0; i < length; i++, curLeft++, index++) {
            if (!leftPatternColumn.isNull(curLeft)) {
              flag =
                  transform(
                      TransformUtils.compare(
                          leftTransformer.getType().getBinary(leftPatternColumn, curLeft),
                          rightvalue));
            } else {
              columnBuilderTmp.appendNull();
            }
            returnType.writeBoolean(columnBuilderTmp, flag);
          }

        } else if (TypeEnum.BOOLEAN.equals(leftTransformer.getType().getTypeEnum())) {

          Boolean rightvalue = rightTransformer.getType().getBoolean(rightColumn, 0);
          for (int i = 0; i < length; i++, curLeft++, index++) {
            if (!leftPatternColumn.isNull(curLeft)) {
              flag =
                  transform(
                      Boolean.compare(
                          leftTransformer.getType().getBoolean(leftPatternColumn, curLeft),
                          rightvalue));
            } else {
              columnBuilderTmp.appendNull();
            }
            returnType.writeBoolean(columnBuilderTmp, flag);
          }
        } else {
          final double rightvalue = rightTransformer.getType().getDouble(rightColumn, 0);
          if (Double.isNaN(rightvalue)) {
            returnType.writeBoolean(columnBuilderTmp, false);
          } else {
            for (int i = 0; i < length; i++, curLeft++, index++) {
              final double leftvalue =
                  leftTransformer.getType().getDouble(leftPatternColumn, curLeft);
              if (!leftPatternColumn.isNull(curLeft)) {
                if (!Double.isNaN(leftvalue)) {
                  flag = transform(Double.compare(leftvalue, rightvalue));
                }
              } else {
                columnBuilderTmp.appendNull();
              }
              returnType.writeBoolean(columnBuilderTmp, flag);
            }
          }
        }
        ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), length);
      }
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

    if (leftColumn.isNull(0)) {
      ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(1);
      columnBuilderTmp.appendNull();
      ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), positionCount);
      return;
    }

    while (index < positionCount) {
      if (curRight == curRightPositionCount) {
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
      }

      length =
          curRightPositionCount - curRight > positionCount - index
              ? positionCount - index
              : curRightPositionCount - curRight;
      if (rightPatternColumn.getPositionCount() == 1) {
        ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(1);
        boolean flag = false;
        if (!rightPatternColumn.isNull(0)) {
          // compare binary type
          if (TypeEnum.BINARY.equals(leftTransformer.getType().getTypeEnum())) {
            flag =
                transform(
                    TransformUtils.compare(
                        leftTransformer.getType().getBinary(leftColumn, 0),
                        rightTransformer.getType().getBinary(rightPatternColumn, 0)));
          } else if (TypeEnum.BOOLEAN.equals(leftTransformer.getType().getTypeEnum())) {
            flag =
                transform(
                    Boolean.compare(
                        leftTransformer.getType().getBoolean(leftColumn, 0),
                        rightTransformer.getType().getBoolean(rightPatternColumn, 0)));
          } else {
            final double left = leftTransformer.getType().getDouble(leftColumn, 0);
            final double right = rightTransformer.getType().getDouble(rightPatternColumn, 0);
            if (!Double.isNaN(left) && !Double.isNaN(right)) {
              flag = transform(Double.compare(left, right));
            }
          }
          returnType.writeBoolean(columnBuilderTmp, flag);
        } else {
          columnBuilderTmp.appendNull();
        }
        ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), length);
        index += length;
        curRight += length;
      } else {

        boolean flag = false;
        ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(length);
        // compare binary type
        if (TypeEnum.BINARY.equals(leftTransformer.getType().getTypeEnum())) {

          Binary leftvalue = leftTransformer.getType().getBinary(leftColumn, 0);
          for (int i = 0; i < length; i++, curRight++, index++) {
            if (!rightPatternColumn.isNull(curRight)) {
              flag =
                  transform(
                      TransformUtils.compare(
                          leftvalue,
                          rightTransformer.getType().getBinary(rightPatternColumn, curRight)));
            } else {
              columnBuilderTmp.appendNull();
            }
            returnType.writeBoolean(columnBuilderTmp, flag);
          }

        } else if (TypeEnum.BOOLEAN.equals(leftTransformer.getType().getTypeEnum())) {

          Boolean leftvalue = leftTransformer.getType().getBoolean(leftColumn, 0);
          for (int i = 0; i < length; i++, curRight++, index++) {
            if (!rightPatternColumn.isNull(curRight)) {
              flag =
                  transform(
                      Boolean.compare(
                          leftvalue,
                          rightTransformer.getType().getBoolean(rightPatternColumn, curRight)));
            } else {
              columnBuilderTmp.appendNull();
            }
            returnType.writeBoolean(columnBuilderTmp, flag);
          }

        } else {
          final double leftvalue = leftTransformer.getType().getDouble(leftColumn, 0);
          if (Double.isNaN(leftvalue)) {
            returnType.writeBoolean(columnBuilderTmp, false);
          } else {
            for (int i = 0; i < length; i++, curRight++, index++) {
              final double rightvalue =
                  rightTransformer.getType().getDouble(rightPatternColumn, curRight);
              if (!rightPatternColumn.isNull(curRight)) {
                if (!Double.isNaN(rightvalue)) {
                  flag = transform(Double.compare(leftvalue, rightvalue));
                }
              } else {
                columnBuilderTmp.appendNull();
              }
              returnType.writeBoolean(columnBuilderTmp, flag);
            }
          }
        }
        ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), length);
      }
    }
  }

  @Override
  protected void doTransform(
      Column leftColumn, Column rightColumn, ColumnBuilder builder, int positionCount) {
    // if either column is all null, append nullCount. For now, a RunLengthEncodeColumn with
    // mayHaveNull == true is all null
    if ((leftColumn.mayHaveNull() && leftColumn instanceof RunLengthEncodedColumn)
        || (rightColumn.mayHaveNull() && rightColumn instanceof RunLengthEncodedColumn)) {
      builder.appendNull(positionCount);
      return;
    }
    if (leftColumn instanceof RLEColumn && rightColumn instanceof RLEColumn) {
      doTransformBetweenRLE(leftColumn, rightColumn, builder, positionCount);
      return;
    } else if (leftColumn instanceof RLEColumn && rightColumn instanceof RunLengthEncodedColumn) {
      doTransformWithLeftRLEandConstant(leftColumn, rightColumn, builder, positionCount);
      return;
    } else if (rightColumn instanceof RLEColumn && leftColumn instanceof RunLengthEncodedColumn) {
      doTransformWithRightRLEandConstant(leftColumn, rightColumn, builder, positionCount);
      return;
    }
    for (int i = 0; i < positionCount; i++) {
      if (!leftColumn.isNull(i) && !rightColumn.isNull(i)) {
        boolean flag = false;
        // compare binary type
        if (TypeEnum.BINARY.equals(leftTransformer.getType().getTypeEnum())) {
          flag =
              transform(
                  TransformUtils.compare(
                      leftTransformer.getType().getBinary(leftColumn, i),
                      rightTransformer.getType().getBinary(rightColumn, i)));
        } else if (TypeEnum.BOOLEAN.equals(leftTransformer.getType().getTypeEnum())) {
          flag =
              transform(
                  Boolean.compare(
                      leftTransformer.getType().getBoolean(leftColumn, i),
                      rightTransformer.getType().getBoolean(rightColumn, i)));
        } else {
          final double left = leftTransformer.getType().getDouble(leftColumn, i);
          final double right = rightTransformer.getType().getDouble(rightColumn, i);
          if (!Double.isNaN(left) && !Double.isNaN(right)) {
            flag = transform(Double.compare(left, right));
          }
        }
        returnType.writeBoolean(builder, flag);
      } else {
        builder.appendNull();
      }
    }
  }

  @Override
  protected void checkType() {
    // Boolean type can only be compared by == or !=
    if (leftTransformer.typeNotEquals(TypeEnum.BOOLEAN)
        && rightTransformer.typeNotEquals(TypeEnum.BOOLEAN)) {
      return;
    }
    throw new UnsupportedOperationException("Unsupported Type");
  }

  /**
   * Transform int value of flag to corresponding boolean value.
   *
   * @param flag input int value
   * @return result boolean value
   */
  protected abstract boolean transform(int flag);
}
