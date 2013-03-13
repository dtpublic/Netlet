/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package malhar.netlet;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import malhar.netlet.Listener.ServerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chetan Narsude <chetan@malhar-inc.com>
 */
public abstract class Server implements ServerListener
{
  SocketAddress boundAddress;

  @Override
  public void registered(SelectionKey key)
  {
    try {
      boundAddress = ((ServerSocketChannel)key.channel()).getLocalAddress();
    }
    catch (IOException io) {
      throw new RuntimeException(io);
    }
  }

  @Override
  public void unregistered(SelectionKey key)
  {
  }

  @Override
  public void handleException(Exception cce, DefaultEventLoop el)
  {
    logger.debug("", cce);
  }

  public SocketAddress getServerAddress()
  {
    return boundAddress;
  }

  private static final Logger logger = LoggerFactory.getLogger(Server.class);
}
