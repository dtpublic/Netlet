/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package malhar.netlet;

import java.io.IOException;
import static java.lang.Thread.sleep;
import java.net.InetSocketAddress;
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
public class EventLoop implements Runnable
{
  public final String id;
  private Selector selector;
  private Thread eventThread;
  private final Collection<SelectionKey> disconnected = new ArrayList<SelectionKey>();
  private final Collection<Runnable> tasks = new ArrayList<Runnable>();

  public EventLoop(String id) throws IOException
  {
    this.id = id;
    selector = Selector.open();
  }

  @Override
  @SuppressWarnings("SleepWhileInLoop")
  public void run()
  {
    eventThread = Thread.currentThread();

    int size;
    SocketChannel sc;
    ClientListener l;

    try {
      while (true) {
        if ((size = selector.selectNow()) == 0) {
//          for (SelectionKey sk : selector.keys()) {
//            logger.debug("{} for {}", sk.channel(), Integer.toBinaryString(sk.interestOps()));
//          }
        }
        else {
          Set<SelectionKey> selectedKeys = selector.selectedKeys();
          for (SelectionKey sk : selectedKeys) {
//            logger.debug("sk = {} readyOps = {}", sk, Integer.toBinaryString(sk.readyOps()));
            if (!sk.isValid()) {
              continue;
            }

            switch (sk.readyOps()) {
              case SelectionKey.OP_ACCEPT:
                ServerSocketChannel ssc = (ServerSocketChannel)sk.channel();
                sc = ssc.accept();
                sc.configureBlocking(false);
                ServerListener sl = (ServerListener)sk.attachment();
                sl.accepted(sk, sc);
                /*
                 * now lets pretend that the client connection was also connected due to acceptance.
                 */
                l = sl.getClientConnection(sc, (ServerSocketChannel)sk.channel());
                register(sc, SelectionKey.OP_READ, l);
                l.connected(sk);
                break;

              case SelectionKey.OP_CONNECT:
                ((SocketChannel)sk.channel()).finishConnect();
                ((ClientListener)sk.attachment()).connected(sk);
                break;

              case SelectionKey.OP_READ:
                ((ClientListener)sk.attachment()).read(sk);
                break;

              case SelectionKey.OP_WRITE:
                ((ClientListener)sk.attachment()).write(sk);
                break;

              case SelectionKey.OP_READ | SelectionKey.OP_WRITE:
                (l = (ClientListener)sk.attachment()).read(sk);
                l.write(sk);
                break;

              case SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT:
                (l = (ClientListener)sk.attachment()).connected(sk);
                l.read(sk);
                l.write(sk);
                break;

              case SelectionKey.OP_READ | SelectionKey.OP_CONNECT:
                (l = (ClientListener)sk.attachment()).connected(sk);
                l.read(sk);
                break;

              case SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT:
                (l = (ClientListener)sk.attachment()).connected(sk);
                l.write(sk);
                break;
            }
          }
          selectedKeys.clear();
        }

        if (!tasks.isEmpty()) {
          synchronized (tasks) {
            Iterator<Runnable> i = tasks.iterator();
            while (i.hasNext()) {
              Runnable r = i.next();
              i.remove();
              r.run();
            }
          }
        }

        if (!disconnected.isEmpty()) {
          Iterator<SelectionKey> keys = disconnected.iterator();
          while (keys.hasNext()) {
            SelectionKey key = keys.next();
            if (!key.isValid()) {
              keys.remove();
              try {
                key.channel().close();
              }
              catch (IOException io) {
                ((Listener)key.attachment()).handleException(io, EventLoop.this);
              }
            }
          }
        }
      }
    }
    catch (IOException io) {
      throw new RuntimeException("Selector Failed!", io.getCause());
    }
  }

  public void submit(Runnable r)
  {
    if (eventThread == Thread.currentThread()) {
      r.run();
    }
    else {
      synchronized (tasks) {
        tasks.add(r);
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
          c.register(selector, ops, l);
        }
        catch (ClosedChannelException cce) {
          l.handleException(cce, EventLoop.this);
        }
      }

    });
  }

  public void deregister(final AbstractSelectableChannel c, final int ops)
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

  public void deregister(final SelectableChannel c)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        for (SelectionKey sk : selector.keys()) {
          if (sk.channel() == c) {
            sk.cancel();
          }
        }
      }

    });
  }

  public void register(ServerSocketChannel channel, Listener l)
  {
    register(channel, SelectionKey.OP_ACCEPT, l);
  }

  public void register(SocketChannel channel, int ops, Listener l)
  {
    register((AbstractSelectableChannel)channel, ops, l);
  }

  public final void connect(final String host, final int port, final Listener l)
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
          channel.connect(host == null ? new InetSocketAddress(port) : new InetSocketAddress(host, port));
          register(channel, SelectionKey.OP_CONNECT, l);
        }
        catch (IOException ie) {
          l.handleException(ie, EventLoop.this);
          if (channel != null && channel.isOpen()) {
            try {
              channel.close();
            }
            catch (IOException io) {
              l.handleException(io, EventLoop.this);
            }
          }
        }
      }

    });
  }

  public final void disconnect(final ClientListener l)
  {
    submit(new Runnable()
    {
      @Override
      public void run()
      {
        for (SelectionKey key : selector.keys()) {
          if (key.attachment() == l) {
            l.disconnected(key);
            if (key.isValid()) {
              disconnected.add(key);
            }
            else {
              try {
                key.channel().close();
              }
              catch (IOException io) {
                l.handleException(io, EventLoop.this);
              }
            }
          }
        }
      }

    });
  }

  public final void start(final String host, final int port, final Listener l)
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
          l.handleException(io, EventLoop.this);
          if (channel != null && channel.isOpen()) {
            try {
              channel.close();
            }
            catch (IOException ie) {
              l.handleException(ie, EventLoop.this);
            }
          }
        }
      }

    });
  }

  private static final Logger logger = LoggerFactory.getLogger(EventLoop.class);
}
