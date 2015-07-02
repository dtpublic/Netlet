package com.datatorrent.netlet.datagram;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;

/**
 *
 */
public class DatagramConnectionChannel extends SelectableChannel implements ReadableByteChannel, WritableByteChannel
{
  private SocketAddress socketAddress;
  private ConnectionAdapter adapter;

  private ByteBuffer buffer;

  public DatagramConnectionChannel(SocketAddress socketAddress, ConnectionAdapter adapter)
  {
    this.socketAddress = socketAddress;
    this.adapter = adapter;
    this.buffer = adapter.getReadBuffer(this);
  }

  public SocketAddress getSocketAddress()
  {
    return socketAddress;
  }

  @Override
  public int read(ByteBuffer dst) throws IOException
  {
    // Since datagram packets are always offered in full the buffer will only be copied if space is available
    int length = buffer.remaining();
    int space = dst.remaining();
    if (length <= space) {
      dst.put(buffer);
      return length;
    }
    return 0;
  }

  @Override
  public SelectorProvider provider()
  {
    return null;
  }

  @Override
  public int validOps()
  {
    return 0;
  }

  @Override
  public boolean isRegistered()
  {
    return false;
  }

  @Override
  public SelectionKey keyFor(Selector sel)
  {
    return null;
  }

  @Override
  public SelectionKey register(Selector sel, int ops, Object att) throws ClosedChannelException
  {
    return null;
  }

  @Override
  public SelectableChannel configureBlocking(boolean block) throws IOException
  {
    return null;
  }

  @Override
  public boolean isBlocking()
  {
    return false;
  }

  @Override
  public Object blockingLock()
  {
    return null;
  }

  @Override
  protected void implCloseChannel() throws IOException
  {

  }

  @Override
  public int write(ByteBuffer src) throws IOException
  {
    return adapter.write(src, this);
  }

  public interface ConnectionAdapter
  {
    ByteBuffer getReadBuffer(DatagramConnectionChannel channel);
    int write(ByteBuffer b, DatagramConnectionChannel channel) throws IOException;
  }
}
