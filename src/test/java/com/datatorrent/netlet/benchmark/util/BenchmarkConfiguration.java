/**
 * Copyright (C) 2015 DataTorrent, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.netlet.benchmark.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.EnvironmentConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SystemConfiguration;

public class BenchmarkConfiguration
{
  private static final Logger logger = LoggerFactory.getLogger(BenchmarkConfiguration.class);

  public static final String messageCountName = "com.datatorrent.netlet.benchmark.message.count";
  public static final String messageSizeName = "com.datatorrent.netlet.benchmark.message.size";

  private static final int defaultMessageCount = 1000000;
  private static final int defaultMessageSize = 256;

  private static final CompositeConfiguration configuration = new CompositeConfiguration();

  static {
    configuration.addConfiguration(new SystemConfiguration());
    configuration.addConfiguration(new EnvironmentConfiguration());
    try {
      configuration.addConfiguration(new PropertiesConfiguration("benchmark.properties"));
    }
    catch (ConfigurationException e) {
      logger.warn("", e);
    }
  }

  public static final int messageCount = getInt(messageCountName, defaultMessageCount);
  public static final int messageSize = getInt(messageSizeName, defaultMessageSize);

  private static int getInt(final String key, final int defaultValue) {
    return configuration.getInt(key, defaultValue);
  }

}
