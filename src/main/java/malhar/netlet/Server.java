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
  public void started(SelectionKey key)
  {
    ServerSocketChannel ssc = (ServerSocketChannel)key.channel();
    try {
      boundAddress = ssc.getLocalAddress();
    }
    catch (IOException io) {
      throw new RuntimeException(io);
    }
  }

  @Override
  public void stopped(SelectionKey key)
  {
  }

  @Override
  public void accepted(SocketChannel sc)
  {
  }

  @Override
  public void handleException(Exception cce, EventLoop el)
  {
    logger.debug("", cce);
  }

  public SocketAddress getServerAddress()
  {
    return boundAddress;
  }

  private static final Logger logger = LoggerFactory.getLogger(Server.class);
}
