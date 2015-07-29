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
package com.datatorrent.benchmark.util;

import java.text.NumberFormat;
import java.util.Arrays;

import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;

/**
 * <p>Coral Block based Detailed Benchmark</p>
 * see: <a href="http://www.coralblocks.com/index.php/2014/04/coralreactor-vs-netty-performance-comparison">http://www.coralblocks.com/index.php/2014/04/coralreactor-vs-netty-performance-comparison</a>,
 * <a href="http://stackoverflow.com/questions/23839437/what-are-the-netty-alternatives-for-high-performance-networking">http://stackoverflow.com/questions/23839437/what-are-the-netty-alternatives-for-high-performance-networking</a>,
 * <a href="http://www.coralblocks.com/NettyBench.zip">http://www.coralblocks.com/NettyBench.zip</a> and
 * <a href="https://groups.google.com/forum/#!topic/mechanical-sympathy/fhbyMnnxmaA">https://groups.google.com/forum/#!topic/mechanical-sympathy/fhbyMnnxmaA</a>
 */
public class DetailedBenchmarker extends Benchmarker {
	
	private TLongIntMap results = new TLongIntHashMap(1000000);
  private long[] times;
	private int size;
	
	DetailedBenchmarker(int warmup) {
	    super(warmup);
    }
	
	@Override
	public void reset() {
		super.reset();
		size = 0;
		results.clear();
	}
	
	@Override
	public boolean measure(long lastTime) {
		boolean counted = super.measure(lastTime);
		if (counted) {
      results.adjustOrPutValue(lastTime, 1, 1);
			size++;
		}
		return counted;
	}
	
	private String formatPercentage(double x) {
		NumberFormat percentFormat = NumberFormat.getPercentInstance();
		percentFormat.setMaximumFractionDigits(3);
		return percentFormat.format(x);
	}

	private void addPercentile(StringBuilder sb, double perc) {
		
		if (results.isEmpty()) {
			return;
		}

		long max = -1;
		long x = Math.round(perc * size);
    int count = 0;
    long sum = 0;
    for (long time : times) {
      int i = results.get(time);
      count += i;
      sum += (i*time);
      if (count >= x) {
        max = time;
        break;
      }
		}
		sb.append(" | ").append(formatPercentage(perc)).append(" = [avg: ").append(convertNanoTime(sum / count)).append(", max: ").append(convertNanoTime(max)).append(']');
	}
	
	@Override
	public String results() {
		StringBuilder sb = new StringBuilder(super.results());
    times = results.keys();
    Arrays.sort(times);

		addPercentile(sb, 0.75D);
		addPercentile(sb, 0.9D);
		addPercentile(sb, 0.99D);
		addPercentile(sb, 0.999D);
		addPercentile(sb, 0.9999D);
		addPercentile(sb, 0.99999D);
		
		return sb.toString();
	}
	
}
