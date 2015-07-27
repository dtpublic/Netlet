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
package com.datatorrent.benchmark.netlet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.benchmark.util.Benchmarker;
import com.datatorrent.netlet.AbstractClient;
import com.datatorrent.netlet.DefaultEventLoop;
import com.datatorrent.netlet.EventLoop;
import com.datatorrent.netlet.OptimizedEventLoop;

import static com.datatorrent.benchmark.util.SystemUtils.getBoolean;
import static com.datatorrent.benchmark.util.SystemUtils.getInt;
import static java.lang.Thread.sleep;

/**
 * <p>Netlet Coral Block based Benchmark Test Client</p>
 * see: <a href="http://www.coralblocks.com/index.php/2014/04/coralreactor-vs-netty-performance-comparison">http://www.coralblocks.com/index.php/2014/04/coralreactor-vs-netty-performance-comparison</a>,
 * <a href="http://stackoverflow.com/questions/23839437/what-are-the-netty-alternatives-for-high-performance-networking">http://stackoverflow.com/questions/23839437/what-are-the-netty-alternatives-for-high-performance-networking</a>,
 * <a href="http://www.coralblocks.com/NettyBench.zip">http://www.coralblocks.com/NettyBench.zip</a> and
 * <a href="https://groups.google.com/forum/#!topic/mechanical-sympathy/fhbyMnnxmaA">https://groups.google.com/forum/#!topic/mechanical-sympathy/fhbyMnnxmaA</a>
 * <p>run: <code>java -server -verbose:gc -Xms1g -Xmx1g -XX:NewSize=512m -XX:MaxNewSize=512m -DdetailedBenchmarker=true -DmeasureGC=false -DmsgSize=256 -Dmessages=1000000  -DoptimizedEventLoop=true com.datatorrent.benchmark.netlet.BenchmarkTcpClient localhost 8080</code></p>
 * <p>results=Iterations: 1000000 | Avg Time: 28.966 micros | Min Time: 11.0 micros | Max Time: 340.0 micros | 75% = [avg: 26.232 micros, max: 29.0 micros] | 90% = [avg: 27.38 micros, max: 37.0 micros] | 99% = [avg: 28.618 micros, max: 50.0 micros] | 99.9% = [avg: 28.907 micros, max: 80.0 micros] | 99.99% = [avg: 28.957 micros, max: 102.0 micros] | 99.999% = [avg: 28.964 micros, max: 133.0 micros]</p>
 */
public class BenchmarkTcpClient extends AbstractClient
{
  private static final Logger logger = LoggerFactory.getLogger(BenchmarkTcpClient.class);

  private int count = 0;
  private long start;
  private boolean warmingUp = false;
  private boolean benchmarking = false;
  private long tsSent;
  private final ByteBuffer readBuffer;
  private final ByteBuffer sendBuffer;
  private final int messages;
  private final Benchmarker bench = Benchmarker.create();
  private final DefaultEventLoop eventLoop;

  private BenchmarkTcpClient(final String host, final int port) throws IOException, InterruptedException
  {
    super();
    final int msgSize = getInt("msgSize", 256);
    messages = getInt("messages", 1000000);
    readBuffer = ByteBuffer.allocate(msgSize);
    sendBuffer = ByteBuffer.allocate(msgSize);
    eventLoop = getBoolean("optimizedEventLoop", false)?
            new OptimizedEventLoop("OptimizedEventLoop") :new DefaultEventLoop("DefaultEventLoop");
    final Thread eventLoopThread = eventLoop.start();
    eventLoop.connect(new InetSocketAddress(host, port), this);
    eventLoopThread.join();
  }

  @Override
  public ByteBuffer buffer()
  {
    return readBuffer;
  }

  @Override
  public void handleException(Exception e, EventLoop eventLoop)
  {
    logger.error("", e);
    this.eventLoop.stop();
  }

  @Override
  public void connected()
  {
    logger.info("Connected. Sending the first message.");
    start = System.currentTimeMillis();
    warmingUp = true;
    benchmarking = false;
    count = 0;
    send(-1);
  }

  @Override
  public void unregistered(SelectionKey key)
  {
    super.unregistered(key);
    disconnected();
  }

  @Override
  public void disconnected()
  {
    logger.info("Disconnected. Overall test time: {} millis", System.currentTimeMillis() - start);
    bench.printResults(System.out);
    eventLoop.stop();
  }

  @Override
  public void read(int len)
  {
    if (readBuffer.position() != readBuffer.capacity()) {
      logger.error("Read buffer position {} != capacity {}", readBuffer.position(), readBuffer.capacity());
      eventLoop.disconnect(this);
      return;
    }

    readBuffer.flip();
    long tsReceived = readBuffer.getLong();
    if (tsReceived < -2) {
      logger.error("Received bad timestamp {}", tsReceived);
      eventLoop.disconnect(this);
      return;
    } else if (tsReceived != tsSent) {
      logger.error("Received bad timestamp {}. Sent timestamp {}", tsReceived, tsSent);
      eventLoop.disconnect(this);
      return;
    } else if (tsReceived > 0) {
      bench.measure(System.nanoTime() - tsReceived);
    }
    readBuffer.clear();

    send();
  }

  private void send(final long tsSent)
  {
    this.tsSent = tsSent;
    sendBuffer.putLong(tsSent);
    while (sendBuffer.hasRemaining()) {
      sendBuffer.put((byte)'x');
    }
    sendBuffer.flip();
    try {
      while (!send(sendBuffer.array())) {
        sleep(5);
      }
      write();
    } catch (Exception e) {
      logger.error("", e);
      eventLoop.disconnect(this);
      return;
    }
    sendBuffer.clear();
  }

  private void send()
  {
    if (warmingUp) {
      if (++count == messages) {
        logger.info("Finished warming up! Sent {} messages in {} millis", count, System.currentTimeMillis() - start);
        warmingUp = false;
        benchmarking = true;
        count = 0;
        send(System.nanoTime());
      } else {
        send(0);
      }
    } else if (benchmarking) {
      if (++count == messages) {
        send(-2);
        logger.info("Finished sending messages! Sent {} messages.", count);
        eventLoop.disconnect(this);
      } else {
        send(System.nanoTime());
      }
    }
  }

  public static void main(String[] args)
  {
    String host = args[0];
    int port = Integer.parseInt(args[1]);
    try {
      new BenchmarkTcpClient(host, port);
    } catch (IOException e) {
      logger.error("", e);
    } catch (InterruptedException e) {
      logger.error("", e);
    }
  }
}
