/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.malhartech.netlet;

import com.malhartech.util.Fragment;
import com.malhartech.netlet.Listener.ClientListener;
import com.malhartech.util.CircularBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chetan Narsude <chetan@malhar-inc.com>
 */
public abstract class Client implements ClientListener
{
  protected final ByteBuffer writeBuffer;
  protected final CircularBuffer<Fragment> freeBuffer;
  protected CircularBuffer<Fragment> sendBuffer4Offers, sendBuffer4Polls;
  protected boolean write = true;
  protected SelectionKey key;

  public SelectionKey getKey()
  {
    return key;
  }

  public Client(int writeBufferSize, int sendBufferSize)
  {
    this(ByteBuffer.allocateDirect(writeBufferSize), sendBufferSize);
  }

  public Client(int sendBufferSize)
  {
    this(8 * 1 * 1024, sendBufferSize);
  }

  public Client()
  {
    this(8 * 1 * 1024, 1024);
  }

  public Client(ByteBuffer writeBuffer, int sendBufferSize)
  {
    this.writeBuffer = writeBuffer;
    if (sendBufferSize == 0) {
      sendBufferSize = 1024;
    }
    else if (sendBufferSize % 1024 > 0) {
      sendBufferSize += 1024 - (sendBufferSize % 1024);
    }
    sendBuffer4Polls = sendBuffer4Offers = new CircularBuffer<Fragment>(sendBufferSize, 10);
    freeBuffer = new CircularBuffer<Fragment>(sendBufferSize, 10);
  }

  @Override
  public void registered(SelectionKey key)
  {
    this.key = key;
    write = false;
    //logger.debug("listener = {} and interestOps = {}", key.attachment(), Integer.toBinaryString(key.interestOps()));
  }

  @Override
  public final void read() throws IOException
  {
    SocketChannel channel = (SocketChannel)key.channel();
    int read;
    if ((read = channel.read(buffer())) > 0) {
      //logger.debug("{} read {} bytes", this, read);
      this.read(read);
    }
    else if (read == -1) {
      try {
        channel.close();
      }
      finally {
        unregistered(key);
        key.attach(Listener.NOOP_CLIENT_LISTENER);
      }
    }
    else {
      logger.debug("{} read 0 bytes", this);
    }
  }

  public void suspendRead()
  {
    key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
  }

  public void resumeRead()
  {
    key.interestOps(key.interestOps() | SelectionKey.OP_READ);
  }

  @Override
  public final void write() throws IOException
  {
    /*
     * at first when we enter this function, our buffer is in fill mode.
     */
    int remaining, size;
    if ((size = sendBuffer4Polls.size()) > 0 && (remaining = writeBuffer.remaining()) > 0) {
      do {
        Fragment f = sendBuffer4Polls.peekUnsafe();
        if (remaining <= f.length) {
          writeBuffer.put(f.buffer, f.offset, remaining);
          f.offset += remaining;
          f.length -= remaining;
          break;
        }
        else {
          writeBuffer.put(f.buffer, f.offset, f.length);
          remaining -= f.length;
          freeBuffer.offer(sendBuffer4Polls.pollUnsafe());
        }
      }
      while (--size > 0);
    }

    /*
     * switch to the read mode!
     */
    writeBuffer.flip();

    SocketChannel channel = (SocketChannel)key.channel();
    while ((remaining = writeBuffer.remaining()) > 0) {
      remaining -= channel.write(writeBuffer);
      if (remaining > 0) {
        /*
         * switch back to the fill mode.
         */
        writeBuffer.compact();
        return;
      }
      else if (size > 0) {
        /*
         * switch back to the write mode.
         */
        writeBuffer.clear();

        remaining = writeBuffer.capacity();
        do {
          Fragment f = sendBuffer4Polls.peekUnsafe();
          if (remaining <= f.length) {
            writeBuffer.put(f.buffer, f.offset, remaining);
            f.offset += remaining;
            f.length -= remaining;
            break;
          }
          else {
            writeBuffer.put(f.buffer, f.offset, f.length);
            remaining -= f.length;
            freeBuffer.offer(sendBuffer4Polls.pollUnsafe());
          }
        }
        while (--size > 0);

        /*
         * switch to the read mode.
         */
        writeBuffer.flip();
      }
    }

    /*
     * switch back to fill mode.
     */
    writeBuffer.clear();
    synchronized (this) {
      if (sendBuffer4Polls.isEmpty()) {
        if (sendBuffer4Offers == sendBuffer4Polls) {
          key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
          write = false;
        }
        else {
          sendBuffer4Polls = sendBuffer4Offers;
        }
      }
    }
  }

  public boolean send(byte[] array)
  {
    return send(array, 0, array.length);
  }

  public boolean send(byte[] array, int offset, int len)
  {
    Fragment f;
    if (freeBuffer.isEmpty()) {
      f = new Fragment(array, offset, len);
    }
    else {
      f = freeBuffer.pollUnsafe();
      f.buffer = array;
      f.offset = offset;
      f.length = len;
    }

    if (sendBuffer4Offers.offer(f)) {
      synchronized (this) {
        if (!write) {
          key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
          write = true;
        }
      }

      return true;
    }

    if (sendBuffer4Offers.capacity() != 32 * 1024) {
      synchronized (this) {
        if (sendBuffer4Offers == sendBuffer4Polls) {
          logger.debug("allocating new sendBuffer4Offers of size {} for {}", sendBuffer4Offers.size(), this);
          sendBuffer4Offers = new CircularBuffer<Fragment>(sendBuffer4Offers.capacity() << 1);
          sendBuffer4Offers.add(f);
          if (!write) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            write = true;
          }

          return true;
        }
      }
    }

    return false;
  }

  @Override
  public void handleException(Exception cce, DefaultEventLoop el)
  {
    logger.debug("", cce);
  }

  public abstract ByteBuffer buffer();

  public abstract void read(int len);

  @Override
  public synchronized void unregistered(SelectionKey key)
  {
    final CircularBuffer<Fragment> SEND_BUFFER = sendBuffer4Offers;
    sendBuffer4Offers = new CircularBuffer<Fragment>(0)
    {
      @Override
      public boolean isEmpty()
      {
        return SEND_BUFFER.isEmpty();
      }

      @Override
      public boolean offer(Fragment e)
      {
        throw new RuntimeException("client does not own the socket any longer!");
      }

      @Override
      public int size()
      {
        return SEND_BUFFER.size();
      }

      @Override
      public Fragment pollUnsafe()
      {
        return SEND_BUFFER.pollUnsafe();
      }

      @Override
      public Fragment peekUnsafe()
      {
        return SEND_BUFFER.peekUnsafe();
      }

    };
  }

  private static final Logger logger = LoggerFactory.getLogger(Client.class);
}
