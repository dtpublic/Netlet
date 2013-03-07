/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package malhar.netlet;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chetan Narsude <chetan@malhar-inc.com>
 */
public class ServerTest
{
  public ServerTest()
  {
  }

  static class ServerImpl extends Server
  {
    @Override
    public ClientListener getClientConnection(SocketChannel sc, ServerSocketChannel ssc)
    {
      return new EchoClient();
    }

  }

  static class EchoClient extends Client
  {
    ByteBuffer buffer = ByteBuffer.allocate(1024 * 4);
    SelectionKey key;

    @Override
    public ByteBuffer buffer()
    {
      buffer.clear();
      return buffer;
    }

    @Override
    public void connected(SelectionKey key)
    {
      super.connected(key); //To change body of generated methods, choose Tools | Templates.
      this.key = key;
    }

    @Override
    public void read(int len)
    {
      logger.debug("echo client read {} bytes", len);
      byte[] array = new byte[len];
      System.arraycopy(buffer.array(), 0, array, 0, len);
      try {
        send(array, 0, len);
        int interest = key.interestOps();
        if ((interest & SelectionKey.OP_WRITE) == 0) {
          key.interestOps(interest & SelectionKey.OP_WRITE);
        }
      }
      catch (InterruptedException ie) {
        throw new RuntimeException(ie);
      }
    }

  }

  private static final Logger logger = LoggerFactory.getLogger(ServerTest.class);
}