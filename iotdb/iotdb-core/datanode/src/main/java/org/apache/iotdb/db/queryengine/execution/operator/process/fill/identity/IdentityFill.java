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

package org.apache.iotdb.db.queryengine.execution.operator.process.fill.identity;

import org.apache.iotdb.db.queryengine.execution.operator.process.fill.IFill;

import org.apache.tsfile.block.column.Column;
import org.apache.tsfile.read.common.block.column.TimeColumn;

public class IdentityFill implements IFill {

  @Override
  public Column fill(TimeColumn timeColumn, Column valueColumn) {
    return valueColumn;
  }
}