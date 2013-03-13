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

}
