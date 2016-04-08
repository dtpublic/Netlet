/**
 * Copyright (C) 2016 DataTorrent, Inc.
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
package com.datatorrent.netlet;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.netlet.util.CircularBuffer;
import com.datatorrent.netlet.util.Slice;

import static com.datatorrent.netlet.AbstractClientTest.verifyExceptionsInClientCallback;
import static com.datatorrent.netlet.AbstractClientTest.verifyUnresolvedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AbstractLengthPrependerClientTest
{
  private static final Logger logger = LoggerFactory.getLogger(AbstractLengthPrependerClientTest.class);
  private static final int COUNT = 256;

  @Test
  public void testDisconnectOnResponse() throws IOException, InterruptedException
  {
    final Slice msg = new Slice("test".getBytes());
    final DefaultEventLoop serverEventLoop = DefaultEventLoop.createEventLoop("server");
    serverEventLoop.start();
    final CountDownLatch registered = new CountDownLatch(1);
    final CountDownLatch serverDisconnects = new CountDownLatch(COUNT);
    final AbstractServer server = new AbstractServer()
    {
      @Override
      public ClientListener getClientConnection(SocketChannel client, ServerSocketChannel server)
      {
        return new AbstractLengthPrependerClient()
        {
          @Override
          public void disconnected()
          {
            super.disconnected();
            serverDisconnects.countDown();
          }

          @Override
          public void onMessage(byte[] buffer, int offset, int size)
          {
            assertEquals(msg, new Slice(buffer, offset, size));
            write(buffer, offset, size);
          }
        };
      }

      @Override
      public void registered(SelectionKey key)
      {
        super.registered(key);
        registered.countDown();
      }
    };

    serverEventLoop.start("localhost", 0, server);
    registered.await();

    final DefaultEventLoop clientEventLoop = DefaultEventLoop.createEventLoop("client");
    try {
      Field task = DefaultEventLoop.class.getDeclaredField("tasks");
      task.setAccessible(true);
      task.set(clientEventLoop, new CircularBuffer<Runnable>(32, 5));
    } catch (Exception e) {
      logger.error("", e);
      fail();
    }
    clientEventLoop.start();
    final CountDownLatch clientDisconnects = new CountDownLatch(COUNT);
    int count = 0;
    while (count++ < COUNT) {
      final AbstractLengthPrependerClient client = new AbstractLengthPrependerClient()
      {
        @Override
        public void disconnected()
        {
          clientDisconnects.countDown();
        }

        @Override
        public void onMessage(byte[] buffer, int offset, int size)
        {
          assertEquals(msg, new Slice(buffer, offset, size));
          clientEventLoop.disconnect(this);
        }

        @Override
        public void handleException(Exception cce, EventLoop el)
        {
          clientDisconnects.countDown();
          super.handleException(cce, el);
        }
      };
      clientEventLoop.connect((InetSocketAddress)server.boundAddress, client);
      client.write(msg.buffer);
    }
    clientDisconnects.await();
    serverDisconnects.await();
    clientEventLoop.stop();
    serverEventLoop.stop(server);
    serverEventLoop.stop();
  }

  @org.junit.Ignore
  @Test
  public void testConnectDisconnect() throws IOException, InterruptedException
  {
    DefaultEventLoop eventLoop = DefaultEventLoop.createEventLoop("test");
    Thread thread = eventLoop.start();
    final CountDownLatch registered = new CountDownLatch(1);
    AbstractServer server = new AbstractServer()
    {
      @Override
      public void registered(SelectionKey key)
      {
        super.registered(key);
        registered.countDown();
      }

      @Override
      public ClientListener getClientConnection(SocketChannel client, ServerSocketChannel server)
      {
        return new AbstractLengthPrependerClient()
        {
          @Override
          public void onMessage(byte[] buffer, int offset, int size)
          {

          }
        };
      }
    };
    eventLoop.start("localhost", 0, server);
    registered.await();

    final AbstractLengthPrependerClient client = new AbstractLengthPrependerClient()
    {
      @Override
      public void connected()
      {
        super.connected();
      }

      @Override
      public void disconnected()
      {
        super.disconnected();
      }

      @Override
      public void onMessage(byte[] buffer, int offset, int size)
      {

      }
    };
    eventLoop.connect((InetSocketAddress)server.boundAddress, client);
    eventLoop.disconnect(client);
    thread.join();
  }

  @Test
  public void testUnresolvedException() throws IOException, InterruptedException
  {
    final DefaultEventLoop eventLoop = DefaultEventLoop.createEventLoop("test");
    final CountDownLatch handled = new CountDownLatch(1);
    final AbstractLengthPrependerClient ci = new AbstractLengthPrependerClient()
    {
      @Override
      public void onMessage(byte[] buffer, int offset, int size)
      {
        fail();
      }

      @Override
      public void handleException(Exception cce, EventLoop el)
      {
        assertSame(el, eventLoop);
        assertTrue(cce instanceof RuntimeException);
        assertTrue(cce.getCause() instanceof UnresolvedAddressException);
        super.handleException(cce, el);
        handled.countDown();
      }
    };
    verifyUnresolvedException(ci, eventLoop, handled);
  }

  @Test
  public void testExceptionsInClientCallback() throws IOException, InterruptedException
  {
    final DefaultEventLoop eventLoop = DefaultEventLoop.createEventLoop("test");
    final CountDownLatch handled = new CountDownLatch(1);

    final AbstractLengthPrependerClient client = new AbstractLengthPrependerClient()
    {
      RuntimeException exception;
      @Override
      public void connected()
      {
        exception = new RuntimeException();
        throw exception;
      }

      @Override
      public void handleException(Exception cce, EventLoop el)
      {
        assertSame(exception, cce);
        assertSame(eventLoop, el);
        super.handleException(cce, el);
        handled.countDown();
      }

      @Override
      public void onMessage(byte[] buffer, int offset, int size)
      {

      }
    };
    verifyExceptionsInClientCallback(client, eventLoop, handled);
  }
}
