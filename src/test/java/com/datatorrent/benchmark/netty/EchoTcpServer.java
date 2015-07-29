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
package com.datatorrent.benchmark.netty;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import com.datatorrent.benchmark.util.Benchmarker;

/**
 * <p>Netty Coral Block based Benchmark Echo Test Server</p>
 * see: <a href="http://www.coralblocks.com/index.php/2014/04/coralreactor-vs-netty-performance-comparison">http://www.coralblocks.com/index.php/2014/04/coralreactor-vs-netty-performance-comparison</a>,
 * <a href="http://stackoverflow.com/questions/23839437/what-are-the-netty-alternatives-for-high-performance-networking">http://stackoverflow.com/questions/23839437/what-are-the-netty-alternatives-for-high-performance-networking</a>,
 * <a href="http://www.coralblocks.com/NettyBench.zip">http://www.coralblocks.com/NettyBench.zip</a> and
 * <a href="https://groups.google.com/forum/#!topic/mechanical-sympathy/fhbyMnnxmaA">https://groups.google.com/forum/#!topic/mechanical-sympathy/fhbyMnnxmaA</a>
 * <p>run: <code>java-server -XX:+PrintGCDetails -Xms2g -Xmx2g -XX:NewSize=756m -XX:MaxNewSize=756m -DdetailedBenchmarker=true -DmeasureGC=false -DmsgSize=256 com.datatorrent.benchmark.netlet.EchoTcpServer</code></p>
 * <p>results=Iterations: 1000000 | Avg Time: 16.014 micros | Min Time: 0.0 nanos | Max Time: 174.0 micros | 75% = [avg: 15.07 micros, max: 16.0 micros] | 90% = [avg: 15.404 micros, max: 18.0 micros] | 99% = [avg: 15.86 micros, max: 26.0 micros] | 99.9% = [avg: 15.973 micros, max: 41.0 micros] | 99.99% = [avg: 16.009 micros, max: 74.0 micros] | 99.999% = [avg: 16.012 micros, max: 89.0 micros]</p>
 */
public class EchoTcpServer extends ChannelHandlerAdapter
{

	private static final Logger logger = LoggerFactory.getLogger(EchoTcpServer.class);

	private final Benchmarker bench = Benchmarker.create();
	private long start;

	public EchoTcpServer() throws IOException
  {
		super();
	}
	
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
  {
      // Close the connection when an exception is raised.
      cause.printStackTrace();
      ctx.close();
  }
	
  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg)
  {
    ByteBuf buf = (ByteBuf)msg;

    long tsReceived = buf.getLong(0);

    if (tsReceived > 0) {
      bench.measure(System.nanoTime() - tsReceived);
    } else if (tsReceived == -1) {
      start = System.currentTimeMillis();
      logger.info("Received the first message.");
    } else if (tsReceived == -2) {
      logger.info("Finished receiving messages! Overall test time: {} millis", System.currentTimeMillis() - start);
      ctx.close();
      bench.printResults(System.out);
      return;
    } else if (tsReceived < 0) {
      logger.error("Received bad timestamp {}", tsReceived);
      ctx.close();
      return;
    }

		ctx.writeAndFlush(msg);
	}
	
	public static void main(String[] args) throws Exception
  {
		int port;
		if (args.length > 0) {
				port = Integer.parseInt(args[0]);
		} else {
				port = 8080;
		}
		
		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
			        .childHandler(new ChannelInitializer<SocketChannel>() {
				                @Override
				                public void initChannel(SocketChannel ch) throws Exception {
					                ch.pipeline().addLast(new EchoTcpServer());
				                }
			                }).option(ChannelOption.SO_BACKLOG, 128)
			        .childOption(ChannelOption.SO_KEEPALIVE, true);

			ChannelFuture f = b.bind(port).sync(); // (7)
			f.channel().closeFuture().sync();
		} finally {
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
	}
}
