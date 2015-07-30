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
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.benchmark.util.Benchmarker;
import com.datatorrent.netlet.AbstractClient;
import com.datatorrent.netlet.AbstractServer;
import com.datatorrent.netlet.DefaultEventLoop;
import com.datatorrent.netlet.EventLoop;
import com.datatorrent.netlet.OptimizedEventLoop;

import static com.datatorrent.benchmark.util.SystemUtils.getBoolean;
import static com.datatorrent.benchmark.util.SystemUtils.getInt;
import static java.lang.Thread.sleep;

/**
 * <p>Netlet Coral Block based Benchmark Echo Test Server</p>
 * see: <a href="http://www.coralblocks.com/index.php/2014/04/coralreactor-vs-netty-performance-comparison">http://www.coralblocks.com/index.php/2014/04/coralreactor-vs-netty-performance-comparison</a>,
 * <a href="http://stackoverflow.com/questions/23839437/what-are-the-netty-alternatives-for-high-performance-networking">http://stackoverflow.com/questions/23839437/what-are-the-netty-alternatives-for-high-performance-networking</a>,
 * <a href="http://www.coralblocks.com/NettyBench.zip">http://www.coralblocks.com/NettyBench.zip</a> and
 * <a href="https://groups.google.com/forum/#!topic/mechanical-sympathy/fhbyMnnxmaA">https://groups.google.com/forum/#!topic/mechanical-sympathy/fhbyMnnxmaA</a>
 * <p>run: <code>java -server -XX:+PrintGCDetails -Xms2g -Xmx2g -XX:NewSize=756m -XX:MaxNewSize=756m -DdetailedBenchmarker=true -DmeasureGC=false -DmsgSize=256 -DoptimizedEventLoop=true com.datatorrent.benchmark.netlet.EchoTcpServer</code></p>
 * <p>results=Iterations: 1000000 | Avg Time: 14.549 micros | Min Time: 0.0 nanos | Max Time: 120.0 micros | 75% = [avg: 13.236 micros, max: 15.0 micros] | 90% = [avg: 13.716 micros, max: 18.0 micros] | 99% = [avg: 14.375 micros, max: 26.0 micros] | 99.9% = [avg: 14.502 micros, max: 53.0 micros] | 99.99% = [avg: 14.542 micros, max: 72.0 micros] | 99.999% = [avg: 14.548 micros, max: 89.0 micros]</p>
 */
public class EchoTcpServer extends AbstractServer
{
  private static final Logger logger = LoggerFactory.getLogger(EchoTcpServer.class);

  private EchoTcpServer(final String host, final int port) throws IOException, InterruptedException
  {
    super();
    final DefaultEventLoop defaultEventLoop = getBoolean("optimizedEventLoop", false)?
            new OptimizedEventLoop("OptimizedEventLoop") : new DefaultEventLoop("DefaultEventLoop");
    final Thread eventLoopThread = defaultEventLoop.start();
    defaultEventLoop.start(host, port, this);
    eventLoopThread.join();
  }

  @Override
  public void handleException(Exception e, EventLoop eventLoop)
  {
    logger.error("", e);
    eventLoop.stop(this);
    ((DefaultEventLoop)eventLoop).stop();
  }

  @Override
  public ClientListener getClientConnection(SocketChannel client, final ServerSocketChannel server)
  {
    logger.info("{} connected.", client);
    return new AbstractClient()
    {
      private final ByteBuffer buffer = ByteBuffer.allocate(getInt("msgSize", 256));
      private final Benchmarker bench = Benchmarker.create();
      private long start;

      @Override
      public ByteBuffer buffer()
      {
        buffer.clear();
        return buffer;
      }

      @Override
      public void handleException(Exception e, EventLoop eventLoop)
      {
        logger.error("", e);
        eventLoop.stop(EchoTcpServer.this);
      }

      @Override
      public void unregistered(SelectionKey key)
      {
        super.unregistered(key);
        bench.printResults(System.out);
      }

      @Override
      public void read(int len)
      {
        buffer.flip();
        long tsReceived = buffer.getLong();

        if (tsReceived > 0) {
          bench.measure(System.nanoTime() - tsReceived);
        } else if (tsReceived == -1) {
          start = System.currentTimeMillis();
          logger.info("Received the first message.");
        } else if (tsReceived == -2) {
          logger.info("Finished receiving messages! Overall test time: {} millis", System.currentTimeMillis() - start);
          return;
        } else if (tsReceived < 0) {
          logger.error("Received bad timestamp {}", tsReceived);
          return;
        }

        try {
          while (!send(buffer.array())) {
            sleep(5);
          }
          write();
        }
        catch (Exception ie) {
          throw new RuntimeException(ie);
        }
      }
    };
  }

  public static void main(String[] args)
  {
    int port;
    if (args.length > 0) {
      port = Integer.parseInt(args[0]);
    } else {
      port = 8080;
    }

    try {
      new EchoTcpServer("localhost", port);
    } catch (IOException e) {
      logger.error("", e);
    } catch (InterruptedException e) {
      logger.error("", e);
    }
  }
}
