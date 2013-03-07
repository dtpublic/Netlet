/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package malhar.netlet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.SelectionKey;
import malhar.netlet.ServerTest.ServerImpl;
import org.junit.Test;

/**
 *
 * @author Chetan Narsude <chetan@malhar-inc.com>
 */
public class ClientTest
{
  public ClientTest()
  {
  }

  public class ClientImpl extends Client
  {
    public static final int BUFFER_CAPACITY = 8 * 1024 + 1;
    ByteBuffer outboundBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_CAPACITY);
    private SelectionKey key;
    boolean read;

    @Override
    public ByteBuffer buffer()
    {
      return buffer;
    }

    @Override
    public void connected(SelectionKey key)
    {
      LongBuffer lb = outboundBuffer.asLongBuffer();
      for (int i = 0; i < lb.capacity(); i++) {
        lb.put(i);
      }

      boolean odd = false;
      while (outboundBuffer.hasRemaining()) {
        outboundBuffer.put((byte)(odd ? 0x55 : 0xaa));
      }

      try {
        send(outboundBuffer.array(), 0, outboundBuffer.position());
      }
      catch (InterruptedException ie) {
        throw new RuntimeException(ie);
      }

      key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
      this.key = key;
    }

    @Override
    public void read(int len)
    {
      if (buffer.position() == buffer.capacity()) {
        buffer.flip();
        read = true;

        int i = 0;
        long[] arrays = buffer.asLongBuffer().array();
        assert (arrays.length == BUFFER_CAPACITY / 8);
        while (i < arrays.length) {
          assert (i == arrays[i]);
          i++;
        }
      }
    }

  }

  @Test
  public void verifySendReceive() throws IOException, InterruptedException
  {
    ServerImpl si = new ServerImpl();
    ClientImpl ci = new ClientImpl();

    EventLoop el = new EventLoop("test");
    new Thread(el).start();

    el.start("localhost", 5033, si);
    el.connect("localhost", 5033, ci);

    Thread.sleep(10000);

    el.disconnect(ci);
    el.stop(si);

    assert(ci.read);
  }

}