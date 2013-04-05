/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.malhartech.netlet;

import com.malhartech.util.CircularBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import com.malhartech.netlet.Listener.ClientListener;
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
  protected CircularBuffer<Fragment> sendBuffer;
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
    sendBuffer = new CircularBuffer<Fragment>(sendBufferSize, 10);
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
    if ((size = sendBuffer.size()) > 0 && (remaining = writeBuffer.remaining()) > 0) {
      do {
        Fragment f = sendBuffer.peekUnsafe();
        if (remaining <= f.length) {
          writeBuffer.put(f.buffer, f.offset, remaining);
          f.offset += remaining;
          f.length -= remaining;
          break;
        }
        else {
          writeBuffer.put(f.buffer, f.offset, f.length);
          remaining -= f.length;
          freeBuffer.offer(sendBuffer.pollUnsafe());
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
      else if ((size = sendBuffer.size()) > 0) {
        /*
         * switch back to the write mode.
         */
        writeBuffer.clear();

        remaining = writeBuffer.capacity();
        do {
          Fragment f = sendBuffer.peekUnsafe();
          if (remaining <= f.length) {
            writeBuffer.put(f.buffer, f.offset, remaining);
            f.offset += remaining;
            f.length -= remaining;
            break;
          }
          else {
            writeBuffer.put(f.buffer, f.offset, f.length);
            remaining -= f.length;
            freeBuffer.offer(sendBuffer.pollUnsafe());
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
      if (sendBuffer.isEmpty()) {
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        write = false;
      }
    }
  }

  public void send(byte[] array) throws InterruptedException
  {
    send(array, 0, array.length);
  }

  public void send(byte[] array, int offset, int len) throws InterruptedException
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
    sendBuffer.put(f);

    synchronized (this) {
      if (!write) {
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        write = true;
      }
    }
  }

  @Override
  public void handleException(Exception cce, DefaultEventLoop el)
  {
    logger.debug("", cce);
  }

  public abstract ByteBuffer buffer();

  public abstract void read(int len);

  @Override
  public void unregistered(SelectionKey key)
  {
    final CircularBuffer<Fragment> SEND_BUFFER = sendBuffer;
    sendBuffer = new CircularBuffer<Fragment>(sendBuffer.capacity())
    {
      @Override
      public boolean isEmpty()
      {
        return false; //To change body of generated methods, choose Tools | Templates.
      }

      @Override
      public void put(Fragment e) throws InterruptedException
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

  public static class Fragment
  {
    public byte[] buffer;
    public int offset;
    public int length;

    public Fragment(byte[] array, int offset, int length)
    {
      buffer = array;
      this.offset = offset;
      this.length = length;
    }

    @Override
    public int hashCode()
    {
      int hash = 5;
      hash = 59 * hash + Arrays.hashCode(this.buffer);
      hash = 59 * hash + this.offset;
      hash = 59 * hash + this.length;
      return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final Fragment other = (Fragment)obj;
      if (!Arrays.equals(this.buffer, other.buffer)) {
        return false;
      }
      if (this.offset != other.offset) {
        return false;
      }
      if (this.length != other.length) {
        return false;
      }
      return true;
    }

  }

  private static final Logger logger = LoggerFactory.getLogger(Client.class);
}
