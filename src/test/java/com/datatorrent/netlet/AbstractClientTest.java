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
import static java.lang.Thread.sleep;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.netlet.ServerTest.ServerImpl;

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
    public static final int UDP_BUFFER_CAPACITY = 8 * 256 + 1;

    int bufferCapacity;

    ByteBuffer buffer;
    boolean read;

    public ClientImpl(int bufferCapacity) {
      this.bufferCapacity = bufferCapacity;
      buffer = ByteBuffer.allocate(bufferCapacity);
    }

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

        assert (i == bufferCapacity / 8);
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

  @Test
  public void verifySendReceive() throws IOException, InterruptedException
  {
    verifySendReceive(ConnectionType.TCP);
    verifySendReceive(ConnectionType.UDP);
  }

  @SuppressWarnings("SleepWhileInLoop")
  private void verifySendReceive(ConnectionType connectionType) throws IOException, InterruptedException
  {
    ServerImpl si = null;
    ServerTest.UDPServerImpl usi = null;
    int bufferCapacity;
    if (connectionType == ConnectionType.TCP) {
      si = new ServerImpl();
      bufferCapacity = ClientImpl.BUFFER_CAPACITY;
    } else {
      usi = new ServerTest.UDPServerImpl();
      bufferCapacity = ClientImpl.UDP_BUFFER_CAPACITY;
    }
    ClientImpl ci = new ClientImpl(bufferCapacity);

    DefaultEventLoop el = new DefaultEventLoop("test");
    new Thread(el).start();

    if (connectionType == ConnectionType.TCP) {
      el.start("localhost", 5033, si);
    } else {
      el.startUDP("localhost", 5033, usi);
    }

    el.connect(new InetSocketAddress("localhost", 5033), ci, connectionType);

    ByteBuffer outboundBuffer = ByteBuffer.allocate(bufferCapacity);
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

    el.disconnect(ci);
    if (connectionType == ConnectionType.TCP) {
      el.stop(si);
    } else {
      el.stopUDP(usi);
    }
    el.stop();
    assert (ci.read);
  }

  private static final Logger logger = LoggerFactory.getLogger(AbstractClientTest.class);
}