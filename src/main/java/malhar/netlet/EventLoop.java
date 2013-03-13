/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package malhar.netlet;

import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import malhar.netlet.Listener.ClientListener;
import malhar.netlet.Listener.ServerListener;

/**
 *
 * @author Chetan Narsude <chetan@malhar-inc.com>
 */
public interface EventLoop
{
  void connect(final InetSocketAddress address, final Listener l);

  void disconnect(final ClientListener l);

  void register(ServerSocketChannel channel, Listener l);

  void register(SocketChannel channel, int ops, Listener l);

  void start(final String host, final int port, final ServerListener l);

  void stop(final ServerListener l);

  void submit(Runnable r);

  void unregister(final SelectableChannel c);

}
