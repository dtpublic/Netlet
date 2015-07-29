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
import java.nio.channels.*;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.netlet.Listener.ClientListener;
import com.datatorrent.netlet.Listener.ServerListener;
import com.datatorrent.netlet.util.CircularBuffer;

/**
 * <p>
 * DefaultEventLoop class.</p>
 *
 * @since 1.0.0
 */
public class DefaultEventLoop implements Runnable, EventLoop
{
  public static final String eventLoopPropertyName = "com.datatorrent.netlet.disableOptimizedEventLoop";
  @SuppressWarnings("deprecation")
  public static DefaultEventLoop createEventLoop(final String id) throws IOException
  {
    final String disableOptimizedEventLoop = System.getProperty(eventLoopPropertyName);
    if (disableOptimizedEventLoop == null || disableOptimizedEventLoop.equalsIgnoreCase("true") || disableOptimizedEventLoop.equalsIgnoreCase("yes")) {
      return new DefaultEventLoop(id);
    } else {
      return new OptimizedEventLoop(id);
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
          Listener l = (Listener)sk.attachment();
          if (l != null) {
            l.handleException(ex, this);
          }
        }
      }
    }
    while (alive);
    //logger.debug("Terminated {}", this);
  }

  protected final void handleSelectedKey(final SelectionKey sk) throws IOException
  {
    switch (sk.readyOps()) {
      case SelectionKey.OP_ACCEPT:
        ServerSocketChannel ssc = (ServerSocketChannel)sk.channel();
        SocketChannel sc = ssc.accept();
        sc.configureBlocking(false);
        final ServerListener sl = (ServerListener)sk.attachment();
        final ClientListener l = sl.getClientConnection(sc, (ServerSocketChannel)sk.channel());
        register(sc, SelectionKey.OP_READ | SelectionKey.OP_WRITE, l);
        break;

      case SelectionKey.OP_CONNECT:
        if (((SocketChannel)sk.channel()).finishConnect()) {
          ((ClientListener)sk.attachment()).connected();
        }
        break;

      case SelectionKey.OP_READ:
        ((ClientListener)sk.attachment()).read();
        break;

      case SelectionKey.OP_WRITE:
        ((ClientListener)sk.attachment()).write();
        break;

      case SelectionKey.OP_READ | SelectionKey.OP_WRITE:
        ((ClientListener)sk.attachment()).read();
        ((ClientListener)sk.attachment()).write();
        break;

      case SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT:
      case SelectionKey.OP_READ | SelectionKey.OP_CONNECT:
      case SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT:
        if (((SocketChannel)sk.channel()).finishConnect()) {
          ((ClientListener)sk.attachment()).connected();
          if (sk.isReadable()) {
            ((ClientListener)sk.attachment()).read();
          }
          if (sk.isWritable()) {
            ((ClientListener)sk.attachment()).write();
          }
        }
        break;

      default:
        logger.warn("!!!!!! not sure what interest this is {} !!!!!!", Integer.toBinaryString(sk.readyOps()));
        break;
    }

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

  private void register(final SelectableChannel c, final int ops, final Listener l)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        try {
          l.registered(c.register(selector, ops, l));
        }
        catch (ClosedChannelException cce) {
          l.handleException(cce, DefaultEventLoop.this);
        }
      }

      @Override
      public String toString()
      {
        return String.format("register(%s, %d, %s)", c, ops, l);
      }

    });
  }

  //@Override
  public void unregister(final SelectableChannel c)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        for (SelectionKey key : selector.keys()) {
          if (key.channel() == c) {
            ((Listener)key.attachment()).unregistered(key);
            key.interestOps(0);
            key.attach(Listener.NOOP_LISTENER);
          }
        }
      }

      @Override
      public String toString()
      {
        return String.format("unregister(%s)", c);
      }

    });
  }

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

  @Override
  public final void connect(final InetSocketAddress address, final ClientListener l)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        SocketChannel channel = null;
        try {
          channel = SocketChannel.open();
          channel.configureBlocking(false);
          if (channel.connect(address)) {
            l.connected();
            register(channel, SelectionKey.OP_READ, l);
          }
          else {
            /*
             * According to the spec SelectionKey.OP_READ is not necessary here, but without it
             * occasionally channel key will not be selected after connection is established and finishConnect()
             * will return true.
             */
            register(channel, SelectionKey.OP_CONNECT | SelectionKey.OP_READ, new ClientListener()
            {
              private SelectionKey key;

              @Override
              public void read() throws IOException
              {
                logger.debug("missing OP_CONNECT");
                connected();
                l.read();
              }

              @Override
              public void write() throws IOException
              {
                logger.debug("missing OP_CONNECT");
                connected();
                l.write();
              }

              @Override
              public void connected()
              {
                logger.debug("{}", this);
                key.attach(l);
                l.connected();
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
              }

              @Override
              public void disconnected()
              {
                /*
                 * Expectation is that connected() or read() will be called and this ClientListener will be replaced
                 * by the original ClientListener in the key attachment before disconnect is initiated. In any case
                 * as original Client Listener was never attached to the key, this method will never be called. Please
                 * see DefaultEventLoop.disconnect().
                 */
                logger.debug("missing OP_CONNECT {}", this);
                throw new NotYetConnectedException();
              }

              @Override
              public void handleException(Exception exception, EventLoop eventloop)
              {
                key.attach(l);
                l.handleException(exception, eventloop);
              }

              @Override
              public void registered(SelectionKey key)
              {
                l.registered(this.key = key);
              }

              @Override
              public void unregistered(SelectionKey key)
              {
                l.unregistered(key);
              }

              @Override
              public String toString()
              {
                return "Pre-connect Client listener for " + l.toString();
              }

            });
          }
        }
        catch (IOException ie) {
          l.handleException(ie, DefaultEventLoop.this);
          if (channel != null && channel.isOpen()) {
            try {
              channel.close();
            }
            catch (IOException io) {
              l.handleException(io, DefaultEventLoop.this);
            }
          }
        }
      }

      @Override
      public String toString()
      {
        return String.format("connect(%s, %s)", address, l);
      }

    });
  }

  @Override
  public final void disconnect(final ClientListener l)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        for (SelectionKey key : selector.keys()) {
          if (key.attachment() == l) {
            try {
              l.unregistered(key);
            }
            finally {

              boolean disconnected = true;
              if (key.isValid()) {
                if ((key.interestOps() & SelectionKey.OP_WRITE) != 0) {
                  key.attach(new Listener.DisconnectingListener(key));
                  disconnected = false;
                }
              }

              if (disconnected) {
                try {
                  key.attach(Listener.NOOP_CLIENT_LISTENER);
                  key.channel().close();
                }
                catch (IOException io) {
                  l.handleException(io, DefaultEventLoop.this);
                }
              }
            }
          }
        }
      }

      @Override
      public String toString()
      {
        return String.format("disconnect(%s)", l);
      }

    });
  }

  @Override
  public final void start(final String host, final int port, final ServerListener l)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        ServerSocketChannel channel = null;
        try {
          channel = ServerSocketChannel.open();
          channel.configureBlocking(false);
          channel.socket().bind(host == null ? new InetSocketAddress(port) : new InetSocketAddress(host, port), 128);
          register(channel, SelectionKey.OP_ACCEPT, l);
        }
        catch (IOException io) {
          l.handleException(io, DefaultEventLoop.this);
          if (channel != null && channel.isOpen()) {
            try {
              channel.close();
            }
            catch (IOException ie) {
              l.handleException(ie, DefaultEventLoop.this);
            }
          }
        }
      }

      @Override
      public String toString()
      {
        return String.format("start(%s, %d, %s)", host, port, l);
      }

    });
  }

  @Override
  public final void stop(final ServerListener l)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        for (SelectionKey key : selector.keys()) {
          if (key.attachment() == l) {
            if (key.isValid()) {
              l.unregistered(key);
              key.cancel();
            }
            key.attach(Listener.NOOP_LISTENER);
            try {
              key.channel().close();
            }
            catch (IOException io) {
              l.handleException(io, DefaultEventLoop.this);
            }
          }
        }
      }

      @Override
      public String toString()
      {
        return String.format("stop(%s)", l);
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
