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

package cn.edu.tsinghua.iot.benchmark.workload.query.impl;

import cn.edu.tsinghua.iot.benchmark.conf.Config;
import cn.edu.tsinghua.iot.benchmark.conf.ConfigDescriptor;
import cn.edu.tsinghua.iot.benchmark.conf.Constants;
import cn.edu.tsinghua.iot.benchmark.schema.schemaImpl.DeviceSchema;
import cn.edu.tsinghua.iot.benchmark.utils.TimeUtils;

import java.util.List;

public class ExpValueQuery extends RangeQuery {

  private static final Config config = ConfigDescriptor.getInstance().getConfig();

  private static final long timeStampConst =
      TimeUtils.getTimestampConst(config.getTIMESTAMP_PRECISION());
  private static final long timeRangeConst =
      (config.getTIMESTAMP_PRECISION().equals("ns")) ? 3L : 1000L;
  private static final long END_TIME =
      (Constants.START_TIMESTAMP
              + config.getPOINT_STEP() * config.getBATCH_SIZE_PER_WRITE() * 1000L * timeRangeConst)
          * timeStampConst;

  public ExpValueQuery() {}

  public ExpValueQuery(
      List<DeviceSchema> deviceSchema, String exp, double expressionValue, double valueThreshold) {
    super(deviceSchema, Constants.START_TIMESTAMP * timeStampConst, END_TIME);
    this.exp = exp;
    this.valueThreshold = valueThreshold;
    this.expressionValue = expressionValue;
  }

  public double getValueThreshold() {
    return valueThreshold;
  }

  public String getExp() {
    return exp;
  }

  public double getExpressionValue() {
    return expressionValue;
  }

  private double valueThreshold;
  private double expressionValue;
  private String exp;

  @Override
  public StringBuilder getQueryAttrs() {
    return super.getQueryAttrs();
  }
}
