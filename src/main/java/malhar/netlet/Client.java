/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package malhar.netlet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import malhar.netlet.Listener.ClientListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chetan Narsude <chetan@malhar-inc.com>
 */
public abstract class Client implements ClientListener
{
  final ByteBuffer writeBuffer;
  final CircularBuffer<Fragment> sendBuffer;
  final CircularBuffer<Fragment> freeBuffer;

  public Client(int writeBufferSize, int sendBufferSize)
  {
    this(ByteBuffer.allocateDirect(writeBufferSize), sendBufferSize);
  }

  public Client()
  {
    this(8 * 1024, 1024);
  }

  public Client(ByteBuffer writeBuffer, int sendBufferSize)
  {
    this.writeBuffer = writeBuffer;
    sendBuffer = new CircularBuffer<Fragment>(sendBufferSize, 10);
    freeBuffer = new CircularBuffer<Fragment>(sendBufferSize, 10);
  }

  @Override
  public void connected(SelectionKey key)
  {
    key.interestOps(SelectionKey.OP_READ);
  }

  @Override
  public final void read(SelectionKey key) throws IOException
  {
    SocketChannel channel = (SocketChannel)key.channel();
    int read;
    if ((read = channel.read(buffer())) > 0) {
      this.read(read);
    }
    else if (read == -1) {
      disconnected(key);
      channel.close();
    }
  }

  @Override
  public final void write(SelectionKey key) throws IOException
  {
    logger.debug("call to write");
    SocketChannel channel = (SocketChannel)key.channel();
    int size;
    if (!writeBuffer.hasRemaining() && (size = sendBuffer.size()) > 0) {
      int remaining = writeBuffer.capacity();

      do {
        Fragment f = sendBuffer.peekUnsafe();
        if (remaining < f.len) {
          writeBuffer.put(f.array, f.offset, remaining);
          f.offset += remaining;
          f.len -= remaining;
          break;
        }
        else {
          writeBuffer.put(f.array, f.offset, f.len);
          freeBuffer.add(sendBuffer.pollUnsafe());
          remaining -= f.len;
        }
      }
      while (--size > 0);

      writeBuffer.flip();
    }

    while (writeBuffer.hasRemaining()) {
      logger.debug("writing {} bytes", writeBuffer.remaining());
      channel.write(writeBuffer);
      if (writeBuffer.hasRemaining()) {
        key.interestOps(SelectionKey.OP_WRITE);
        return;
      }
      else {
        writeBuffer.clear();
        int remaining = writeBuffer.capacity();

        while (!sendBuffer.isEmpty()) {
          Fragment f = sendBuffer.peekUnsafe();
          if (remaining < f.len) {
            writeBuffer.put(f.array, f.offset, remaining);
            f.offset += remaining;
            f.len -= remaining;
            break;
          }
          else {
            writeBuffer.put(f.array, f.offset, f.len);
            freeBuffer.add(sendBuffer.pollUnsafe());
            remaining -= f.len;
          }
        }

        writeBuffer.flip();
      }
    }
  }

  public void send(byte[] array, int offset, int len) throws InterruptedException
  {
    Fragment f = freeBuffer.isEmpty() ? new Fragment() : freeBuffer.pollUnsafe();
    f.array = array;
    f.offset = offset;
    f.len = len;
    sendBuffer.put(f);
  }

  @Override
  public void handleException(Exception cce, EventLoop el)
  {
    logger.debug("", cce);
  }

  public abstract ByteBuffer buffer();

  public abstract void read(int len);

  @Override
  public void disconnected(SelectionKey key)
  {
  }

  private static class Fragment
  {
    byte[] array;
    int offset;
    int len;
  }

  private static final Logger logger = LoggerFactory.getLogger(Client.class);
}
