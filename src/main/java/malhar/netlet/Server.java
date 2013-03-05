/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package malhar.netlet;

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
  @Override
  public void accepted(SelectionKey key, SocketChannel sc)
  {
  }

  @Override
  public void handleException(Exception cce, EventLoop el)
  {
    logger.debug("", cce);
  }

  private static final Logger logger = LoggerFactory.getLogger(Server.class);
}
