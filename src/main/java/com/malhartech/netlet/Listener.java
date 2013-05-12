/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.malhartech.netlet;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chetan Narsude <chetan@malhar-inc.com>
 */
public interface Listener
{
  public void handleException(Exception cce, DefaultEventLoop el);

  public void registered(SelectionKey key);

  public void unregistered(SelectionKey key);

  public static interface ServerListener extends Listener
  {
    public ClientListener getClientConnection(SocketChannel sc, ServerSocketChannel ssc);

  }

  public static interface ClientListener extends Listener
  {
    public void read() throws IOException;

    public void write() throws IOException;

    public void connected();

    public void disconnected();

  }

  public static final Listener NOOP_LISTENER = new Listener()
  {
    @Override
    public void handleException(Exception cce, DefaultEventLoop el)
    {
    }

    @Override
    public void registered(SelectionKey key)
    {
    }

    @Override
    public void unregistered(SelectionKey key)
    {
    }

  };
  public static final Listener NOOP_CLIENT_LISTENER = new ClientListener()
  {
    @Override
    public void read() throws IOException
    {
    }

    @Override
    public void write() throws IOException
    {
    }

    @Override
    public void handleException(Exception cce, DefaultEventLoop el)
    {
    }

    @Override
    public void registered(SelectionKey key)
    {
    }

    @Override
    public void unregistered(SelectionKey key)
    {
    }

    @Override
    public void connected()
    {
    }

    @Override
    public void disconnected()
    {
    }

  };

  static class DisconnectingListener implements ClientListener
  {
    private final ClientListener previous;
    private final SelectionKey key;

    public DisconnectingListener(SelectionKey key)
    {
      this.key = key;
      previous = (ClientListener)key.attachment();
    }

    @Override
    public void read() throws IOException
    {
      disconnect();
    }

    /**
     * Disconnect if there is no write interest on this socket.
     */
    private void disconnect()
    {
      if ((key.interestOps() & SelectionKey.OP_WRITE) == 0) {
        disconnected();
        if (key.isValid()) {
          key.cancel();
        }

        key.attach(null);

        try {
          key.channel().close();
        }
        catch (IOException ie) {
          logger.warn("exception while closing socket", ie);
        }
      }
    }

    @Override
    public void write() throws IOException
    {
      previous.write();
      disconnect();
    }

    @Override
    public void handleException(Exception cce, DefaultEventLoop el)
    {
      previous.handleException(cce, el);
    }

    @Override
    public void registered(SelectionKey key)
    {
    }

    @Override
    public void unregistered(SelectionKey key)
    {
    }

    @Override
    public void connected()
    {
    }

    @Override
    public void disconnected()
    {
    }

    private static final Logger logger = LoggerFactory.getLogger(DisconnectingListener.class);
  }

}
