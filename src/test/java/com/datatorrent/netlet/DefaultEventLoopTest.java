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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultEventLoopTest
{
  private static final Logger logger = LoggerFactory.getLogger(DefaultEventLoopTest.class);

  private DefaultEventLoop defaultEventLoop;
  private Thread thread;
  private AtomicInteger count;

  private static class DefaultEventLoopClient extends AbstractClient
  {
    private final ByteBuffer buffer = ByteBuffer.allocate(1024);
    public volatile boolean isConnected;
    public volatile boolean isRegistered;
    private final CountDownLatch connected;
    private final CountDownLatch registered;
    private final CountDownLatch disconnected;

    public DefaultEventLoopClient(CountDownLatch connected, CountDownLatch registered, CountDownLatch disconnected)
    {
      this.connected = connected;
      this.registered = registered;
      this.disconnected = disconnected;
    }

    @Override
    public ByteBuffer buffer()
    {
      return buffer;
    }

    @Override
    public void read(int len)
    {
    }

    @Override
    public void connected()
    {
      super.connected();
      isConnected = true;
      if (connected != null) {
        connected.countDown();
      }
    }

    @Override
    public void disconnected()
    {
      super.disconnected();
      isConnected = false;
      if (disconnected != null) {
        disconnected.countDown();
      }
    }

    @Override
    public void registered(SelectionKey key)
    {
      super.registered(key);
      isRegistered = true;
      if (registered != null) {
        registered.countDown();
      }
    }

    @Override
    public void unregistered(SelectionKey key)
    {
      super.unregistered(key);
      assertSame(this.key, key);
      if (key.isValid()) {
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
      }
      isRegistered = false;
    }
  }

  @Before
  public void setDefaultEventLoop() throws IOException
  {
    defaultEventLoop = DefaultEventLoop.createEventLoop("test");
    thread = defaultEventLoop.start();
    count = new AtomicInteger();
  }

  @After
  public void assertNotActive() throws InterruptedException
  {
    assertFalse("Default event loop " + defaultEventLoop + " not active", defaultEventLoop.isActive());
  }

  @Test
  public void testFullTasksCircularBufferHandlerSameThread() throws InterruptedException
  {
    defaultEventLoop.submit(new Runnable()
    {
      @Override
      public void run()
      {
        synchronized (defaultEventLoop.tasks) {
          defaultEventLoop.tasks.add(new Runnable()
          {
            @Override
            public void run()
            {
            }
          });
        }
        for (int i = 0; i < defaultEventLoop.tasks.capacity(); i++) {
          defaultEventLoop.submit(new Runnable()
          {
            @Override
            public void run()
            {
              count.incrementAndGet();
            }
          });
        }
        defaultEventLoop.submit(new Runnable()
        {
          @Override
          public void run()
          {
            defaultEventLoop.stop();
          }
        });
      }
    });
    thread.join();
    assertEquals(defaultEventLoop.tasks.capacity(), count.get());
  }

  @Test
  public void testFullTasksCircularBufferHandlerDifferentThread() throws InterruptedException
  {
    defaultEventLoop.submit(new Runnable()
    {
      @Override
      public void run()
      {
        try {
          while (defaultEventLoop.tasks.size() < defaultEventLoop.tasks.capacity()) {
            sleep(5);
          }
        } catch (InterruptedException e) {
          fail(e.getMessage());
        }
      }
    });
    for (int i = 0; i < defaultEventLoop.tasks.capacity(); i++) {
      defaultEventLoop.submit(new Runnable()
      {
        @Override
        public void run()
        {
          count.incrementAndGet();
        }
      });
    }
    defaultEventLoop.submit(new Runnable()
    {
      @Override
      public void run()
      {
        defaultEventLoop.stop();
      }
    });
    thread.join();
    assertEquals(defaultEventLoop.tasks.capacity(), count.get());
  }

  @Test
  public void testStop() throws InterruptedException
  {
    final CountDownLatch connected = new CountDownLatch(2);
    final CountDownLatch registered = new CountDownLatch(1);
    final ArrayList<DefaultEventLoopClient> clientConnections = new ArrayList<DefaultEventLoopClient>();

    DefaultEventLoopClient client = new DefaultEventLoopClient(connected, null, null);

    AbstractServer server = new AbstractServer()
    {
      @Override
      public ClientListener getClientConnection(SocketChannel channel, ServerSocketChannel server)
      {
        DefaultEventLoopClient client = new DefaultEventLoopClient(null, connected, null);
        client.isConnected = true;
        clientConnections.add(client);
        return client;
      }

      @Override
      public void registered(SelectionKey key)
      {
        super.registered(key);
        registered.countDown();
      }
    };

    defaultEventLoop.start("localhost", 0, server);
    registered.await();
    defaultEventLoop.connect((InetSocketAddress)server.boundAddress, client);
    connected.await();

    assertTrue(client.isRegistered);
    assertTrue(client.isConnected);
    assertTrue(client.isConnected());

    for (DefaultEventLoopClient clientConnection : clientConnections) {
      assertTrue(clientConnection.isConnected());
      assertTrue(clientConnection.isConnected);
    }

    defaultEventLoop.stop();
    thread.join();

    assertFalse(client.isRegistered);
    assertFalse(client.isConnected);
    assertFalse(client.isConnected());

    for (DefaultEventLoopClient clientConnection : clientConnections) {
      assertFalse(clientConnection.isRegistered);
      assertFalse(clientConnection.isConnected);
      assertFalse(clientConnection.isConnected());
    }
  }

  @Test
  public void testStopWithDisconnectedClient() throws InterruptedException
  {
    final CountDownLatch connected = new CountDownLatch(2);
    final CountDownLatch disconnected = new CountDownLatch(1);
    final CountDownLatch registered = new CountDownLatch(1);

    DefaultEventLoopClient client1 = new DefaultEventLoopClient(connected, null, disconnected);
    DefaultEventLoopClient client2 = new DefaultEventLoopClient(connected, null, null);

    AbstractServer server = new AbstractServer()
    {
      @Override
      public ClientListener getClientConnection(SocketChannel client, ServerSocketChannel server)
      {
        return new DefaultEventLoopClient(null, connected, null);
      }

      @Override
      public void registered(SelectionKey key)
      {
        super.registered(key);
        registered.countDown();
      }
    };

    defaultEventLoop.start("localhost", 0, server);
    registered.await();
    defaultEventLoop.connect((InetSocketAddress)server.boundAddress, client1);
    defaultEventLoop.connect((InetSocketAddress)server.boundAddress, client2);
    connected.await();
    defaultEventLoop.disconnect(client1);
    disconnected.await();

    assertFalse(client1.isConnected());

    defaultEventLoop.stop();
    thread.join();
    assertFalse(client2.isConnected());
  }

  @Test
  public void startStop() throws Exception
  {
    assertTrue(defaultEventLoop.isActive());
    assertEquals(thread, defaultEventLoop.start());
    defaultEventLoop.stop();
    assertTrue(defaultEventLoop.isActive());
    defaultEventLoop.stop();
    thread.join();
  }

  @Test
  public void startWithExtraStop() throws Exception
  {
    assertTrue(defaultEventLoop.isActive());
    defaultEventLoop.stop();
    thread.join();
    assertFalse(defaultEventLoop.isActive());
    try {
      defaultEventLoop.stop();
      fail();
    } catch (IllegalStateException e) {
    }
    thread = defaultEventLoop.start();
    assertTrue(defaultEventLoop.isActive());
    defaultEventLoop.stop();
    thread.join();
  }

  @Test(timeout = 1000)
  public void startKilledEventLoop() throws Exception
  {
    assertTrue(defaultEventLoop.isActive());
    final CountDownLatch latch = new CountDownLatch(1);
    defaultEventLoop.submit(new Runnable()
    {
      @Override
      public void run()
      {
        try {
          latch.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        throw new RuntimeException();
      }
    });
    defaultEventLoop.stop();
    latch.countDown();
    thread.join();
    assertFalse(defaultEventLoop.isActive());
    thread = defaultEventLoop.start();
    assertTrue(defaultEventLoop.isActive());
    defaultEventLoop.submit(new Runnable()
    {
      @Override
      public void run()
      {
        throw new RuntimeException();
      }
    });
    thread.join();
    assertFalse(defaultEventLoop.isActive());
    defaultEventLoop.stop();
    thread = defaultEventLoop.start();
    assertTrue(defaultEventLoop.isActive());
    defaultEventLoop.submit(new Runnable()
    {
      @Override
      public void run()
      {
        throw new RuntimeException();
      }
    });
    thread.join();
    assertFalse(defaultEventLoop.isActive());
    thread = defaultEventLoop.start();
    assertTrue(defaultEventLoop.isActive());
    defaultEventLoop.stop();
    defaultEventLoop.stop();
    thread.join();
  }
}
