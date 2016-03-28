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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultEventLoopTest
{
  private static final Logger logger = LoggerFactory.getLogger(DefaultEventLoopTest.class);

  private DefaultEventLoop defaultEventLoop;
  private Thread thread;
  private AtomicInteger count;

  private static class DefaultEventLoopCLient extends AbstractClient
  {
    private final ByteBuffer buffer = ByteBuffer.allocate(1024);
    public volatile boolean isConnected;
    public volatile boolean isRegistered;

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
    }

    @Override
    public void disconnected()
    {
      super.disconnected();
      isConnected = false;
    }

    @Override
    public void registered(SelectionKey key)
    {
      super.registered(key);
      isRegistered = true;
    }

    @Override
    public void unregistered(SelectionKey key)
    {
      super.unregistered(key);
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
    final Lock lock = new ReentrantLock();
    final Condition connected = lock.newCondition();
    final Condition registered = lock.newCondition();
    final ArrayList<DefaultEventLoopCLient> clientConnections = new ArrayList<DefaultEventLoopCLient>();

    DefaultEventLoopCLient client = new DefaultEventLoopCLient()
    {
      @Override
      public void connected()
      {
        super.connected();
        lock.lock();
        connected.signal();
        isConnected = true;
        lock.unlock();
      }

    };

    AbstractServer server = new AbstractServer()
    {
      @Override
      public ClientListener getClientConnection(SocketChannel channel, ServerSocketChannel server)
      {
        DefaultEventLoopCLient client =  new DefaultEventLoopCLient();
        clientConnections.add(client);
        return client;
      }

      @Override
      public void registered(SelectionKey key)
      {
        super.registered(key);
        lock.lock();
        registered.signal();
        lock.unlock();
      }
    };

    defaultEventLoop.start("localhost", 0, server);
    lock.lock();
    registered.awaitUninterruptibly();
    lock.unlock();
    defaultEventLoop.connect((InetSocketAddress)server.boundAddress, client);
    lock.lock();
    connected.awaitUninterruptibly();
    lock.unlock();

    assertTrue(client.isRegistered);
    assertTrue(client.isConnected);
    assertTrue(client.isConnected());

    defaultEventLoop.stop();
    thread.join();
    assertFalse(client.isRegistered);
    assertFalse(client.isConnected);
    assertFalse(client.isConnected());
    assertFalse(client.key.isValid());
    assertFalse(client.key.channel().isOpen());
    for (DefaultEventLoopCLient  clientConnection : clientConnections) {
      assertFalse(clientConnection.isRegistered);
      assertFalse(clientConnection.isConnected);
      assertFalse(clientConnection.isConnected());
      assertFalse(clientConnection.key.isValid());
      assertFalse(clientConnection.key.channel().isOpen());
    }
  }

  @Test
  public void testStopWithDisconnectedClient() throws InterruptedException
  {
    final Lock lock = new ReentrantLock();
    final Condition connected = lock.newCondition();
    final Condition disconnected = lock.newCondition();
    final Condition registered = lock.newCondition();

    DefaultEventLoopCLient client1 = new DefaultEventLoopCLient()
    {
      @Override
      public void connected()
      {
        super.connected();
        lock.lock();
        connected.signal();
        lock.unlock();
      }

      @Override
      public void disconnected()
      {
        super.disconnected();
        lock.lock();
        disconnected.signal();
        lock.unlock();
      }
    };

    DefaultEventLoopCLient client2 = new DefaultEventLoopCLient()
    {
      @Override
      public void connected()
      {
        super.connected();
        lock.lock();
        connected.signal();
        lock.unlock();
      }
    };

    AbstractServer server = new AbstractServer()
    {
      @Override
      public ClientListener getClientConnection(SocketChannel client, ServerSocketChannel server)
      {
        return new DefaultEventLoopCLient();
      }

      @Override
      public void registered(SelectionKey key)
      {
        super.registered(key);
        lock.lock();
        registered.signal();
        lock.unlock();
      }
    };

    defaultEventLoop.start("localhost", 0, server);
    lock.lock();
    registered.awaitUninterruptibly();
    lock.unlock();
    defaultEventLoop.connect((InetSocketAddress)server.boundAddress, client1);
    lock.lock();
    connected.awaitUninterruptibly();
    lock.unlock();
    defaultEventLoop.connect((InetSocketAddress)server.boundAddress, client2);
    lock.lock();
    connected.awaitUninterruptibly();
    lock.unlock();
    defaultEventLoop.disconnect(client1);
    lock.lock();
    disconnected.awaitUninterruptibly();
    lock.unlock();
    assertFalse(client1.isConnected());
    assertFalse(client1.key.isValid());


    defaultEventLoop.stop();
    thread.join();
    assertFalse(client2.isConnected());
  }

}
