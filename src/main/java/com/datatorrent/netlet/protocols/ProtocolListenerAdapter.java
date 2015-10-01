package com.datatorrent.netlet.protocols;

import java.nio.channels.SelectionKey;

import com.datatorrent.netlet.EventLoop;
import com.datatorrent.netlet.Listener;
import com.datatorrent.netlet.ProtocolDriver;
import com.datatorrent.netlet.ProtocolHandler;

/**
 * Created by pramod on 10/1/15.
 */
public abstract class ProtocolListenerAdapter<L extends Listener> implements ProtocolHandler
{
  protected L listener;
  protected ProtocolDriver protocolDriver;

  public ProtocolListenerAdapter(L listener) {
    this.listener = listener;
  }

  public void init(ProtocolDriver protocolDriver) {
    this.protocolDriver = protocolDriver;
  }

  @Override
  public void handleException(Exception exception, EventLoop eventloop)
  {
    listener.handleException(exception, eventloop);
  }

  @Override
  public void registered(SelectionKey key)
  {
    listener.registered(key);
  }

  @Override
  public void unregistered(SelectionKey key)
  {
    listener.unregistered(key);
  }

  public L getListener()
  {
    return listener;
  }
}
