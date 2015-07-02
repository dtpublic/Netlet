package com.datatorrent.netlet.datagram;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectionKey;

/**
 *
 */
public class DatagramSelectionKey extends AbstractSelectionKey
{

  private SelectableChannel channel;

  public DatagramSelectionKey(SelectableChannel channel) {
    this.channel = channel;
  }

  @Override
  public SelectableChannel channel()
  {
    return channel;
  }

  @Override
  public Selector selector()
  {
    return null;
  }

  @Override
  public int interestOps()
  {
    return 0;
  }

  @Override
  public SelectionKey interestOps(int ops)
  {
    return null;
  }

  @Override
  public int readyOps()
  {
    return 0;
  }
}
