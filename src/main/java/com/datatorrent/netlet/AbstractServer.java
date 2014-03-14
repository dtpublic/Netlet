/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.datatorrent.netlet;

import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.netlet.Listener.ServerListener;

/**
 * <p>Abstract AbstractServer class.</p>
 *
 * @author Chetan Narsude <chetan@datatorrent.com>
 * @since 0.3.2
 */
public abstract class AbstractServer implements ServerListener
{
  SocketAddress boundAddress;

  @Override
  public void registered(SelectionKey key)
  {
      boundAddress = ((ServerSocketChannel)key.channel()).socket().getLocalSocketAddress();
  }

  @Override
  public void unregistered(SelectionKey key)
  {
  }

  @Override
  public void handleException(Exception cce, EventLoop el)
  {
    logger.debug("", cce);
  }

  public SocketAddress getServerAddress()
  {
    return boundAddress;
  }

  private static final Logger logger = LoggerFactory.getLogger(AbstractServer.class);
}
