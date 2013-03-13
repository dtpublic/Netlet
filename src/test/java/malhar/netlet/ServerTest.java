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

    @Override
    public String toString()
    {
      return "ServerImpl{" + '}';
    }

  }

  static class EchoClient extends Client
  {
    ByteBuffer buffer = ByteBuffer.allocate(1024 * 4);

    @Override
    public ByteBuffer buffer()
    {
      buffer.clear();
      return buffer;
    }

    @Override
    public String toString()
    {
      return "EchoClient{" + "buffer=" + buffer + '}';
    }

    @Override
    public void read(int len)
    {
      byte[] array = new byte[len];
      System.arraycopy(buffer.array(), 0, array, 0, len);
      try {
        send(array, 0, len);
      }
      catch (InterruptedException ie) {
        throw new RuntimeException(ie);
      }
    }

  }

  private static final Logger logger = LoggerFactory.getLogger(ServerTest.class);
}