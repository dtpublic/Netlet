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
  public void handleException(Exception cce, EventLoop el);

  public static interface ServerListener extends Listener
  {
    public void started(SelectionKey key);

    public void stopped(SelectionKey key);

    public void accepted(SocketChannel sc);

    public ClientListener getClientConnection(SocketChannel sc, ServerSocketChannel ssc);

  }

  public static interface ClientListener extends Listener
  {
    public void connected(SelectionKey key);

    public void disconnected(SelectionKey key);

    public void read() throws IOException;

    public void write() throws IOException;

  }

}
