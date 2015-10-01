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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.netlet.ProtocolHandler.ClientProtocolHandler;
import com.datatorrent.netlet.ProtocolHandler.DisconnectingClientHandler;
import com.datatorrent.netlet.ProtocolHandler.ServerProtocolHandler;
import com.datatorrent.netlet.util.CircularBuffer;

/**
 * <p>
 * DefaultEventLoop class.</p>
 *
 * @since 1.0.0
 */
public class DefaultEventLoop implements Runnable, EventLoop, ProtocolDriver
{
  public static final String eventLoopPropertyName = "com.datatorrent.netlet.disableOptimizedEventLoop";
  public static DefaultEventLoop createEventLoop(final String id) throws IOException
  {
    final String disableOptimizedEventLoop = System.getProperty(eventLoopPropertyName);
    if (disableOptimizedEventLoop == null || disableOptimizedEventLoop.equalsIgnoreCase("false") || disableOptimizedEventLoop.equalsIgnoreCase("no")) {
      return new OptimizedEventLoop(id);
    } else {
      @SuppressWarnings("deprecation")
      DefaultEventLoop eventLoop = new DefaultEventLoop(id);
      return eventLoop;
    }
  }

  public final String id;
  protected final Selector selector;
  protected final CircularBuffer<Runnable> tasks;
  protected boolean alive;
  private int refCount;
  private Thread eventThread;

  /**
   * @deprecated use factory method {@link #createEventLoop(String)}
   * @param id of the event loop
   * @throws IOException
   */
  @Deprecated
  public DefaultEventLoop(String id) throws IOException
  {
    this.tasks = new CircularBuffer<Runnable>(1024, 5);
    this.id = id;
    selector = Selector.open();
  }

  public synchronized Thread start()
  {
    if (++refCount == 1) {
      (eventThread = new Thread(this, id)).start();
    }
    return eventThread;
  }

  public void stop()
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        synchronized (DefaultEventLoop.this) {
          if (--refCount == 0) {
            alive = false;
            selector.wakeup();
          }
        }
      }

      @Override
      public String toString()
      {
        return String.format("stop{%d}", refCount);
      }

    });
  }

  @Override
  public void run()
  {
    synchronized (this) {
      if (eventThread == null) {
        refCount++;
        eventThread = Thread.currentThread();
      } else if (eventThread != Thread.currentThread()) {
        throw new IllegalStateException("DefaultEventLoop can not run in two [" + eventThread.getName() + "] and ["
                + Thread.currentThread().getName() + "] threads.");
      }
    }
    alive = true;

    try {
      runEventLoop();
    }
    finally {
      if (alive) {
        alive = false;
        logger.warn("Unexpected termination of {}", this);
      }
      eventThread = null;
    }
  }

  @SuppressWarnings({"SleepWhileInLoop", "ConstantConditions"})
  protected void runEventLoop()
  {
    //logger.debug("Starting {}", this);
    final Iterator<SelectionKey> EMPTY_ITERATOR = new Iterator<SelectionKey>()
    {

      @Override
      public boolean hasNext()
      {
        return false;
      }

      @Override
      public SelectionKey next()
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void remove()
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

    };

    final Set<SelectionKey> EMPTY_SET = new Set<SelectionKey>()
    {
      @Override
      public int size()
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public boolean isEmpty()
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public boolean contains(Object o)
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public Iterator<SelectionKey> iterator()
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public Object[] toArray()
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public <T> T[] toArray(T[] a)
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public boolean add(SelectionKey e)
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public boolean remove(Object o)
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public boolean containsAll(Collection<?> c)
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public boolean addAll(Collection<? extends SelectionKey> c)
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public boolean retainAll(Collection<?> c)
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public boolean removeAll(Collection<?> c)
      {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void clear()
      {
      }

    };

    SelectionKey sk = null;
    Set<SelectionKey> selectedKeys = EMPTY_SET;
    Iterator<SelectionKey> iterator = EMPTY_ITERATOR;

    do {
      try {
        do {
          if (!iterator.hasNext()) {
            int size = tasks.size();
            if (size > 0) {
              do {
                tasks.pollUnsafe().run();
              }
              while (--size > 0);
              size = selector.selectNow();
            }
            else {
              size = selector.select(100);
            }

            if (size > 0) {
              selectedKeys = selector.selectedKeys();
              iterator = selectedKeys.iterator();
            }
            else {
              iterator = EMPTY_ITERATOR;
            }
          }

          while (iterator.hasNext()) {
            if (!(sk = iterator.next()).isValid()) {
              continue;
            }
            handleSelectedKey(sk);
          }

          selectedKeys.clear();
        }
        while (alive);
      }
      catch (Exception ex) {
        if (sk == null) {
          logger.warn("Unexpected exception not related to SelectionKey", ex);
        }
        else {
          logger.warn("Exception on unregistered SelectionKey {}", sk, ex);
          ProtocolHandler handler = (ProtocolHandler)sk.attachment();
          if (handler != null) {
            handler.handleException(ex, this);
          }
        }
      }
    }
    while (alive);
    //logger.debug("Terminated {}", this);
  }

  protected final void handleSelectedKey(final SelectionKey sk) throws IOException
  {
    ProtocolHandler handler = (ProtocolHandler)sk.attachment();
    handler.handleSelectedKey(sk);
  }

  @Override
  public void submit(Runnable r)
  {
    Thread currentThread = Thread.currentThread();
    //logger.debug("{}.{}.{}", currentThread, r, eventThread);
    if (tasks.isEmpty() && eventThread == currentThread) {
      r.run();
    }
    else {
      synchronized (tasks) {
        tasks.add(r);
        selector.wakeup();
      }
    }
  }

  public void register(final SelectableChannel c, final int ops, final ProtocolHandler handler)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        try {
          handler.registered(c.register(selector, ops, handler));
        }
        catch (ClosedChannelException cce) {
          handler.handleException(cce, DefaultEventLoop.this);
        }
      }

      @Override
      public String toString()
      {
        return String.format("register(%s, %d, %s)", c, ops, handler);
      }

    });
  }

  //@Override
  public void unregister(final ProtocolHandler handler)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        for (SelectionKey key : selector.keys()) {
          if (key.channel() == handler) {
            ((Listener)key.attachment()).unregistered(key);
            key.interestOps(0);
            key.attach(ProtocolHandler.NOOP_HANDLER);
          }
        }
      }

      @Override
      public String toString()
      {
        return String.format("unregister(%s)", handler);
      }

    });
  }

  /*
  //@Override
  public void register(ServerSocketChannel channel, Listener l)
  {
    register(channel, SelectionKey.OP_ACCEPT, l);
  }

  //@Override
  public void register(SocketChannel channel, int ops, Listener l)
  {
    register((AbstractSelectableChannel)channel, ops, l);
  }
  */

  @Override
  public final void connect(final InetSocketAddress address, final ClientProtocolHandler handler)
  {
    handler.init(this);
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        handler.connect(address);
      }

      @Override
      public String toString()
      {
        return String.format("connect(%s, %s)", address, handler);
      }

    });
  }

  @Override
  public final void disconnect(final ClientProtocolHandler handler)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        for (SelectionKey key : selector.keys()) {
          if (key.attachment() == handler) {
            try {
              handler.unregistered(key);
            }
            finally {

              boolean disconnected = true;
              if (key.isValid()) {
                if ((key.interestOps() & SelectionKey.OP_WRITE) != 0) {
                  key.attach(new DisconnectingClientHandler(key));
                  disconnected = false;
                }
              }

              if (disconnected) {
                try {
                  key.attach(ClientProtocolHandler.NOOP_CLIENT_HANDLER);
                  key.channel().close();
                }
                catch (IOException io) {
                  handler.handleException(io, DefaultEventLoop.this);
                }
              }
            }
          }
        }
      }

      @Override
      public String toString()
      {
        return String.format("disconnect(%s)", handler);
      }

    });
  }

  @Override
  public final void start(final String host, final int port, final ServerProtocolHandler handler)
  {
    handler.init(this);
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        handler.start(host, port);
      }

      @Override
      public String toString()
      {
        return String.format("start(%s, %d, %s)", host, port, handler);
      }

    });
  }

  @Override
  public final void stop(final ServerProtocolHandler handler)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        for (SelectionKey key : selector.keys()) {
          if (key.attachment() == handler) {
            if (key.isValid()) {
              handler.unregistered(key);
              key.cancel();
            }
            key.attach(ProtocolHandler.NOOP_HANDLER);
            try {
              key.channel().close();
            }
            catch (IOException io) {
              handler.handleException(io, DefaultEventLoop.this);
            }
          }
        }
      }

      @Override
      public String toString()
      {
        return String.format("stop(%s)", handler);
      }

    });
  }

  public boolean isActive()
  {
    return eventThread != null && eventThread.isAlive();
  }

  @Override
  public String toString()
  {
    return "{id=" + id + ", " + tasks + '}';
  }

  private static final Logger logger = LoggerFactory.getLogger(DefaultEventLoop.class);
}
