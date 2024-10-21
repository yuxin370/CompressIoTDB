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
import org.apache.tsfile.read.common.block.column.RLEColumn;
import org.apache.tsfile.read.common.block.column.RLEColumnBuilder;
import org.apache.tsfile.read.common.block.column.RunLengthEncodedColumn;
import org.apache.tsfile.read.common.type.Type;
import org.apache.tsfile.utils.Pair;

public class LogicAndColumnTransformer extends LogicBinaryColumnTransformer {
  public LogicAndColumnTransformer(
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

    if (!(builder instanceof RLEColumnBuilder)) {
      builder =
          new RLEColumnBuilder(
              null,
              (leftPatternCount > rightPatternCount ? leftPatternCount : rightPatternCount),
              returnType.getTypeEnum());
    }
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
        if (!leftPatternColumn.isNull(0) && !rightPatternColumn.isNull(0)) {
          returnType.writeBoolean(
              columnBuilderTmp,
              transform(
                  leftTransformer.getType().getBoolean(leftPatternColumn, 0),
                  rightTransformer.getType().getBoolean(rightPatternColumn, 0)));
        } else if (!leftPatternColumn.isNull(0)) {
          if (leftTransformer.getType().getBoolean(leftPatternColumn, 0)) {
            columnBuilderTmp.appendNull();
          } else {
            returnType.writeBoolean(columnBuilderTmp, false);
          }
        } else if (!rightPatternColumn.isNull(0)) {
          if (rightTransformer.getType().getBoolean(rightPatternColumn, 0)) {
            columnBuilderTmp.appendNull();
          } else {
            returnType.writeBoolean(columnBuilderTmp, false);
          }
        } else {
          columnBuilderTmp.appendNull();
        }
        ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), length);
        index += length;
        curLeft += length;
        curRight += length;
      } else if (isRLELeft) {
        Boolean leftValue = leftTransformer.getType().getBoolean(leftPatternColumn, 0);
        ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(length);

        if (leftPatternColumn.isNull(0)) {
          for (int i = 0; i < length; i++, curRight++, index++) {
            if (!rightPatternColumn.isNull(curRight)) {
              if (rightTransformer.getType().getBoolean(rightPatternColumn, curRight)) {
                columnBuilderTmp.appendNull();
              } else {
                returnType.writeBoolean(columnBuilderTmp, false);
              }
            } else {
              columnBuilderTmp.appendNull();
            }
          }
        } else {
          for (int i = 0; i < length; i++, curRight++, index++) {
            if (!rightPatternColumn.isNull(curRight)) {
              returnType.writeBoolean(
                  columnBuilderTmp,
                  transform(
                      leftValue,
                      rightTransformer.getType().getBoolean(rightPatternColumn, curRight)));
            } else {
              if (leftValue) {
                columnBuilderTmp.appendNull();
              } else {
                returnType.writeBoolean(columnBuilderTmp, false);
              }
            }
          }
        }
        ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), length);
        curLeft += length;
      } else if (isRLERight) {
        Boolean rightValue = rightTransformer.getType().getBoolean(rightPatternColumn, 0);
        ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(length);

        if (rightPatternColumn.isNull(0)) {
          for (int i = 0; i < length; i++, curLeft++, index++) {
            if (!leftPatternColumn.isNull(curLeft)) {
              if (leftTransformer.getType().getBoolean(leftPatternColumn, curLeft)) {
                columnBuilderTmp.appendNull();
              } else {
                returnType.writeBoolean(columnBuilderTmp, false);
              }
            } else {
              columnBuilderTmp.appendNull();
            }
          }
        } else {
          for (int i = 0; i < length; i++, curLeft++, index++) {
            if (!leftPatternColumn.isNull(curLeft)) {
              returnType.writeBoolean(
                  columnBuilderTmp,
                  transform(
                      leftTransformer.getType().getBoolean(leftPatternColumn, curLeft),
                      rightValue));
            } else {
              if (rightValue) {
                columnBuilderTmp.appendNull();
              } else {
                returnType.writeBoolean(columnBuilderTmp, false);
              }
            }
          }
        }
        ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), length);
        curRight += length;
      } else {
        ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(length);

        for (int i = 0; i < length; i++, curLeft++, curRight++, index++) {

          if (!leftPatternColumn.isNull(curLeft) && !rightPatternColumn.isNull(curRight)) {
            returnType.writeBoolean(
                columnBuilderTmp,
                transform(
                    leftTransformer.getType().getBoolean(leftPatternColumn, curLeft),
                    rightTransformer.getType().getBoolean(rightPatternColumn, curRight)));
          } else if (!leftPatternColumn.isNull(curLeft)) {
            if (leftTransformer.getType().getBoolean(leftPatternColumn, curLeft)) {
              columnBuilderTmp.appendNull();
            } else {
              returnType.writeBoolean(columnBuilderTmp, false);
            }
          } else if (!rightPatternColumn.isNull(curRight)) {
            if (rightTransformer.getType().getBoolean(rightPatternColumn, curRight)) {
              columnBuilderTmp.appendNull();
            } else {
              returnType.writeBoolean(columnBuilderTmp, false);
            }
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
    if (!(builder instanceof RLEColumnBuilder)) {
      builder = new RLEColumnBuilder(null, leftPatternCount, returnType.getTypeEnum());
    }
    Boolean rightValue = rightTransformer.getType().getBoolean(rightColumn, 0);

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
        if (!leftPatternColumn.isNull(0) && !rightColumn.isNull(0)) {
          returnType.writeBoolean(
              columnBuilderTmp,
              transform(leftTransformer.getType().getBoolean(leftPatternColumn, 0), rightValue));
        } else if (!leftPatternColumn.isNull(0)) {
          if (leftTransformer.getType().getBoolean(leftPatternColumn, 0)) {
            columnBuilderTmp.appendNull();
          } else {
            returnType.writeBoolean(columnBuilderTmp, false);
          }
        } else if (!rightColumn.isNull(0)) {
          if (rightValue) {
            columnBuilderTmp.appendNull();
          } else {
            returnType.writeBoolean(columnBuilderTmp, false);
          }
        } else {
          columnBuilderTmp.appendNull();
        }
        ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), length);
        index += length;
        curLeft += length;

      } else {

        ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(length);

        if (rightColumn.isNull(0)) {
          for (int i = 0; i < length; i++, curLeft++, index++) {
            if (!leftPatternColumn.isNull(curLeft)) {
              if (leftTransformer.getType().getBoolean(leftPatternColumn, curLeft)) {
                columnBuilderTmp.appendNull();
              } else {
                returnType.writeBoolean(columnBuilderTmp, false);
              }
            } else {
              columnBuilderTmp.appendNull();
            }
          }
        } else {
          for (int i = 0; i < length; i++, curLeft++, index++) {
            if (!leftPatternColumn.isNull(curLeft)) {
              returnType.writeBoolean(
                  columnBuilderTmp,
                  transform(
                      leftTransformer.getType().getBoolean(leftPatternColumn, curLeft),
                      rightValue));
            } else {
              if (rightValue) {
                columnBuilderTmp.appendNull();
              } else {
                returnType.writeBoolean(columnBuilderTmp, false);
              }
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
    if (!(builder instanceof RLEColumnBuilder)) {
      builder = new RLEColumnBuilder(null, rightPatternCount, returnType.getTypeEnum());
    }
    Boolean leftValue = leftTransformer.getType().getBoolean(leftColumn, 0);
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
        if (!leftColumn.isNull(0) && !rightPatternColumn.isNull(0)) {
          returnType.writeBoolean(
              columnBuilderTmp,
              transform(leftValue, rightTransformer.getType().getBoolean(rightPatternColumn, 0)));
        } else if (!leftColumn.isNull(0)) {
          if (leftValue) {
            columnBuilderTmp.appendNull();
          } else {
            returnType.writeBoolean(columnBuilderTmp, false);
          }
        } else if (!rightPatternColumn.isNull(0)) {
          if (rightTransformer.getType().getBoolean(rightPatternColumn, 0)) {
            columnBuilderTmp.appendNull();
          } else {
            returnType.writeBoolean(columnBuilderTmp, false);
          }
        } else {
          columnBuilderTmp.appendNull();
        }
        ((RLEColumnBuilder) builder).writeRLEPattern(columnBuilderTmp.build(), length);
        index += length;
        curRight += length;

      } else {
        ColumnBuilder columnBuilderTmp = returnType.createColumnBuilder(length);

        if (leftColumn.isNull(0)) {
          for (int i = 0; i < length; i++, curRight++, index++) {
            if (!rightPatternColumn.isNull(curRight)) {
              if (rightTransformer.getType().getBoolean(rightPatternColumn, curRight)) {
                columnBuilderTmp.appendNull();
              } else {
                returnType.writeBoolean(columnBuilderTmp, false);
              }
            } else {
              columnBuilderTmp.appendNull();
            }
          }
        } else {
          for (int i = 0; i < length; i++, curRight++, index++) {
            if (!rightPatternColumn.isNull(curRight)) {
              returnType.writeBoolean(
                  columnBuilderTmp,
                  transform(
                      leftValue,
                      rightTransformer.getType().getBoolean(rightPatternColumn, curRight)));
            } else {
              if (leftValue) {
                columnBuilderTmp.appendNull();
              } else {
                returnType.writeBoolean(columnBuilderTmp, false);
              }
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
        returnType.writeBoolean(
            builder,
            transform(
                leftTransformer.getType().getBoolean(leftColumn, i),
                rightTransformer.getType().getBoolean(rightColumn, i)));
      } else if (!leftColumn.isNull(i)) {
        if (leftTransformer.getType().getBoolean(leftColumn, i)) {
          builder.appendNull();
        } else {
          returnType.writeBoolean(builder, false);
        }
      } else if (!rightColumn.isNull(i)) {
        if (rightTransformer.getType().getBoolean(rightColumn, i)) {
          builder.appendNull();
        } else {
          returnType.writeBoolean(builder, false);
        }
      } else {
        builder.appendNull();
      }
    }
  }

  @Override
  protected boolean transform(boolean left, boolean right) {
    return left && right;
  }
}
