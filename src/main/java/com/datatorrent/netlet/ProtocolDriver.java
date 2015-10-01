package com.datatorrent.netlet;

import java.nio.channels.SelectableChannel;

/**
 * Created by pramod on 10/1/15.
 */
public interface ProtocolDriver extends EventLoop
{
  void register(final SelectableChannel c, final int ops, final ProtocolHandler handler);
}
