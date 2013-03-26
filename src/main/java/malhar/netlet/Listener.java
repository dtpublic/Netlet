/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package malhar.netlet;

import java.io.IOException;
import java.nio.channels.*;

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
    public void read() throws IOException
    {
    }

    @Override
    public void write() throws IOException
    {
    }

  };
}
