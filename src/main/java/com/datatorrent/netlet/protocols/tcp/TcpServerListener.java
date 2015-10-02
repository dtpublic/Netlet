package com.datatorrent.netlet.protocols.tcp;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import com.datatorrent.netlet.Listener;

/**
 * Interface that listener who is interested in server events must implement.
 * In addition to common networking events, this listener is also supposed to
 * provide a mechanism to register listeners which process the events on each
 * incoming connection.
 */
public interface TcpServerListener extends Listener
{
  /**
   * Get a listener which will process the events coming on the newly connected client
   * connection.
   *
   * @param client Socket channel associated with the newly accepted client connection.
   * @param server Socket channel associated with the server connection which accepted the client.
   * @return
   */
  public ClientListener getClientConnection(SocketChannel client, ServerSocketChannel server);

}
