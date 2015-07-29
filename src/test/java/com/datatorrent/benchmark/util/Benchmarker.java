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

import java.io.PrintStream;

/**
 * <p>Coral Block based Benchmark</p>
 * see: <a href="http://www.coralblocks.com/index.php/2014/04/coralreactor-vs-netty-performance-comparison">http://www.coralblocks.com/index.php/2014/04/coralreactor-vs-netty-performance-comparison</a>,
 * <a href="http://stackoverflow.com/questions/23839437/what-are-the-netty-alternatives-for-high-performance-networking">http://stackoverflow.com/questions/23839437/what-are-the-netty-alternatives-for-high-performance-networking</a>,
 * <a href="http://www.coralblocks.com/NettyBench.zip">http://www.coralblocks.com/NettyBench.zip</a> and
 * <a href="https://groups.google.com/forum/#!topic/mechanical-sympathy/fhbyMnnxmaA">https://groups.google.com/forum/#!topic/mechanical-sympathy/fhbyMnnxmaA</a>
 */
public class Benchmarker {

	private static final int DEFAULT_WARMUP = 0;
	private static final int NUMBER_OF_DECIMALS = 3;
	private static final long NANO_COST_ITERATIONS = 10000000L;

	private long time;
	private long count = 0;
	private long totalTime = 0;
	private long minTime = Long.MAX_VALUE;
	private long maxTime = Long.MIN_VALUE;

	private final int warmup;
	private final boolean excludeNanoTimeCost;
	private final long nanoTimeCost;

	Benchmarker(final int warmup) {
		this.warmup = warmup;
		
		this.excludeNanoTimeCost = SystemUtils.getBoolean("excludeNanoTimeCost", false);
		
		if (excludeNanoTimeCost) {
			nanoTimeCost = calcNanoTimeCost();
		} else {
			nanoTimeCost = 0;
		}
		
		try {
			// initialize it here so when you measure for the first time gargage is not created...
			Class.forName("java.lang.Math");
		}
		catch (final Exception e) {
			e.printStackTrace();
		}
	}

	public static Benchmarker create() {
		return create(DEFAULT_WARMUP);
	}
	
	public static Benchmarker create(boolean detailedBenchmarker) {
		return create(DEFAULT_WARMUP, detailedBenchmarker);
	}
	
	public static Benchmarker create(int warmup) {
		boolean detailedBenchmarker = SystemUtils.getBoolean("detailedBenchmarker", false);
		return create(warmup, detailedBenchmarker);
	}
	
	public long getNanoTimeCost() {
		return excludeNanoTimeCost ? nanoTimeCost : -1;
	}
	
    public static long calcNanoTimeCost() {

    	final long start = System.nanoTime();

			for (int i = 0; i < NANO_COST_ITERATIONS; i++) {
					System.nanoTime();
			}

			long finish = System.nanoTime();

			return (finish - start) / NANO_COST_ITERATIONS;
    }
	
	public static Benchmarker create(int warmup, boolean detailedBenchmarker) {
		Benchmarker b;
		if (detailedBenchmarker) {
			b = new DetailedBenchmarker(warmup);
		} else {
			b = new Benchmarker(warmup);
		}
		return b;
	}

	public void reset() {
		time = 0;
		count = 0;
		totalTime = 0;
		minTime = Long.MAX_VALUE;
		maxTime = Long.MIN_VALUE;
	}

	public void mark() {
		time = System.nanoTime();
	}

	public long measure() {
		if (time > 0) {
			long lastTime = System.nanoTime() - time - nanoTimeCost;
			lastTime = Math.max(0, lastTime);
			final boolean counted = measure(lastTime);
			if (counted) {
				return lastTime;
			}
		}
		return -1;
	}
	
	public final boolean isWarmingUp() {
		return warmup <= count;
	}

	public boolean measure(final long lastNanoTime) {
		if (++count > warmup) {
			totalTime += lastNanoTime;
			minTime = Math.min(minTime, lastNanoTime);
			maxTime = Math.max(maxTime, lastNanoTime);
			return true;
		}
		return false;
	}

	private double avg() {
		final long realCount = count - warmup;
		if (realCount <= 0) {
			return 0;
		}
		final double avg = ((double) totalTime / (double) realCount);
		return Math.round(avg * 100D) / 100D;
	}
	
	private static double round(double d) {
		
		return round(d, NUMBER_OF_DECIMALS);
	}
	
	private static double round(double d, int decimals) {
		
		double pow = Math.pow(10, decimals);
		
		return ((double) Math.round(d * pow)) / pow;
	}
	
	public static String convertNanoTime(double nanoTime) {
		if (nanoTime >= 1000000L) {
			// millis...
			double millis = round(nanoTime / 1000000D);
			return millis + " millis";
		} else if (nanoTime >= 1000L) {
			// micros...
			double micros = round(nanoTime / 1000L);
			return micros + " micros";
		} else {
			double nanos = round(nanoTime);
			return nanos + " nanos";
		}
	}

	public String results() {
		final StringBuilder sb = new StringBuilder(128);
		final long realCount = count - warmup;
		sb.append("Iterations: ").append(realCount);
		sb.append(" | Avg Time: ").append(convertNanoTime(avg()));
		if (realCount > 0) {
			sb.append(" | Min Time: ").append(convertNanoTime(minTime));
			sb.append(" | Max Time: ").append(convertNanoTime(maxTime));
		}

		return sb.toString();
	}

	public void printResults(PrintStream out) {
		StringBuilder results = new StringBuilder();
		results.append("results=");
		results.append(results());
		out.println(results);
	}

}
