/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package malhar.netlet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOptions;
import java.nio.channels.*;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import malhar.netlet.Listener.ClientListener;
import malhar.netlet.Listener.ServerListener;
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
  @SuppressWarnings("SleepWhileInLoop")
  public void run()
  {
    // clean this up later.
    alive = true;
    eventThread = Thread.currentThread();

    SocketChannel sc;
    ClientListener l;
    boolean wait = true;

    do {
      SelectionKey sk;
      try {
        do {
          if (selector.selectNow() > 0) {
            wait = false;
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            while (iterator.hasNext()) {
              sk = iterator.next();
              //logger.debug("sk = {} readyOps = {}", sk.attachment(), Integer.toBinaryString(sk.readyOps()));
              if (!sk.isValid()) {
                continue;
              }

              switch (sk.readyOps()) {
                case SelectionKey.OP_ACCEPT:
                  ServerSocketChannel ssc = (ServerSocketChannel)sk.channel();
                  sc = ssc.accept();
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
                  (l = (ClientListener)sk.attachment()).read();
                  l.write();
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

          if (!disconnected.isEmpty()) {
            wait = false;
            logger.debug("handling {} disconenct requests", disconnected.size());
            Iterator<SelectionKey> keys = disconnected.iterator();
            while (keys.hasNext()) {
              SelectionKey key = keys.next();
              if (!key.isValid()) {
                keys.remove();
                try {
                  key.channel().close();
                }
                catch (IOException io) {
                  ((Listener)key.attachment()).handleException(io, DefaultEventLoop.this);
                }
              }
            }
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
        logger.info("ignoring failed selector! ", io);
      }
    }
    while (alive);
  }

  @Override
  public void submit(Runnable r)
  {
    if (tasks.isEmpty() && eventThread == Thread.currentThread()) {
      logger.debug("executing task immediately {}", r);
      r.run();
    }
    else {
      logger.debug("submitted task {}", r);
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

  public void unregister(final AbstractSelectableChannel c, final int ops)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        for (SelectionKey sk : selector.keys()) {
          if (sk.channel() == c) {
            int newOps = sk.interestOps() ^ ops;
            if (newOps == 0) {
              ((Listener)sk.attachment()).unregistered(sk);
              sk.cancel();
            }
            else {
              sk.interestOps(newOps);
            }
          }
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
        for (SelectionKey sk : selector.keys()) {
          if (sk.channel() == c) {
            ((Listener)sk.attachment()).unregistered(sk);
            sk.cancel();
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
        for (SelectionKey key : selector.keys()) {
          if (key.attachment() == l) {
            if (key.isValid()) {
              l.unregistered(key);
              disconnected.add(key);
            }
            else {
              try {
                key.channel().close();
              }
              catch (IOException io) {
                l.handleException(io, DefaultEventLoop.this);
              }
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
          channel.bind(host == null ? new InetSocketAddress(port) : new InetSocketAddress(host, port));
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
        for (SelectionKey key : selector.keys()) {
          if (key.attachment() == l) {
            if (key.isValid()) {
              l.unregistered(key);
              key.cancel();
            }
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

  private static final Logger logger = LoggerFactory.getLogger(DefaultEventLoop.class);
}
