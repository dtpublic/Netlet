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

import java.io.PrintStream;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.Precision;

public class BenchmarkResults
{
  private static final int PRECISION = 3;
  private long[] results;
  private int pos;

  public BenchmarkResults(int count)
  {
    pos = 0;
    results = new long[count];
  }

  public void addResult(long time)
  {
    results[pos++] = time;
  }

  private DescriptiveStatistics getDescriptiveStatistics()
  {
    double[] results = new double[pos];
    for (int i = 0; i < pos; i++)
    {
      results[i] = this.results[i];
    }
    return new DescriptiveStatistics(results);
  }

  private static String fromNanoTime(double nanoTime)
  {
    if (nanoTime > 1000000d) {
      return Precision.round(nanoTime/1000000d, PRECISION) + " millis";
    } else if (nanoTime > 1000d) {
      return Precision.round(nanoTime/1000d, PRECISION) + " micros";
    } else {
      return Precision.round(nanoTime, PRECISION) + " nanos";
    }
  }

  private String getResults()
  {
    DescriptiveStatistics statistics = getDescriptiveStatistics();
    final StringBuilder sb = new StringBuilder();
    sb.append("Iterations: ").append(statistics.getN());
    sb.append(" | Avg Time: ").append(fromNanoTime(statistics.getMean()));
    sb.append(" | Min Time: ").append(fromNanoTime(statistics.getMin()));
    sb.append(" | Max Time: ").append(fromNanoTime(statistics.getMax()));
    sb.append(" | 75% Time: ").append(fromNanoTime(statistics.getPercentile(75d)));
    sb.append(" | 90% Time: ").append(fromNanoTime(statistics.getPercentile(90d)));
    sb.append(" | 99% Time: ").append(fromNanoTime(statistics.getPercentile(99d)));
    sb.append(" | 99.9% Time: ").append(fromNanoTime(statistics.getPercentile(99.9d)));
    sb.append(" | 99.99% Time: ").append(fromNanoTime(statistics.getPercentile(99.99d)));
    sb.append(" | 99.999% Time: ").append(fromNanoTime(statistics.getPercentile(99.999d)));

    return sb.toString();

  }

  public void printResults(PrintStream out)
  {
    StringBuilder results = new StringBuilder();
    results.append("results=");
    results.append(getResults());
    out.println(results);
  }


}
