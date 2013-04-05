/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.malhartech.netlet;

import com.malhartech.util.CircularBuffer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import com.malhartech.netlet.Listener.ClientListener;
import com.malhartech.netlet.Listener.ServerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chetan Narsude <chetan@malhar-inc.com>
 */
public class DefaultEventLoop implements Runnable, EventLoop
{
  public final String id;
  private boolean alive;
  private Selector selector;
  private Thread eventThread;
  private final Collection<SelectionKey> disconnected = new ArrayList<SelectionKey>();
  private final CircularBuffer<Runnable> tasks = new CircularBuffer<Runnable>(1024, 5);

  public DefaultEventLoop(String id) throws IOException
  {
    this.id = id;
    selector = Selector.open();
  }

  public void start()
  {
    new Thread(this, id).start();
  }

  public void stop()
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        alive = false;
      }

    });
  }

  @Override
  @SuppressWarnings({"SleepWhileInLoop", "null", "ConstantConditions"})
  public void run()
  {
    alive = true;
    eventThread = Thread.currentThread();
    boolean wait = true;

    SelectionKey sk = null;
    Set<SelectionKey> selectedKeys = null;
    Iterator<SelectionKey> iterator = null;

    do {
      try {
        do {
          if (wait) {
            if (selector.selectNow() > 0) {
              selectedKeys = selector.selectedKeys();
              iterator = selectedKeys.iterator();
            }
          }

          if (iterator != null) {
            wait = false;

            while (iterator.hasNext()) {
              if (!(sk = iterator.next()).isValid()) {
                continue;
              }

              ClientListener l;
              switch (sk.readyOps()) {
                case SelectionKey.OP_ACCEPT:
                  ServerSocketChannel ssc = (ServerSocketChannel)sk.channel();
                  SocketChannel sc = ssc.accept();
                  sc.configureBlocking(false);
                  ServerListener sl = (ServerListener)sk.attachment();
                  l = sl.getClientConnection(sc, (ServerSocketChannel)sk.channel());
                  register(sc, SelectionKey.OP_READ | SelectionKey.OP_WRITE, l);
                  break;

                case SelectionKey.OP_CONNECT:
                  ((SocketChannel)sk.channel()).finishConnect();
                  sk.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                  break;

                case SelectionKey.OP_READ:
                case SelectionKey.OP_READ | SelectionKey.OP_CONNECT:
                  ((ClientListener)sk.attachment()).read();
                  break;

                case SelectionKey.OP_WRITE:
                case SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT:
                  ((ClientListener)sk.attachment()).write();
                  break;

                case SelectionKey.OP_READ | SelectionKey.OP_WRITE:
                case SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT:
                  (l = (ClientListener)sk.attachment()).write();
                  l.read();
                  break;
              }
            }

            selectedKeys.clear();
          }

          int size = tasks.size();
          if (size > 0) {
            wait = false;

            do {
              tasks.pollUnsafe().run();
            }
            while (--size > 0);
          }

          if (wait) {
            Thread.sleep(5);
          }
          else {
            wait = true;
          }
        }
        while (alive);
      }
      catch (InterruptedException ie) {
        throw new RuntimeException("Interrupted!", ie);
      }
      catch (IOException io) {
        if (sk == null) {
          logger.warn("Unexpected exception not related to SelectionKey", io);
        }
        else {
          Listener l = (Listener)sk.attachment();
          if (l == null) {
            logger.warn("Exception on unregistered SelectionKey", io);
          }
          else {
            l.handleException(io, this);
          }
        }
        if (selectedKeys.isEmpty()) {
          wait = true;
        }
      }
    }
    while (alive);
  }

  @Override
  public void submit(Runnable r)
  {
    if (tasks.isEmpty() && eventThread == Thread.currentThread()) {
      r.run();
    }
    else {
      tasks.add(r);
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

    });
  }

  @Override
  public void unregister(final SelectableChannel c)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        for (SelectionKey key: selector.keys()) {
          if (key.channel() == c) {
            ((Listener)key.attachment()).unregistered(key);
            key.interestOps(0);
            key.attach(Listener.NOOP_LISTENER);
          }
        }
      }

    });
  }

  @Override
  public void register(ServerSocketChannel channel, Listener l)
  {
    register(channel, SelectionKey.OP_ACCEPT, l);
  }

  @Override
  public void register(SocketChannel channel, int ops, Listener l)
  {
    register((AbstractSelectableChannel)channel, ops, l);
  }

  @Override
  public final void connect(final InetSocketAddress address, final Listener l)
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
          channel.connect(address);
          register(channel, SelectionKey.OP_CONNECT, l);
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
        for (SelectionKey key: selector.keys()) {
          if (key.attachment() == l) {
            if (key.isValid()) {
              l.unregistered(key);
              if ((key.interestOps() & SelectionKey.OP_WRITE) != 0) {
                key.attach(new Listener.DisconnectingListener(key));
                return;
              }
            }
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
        for (SelectionKey key: selector.keys()) {
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

    });
  }

  public boolean isActive()
  {
    return eventThread != null && eventThread.isAlive();
  }

  private static final Logger logger = LoggerFactory.getLogger(DefaultEventLoop.class);
}
