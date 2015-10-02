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
package com.datatorrent.netlet.benchmark.netlet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.netlet.AbstractClient;
import com.datatorrent.netlet.DefaultEventLoop;
import com.datatorrent.netlet.ProtocolHandler;
import com.datatorrent.netlet.benchmark.util.BenchmarkConfiguration;
import com.datatorrent.netlet.benchmark.util.BenchmarkResults;
import com.datatorrent.netlet.protocols.tcp.TcpClientHandler;

import static java.lang.Thread.sleep;

/**
 * <p>Netlet Coral Block based Benchmark Test Client</p>
 * see: <a href="http://www.coralblocks.com/index.php/2014/04/coralreactor-vs-netty-performance-comparison">http://www.coralblocks.com/index.php/2014/04/coralreactor-vs-netty-performance-comparison</a>,
 * <a href="http://stackoverflow.com/questions/23839437/what-are-the-netty-alternatives-for-high-performance-networking">http://stackoverflow.com/questions/23839437/what-are-the-netty-alternatives-for-high-performance-networking</a>,
 * <a href="http://www.coralblocks.com/NettyBench.zip">http://www.coralblocks.com/NettyBench.zip</a> and
 * <a href="https://groups.google.com/forum/#!topic/mechanical-sympathy/fhbyMnnxmaA">https://groups.google.com/forum/#!topic/mechanical-sympathy/fhbyMnnxmaA</a>
 * <p>run: <code>mvn exec:exec -Dbenchmark=netlet.client</code></p>
 * <p>results=Iterations: 1000000 | Avg Time: 28.386 micros | Min Time: 15.0 micros | Max Time: 167.0 micros | 75% Time: 28.0 micros | 90% Time: 36.0 micros | 99% Time: 47.0 micros | 99.9% Time: 76.0 micros | 99.99% Time: 94.0 micros | 99.999% Time: 115.0 micros</p>
 */
public class BenchmarkTcpClient extends AbstractClient
{
  private static final Logger logger = LoggerFactory.getLogger(BenchmarkTcpClient.class);

  private int count = 0;
  private long start;
  private boolean warmingUp = false;
  private boolean benchmarking = false;
  private long timestamp;
  private final ByteBuffer readByteBuffer = ByteBuffer.allocate(BenchmarkConfiguration.messageSize);
  private final ByteBuffer sendByteBuffer = ByteBuffer.allocate(BenchmarkConfiguration.messageSize);
  private final BenchmarkResults benchmarkResults = new BenchmarkResults(BenchmarkConfiguration.messageCount);
  private final DefaultEventLoop eventLoop = DefaultEventLoop.createEventLoop("EventLoop");
  private final TcpClientHandler handler = new TcpClientHandler(this);

  private BenchmarkTcpClient(final String host, final int port) throws IOException, InterruptedException
  {
    super();
    final Thread eventLoopThread = eventLoop.start();
    eventLoop.connect(new InetSocketAddress(host, port), handler);
    eventLoopThread.join();
  }

  @Override
  public ByteBuffer buffer()
  {
    return readByteBuffer;
  }

  @Override
  public void handleException(Exception e, ProtocolHandler handler)
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
    benchmarkResults.printResults(System.out);
    eventLoop.stop();
  }

  @Override
  public void read(int len)
  {
    if (readByteBuffer.position() != readByteBuffer.capacity()) {
      logger.error("Read buffer position {} != capacity {}", readByteBuffer.position(), readByteBuffer.capacity());
      handler.disconnectConnection();
      return;
    }

    readByteBuffer.flip();
    long timestamp = readByteBuffer.getLong();
    if (timestamp < -2) {
      logger.error("Received bad timestamp {}", timestamp);
      handler.disconnectConnection();
      return;
    } else if (timestamp != this.timestamp) {
      logger.error("Received bad timestamp {}. Sent timestamp {}", timestamp, this.timestamp);
      handler.disconnectConnection();
      return;
    } else if (timestamp > 0) {
      benchmarkResults.addResult(System.nanoTime() - timestamp);
    }
    readByteBuffer.clear();

    send();
  }

  private void send(final long tsSent)
  {
    this.timestamp = tsSent;
    sendByteBuffer.putLong(tsSent);
    while (sendByteBuffer.hasRemaining()) {
      sendByteBuffer.put((byte)'x');
    }
    sendByteBuffer.flip();
    try {
      while (!send(sendByteBuffer.array())) {
        sleep(5);
      }
      write();
    } catch (Exception e) {
      logger.error("", e);
      handler.disconnectConnection();
      return;
    }
    sendByteBuffer.clear();
  }

  private void send()
  {
    if (warmingUp) {
      if (++count == BenchmarkConfiguration.messageCount) {
        logger.info("Finished warming up! Sent {} messages in {} millis", count, System.currentTimeMillis() - start);
        warmingUp = false;
        benchmarking = true;
        count = 0;
        send(System.nanoTime());
      } else {
        send(0);
      }
    } else if (benchmarking) {
      if (++count == BenchmarkConfiguration.messageCount) {
        send(-2);
        logger.info("Finished sending messages! Sent {} messages.", count);
        handler.disconnectConnection();
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
