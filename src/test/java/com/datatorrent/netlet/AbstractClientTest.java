/*
 * Copyright (c) 2013 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.netlet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.netlet.ServerTest.ServerImpl;
import com.datatorrent.netlet.protocols.tcp.TcpClientHandler;
import com.datatorrent.netlet.protocols.tcp.TcpServerHandler;

import static java.lang.Thread.sleep;

public class AbstractClientTest
{
  public AbstractClientTest()
  {
  }

  @Override
  public String toString()
  {
    return "ClientTest{" + '}';
  }

  public class ClientImpl extends AbstractClient
  {
    public static final int BUFFER_CAPACITY = 8 * 1024 + 1;
    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_CAPACITY);
    boolean read;

    @Override
    public String toString()
    {
      return "ClientImpl{" + "buffer=" + buffer + ", read=" + read + '}';
    }

    @Override
    public ByteBuffer buffer()
    {
      return buffer;
    }

    @Override
    public void read(int len)
    {
      if (buffer.position() == buffer.capacity()) {
        buffer.flip();
        read = true;

        int i = 0;
        LongBuffer lb = buffer.asLongBuffer();
        while (lb.hasRemaining()) {
          Assert.assertEquals(i++, lb.get());
        }

        assert (i == BUFFER_CAPACITY / 8);
      }
    }

    @Override
    public void connected()
    {
    }

    @Override
    public void disconnected()
    {
    }

  }

  public void sendData()
  {
  }

  @SuppressWarnings("SleepWhileInLoop")
  private void verifySendReceive(final DefaultEventLoop el, final int port) throws IOException, InterruptedException
  {
    ServerImpl si = new ServerImpl();
    ClientImpl ci = new ClientImpl();

    TcpServerHandler serverHandler = new TcpServerHandler(si);
    TcpClientHandler clientHandler = new TcpClientHandler(ci);

    new Thread(el).start();

    el.start("localhost", port, serverHandler);
    el.connect(new InetSocketAddress("localhost", port), clientHandler);

    ByteBuffer outboundBuffer = ByteBuffer.allocate(ClientImpl.BUFFER_CAPACITY);
    LongBuffer lb = outboundBuffer.asLongBuffer();

    int i = 0;
    while (lb.hasRemaining()) {
      lb.put(i++);
    }

    boolean odd = false;
    outboundBuffer.position(i * 8);
    while (outboundBuffer.hasRemaining()) {
      outboundBuffer.put((byte)(odd ? 0x55 : 0xaa));
      odd = !odd;
    }

    byte[] array = outboundBuffer.array();
    while (!ci.send(array, 0, array.length)) {
      sleep(5);
    }

    sleep(100);

    el.disconnect(clientHandler);
    el.stop(serverHandler);
    el.stop();
    Assert.assertTrue(ci.read);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testWithDefault() throws IOException, InterruptedException
  {
    verifySendReceive(new DefaultEventLoop("test"), 5033);
  }

  @Test
  public void testWithOptimized() throws IOException, InterruptedException
  {
    verifySendReceive(new OptimizedEventLoop("test"), 5034);
  }

  @Test
  public void testCreateEventLoop() throws IOException
  {
    Assert.assertEquals(OptimizedEventLoop.class, DefaultEventLoop.createEventLoop("test").getClass());
    System.setProperty(DefaultEventLoop.eventLoopPropertyName, "");
    Assert.assertEquals(DefaultEventLoop.class, DefaultEventLoop.createEventLoop("test").getClass());
    System.setProperty(DefaultEventLoop.eventLoopPropertyName, "false");
    Assert.assertEquals(OptimizedEventLoop.class, DefaultEventLoop.createEventLoop("test").getClass());
    System.setProperty(DefaultEventLoop.eventLoopPropertyName, "true");
    Assert.assertEquals(DefaultEventLoop.class, DefaultEventLoop.createEventLoop("test").getClass());
    System.setProperty(DefaultEventLoop.eventLoopPropertyName, "no");
    Assert.assertEquals(OptimizedEventLoop.class, DefaultEventLoop.createEventLoop("test").getClass());
    System.setProperty(DefaultEventLoop.eventLoopPropertyName, "yes");
    Assert.assertEquals(DefaultEventLoop.class, DefaultEventLoop.createEventLoop("test").getClass());
  }

  private static final Logger logger = LoggerFactory.getLogger(AbstractClientTest.class);
}