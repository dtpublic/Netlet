package com.datatorrent.netlet.protocols.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.netlet.Listener.ClientListener;
import com.datatorrent.netlet.ProtocolHandler.ServerProtocolHandler;
import com.datatorrent.netlet.protocols.ProtocolListenerAdapter;

/**
 * Created by pramod on 10/1/15.
 */
public class TcpServerHandler extends ProtocolListenerAdapter<TcpServerListener> implements ServerProtocolHandler
{

  private static final Logger logger = LoggerFactory.getLogger(TcpServerHandler.class);

  public TcpServerHandler(TcpServerListener serverListener) {
    super(serverListener);
  }

  public void start(final String host, final int port) {
    ServerSocketChannel channel = null;
    try {
      channel = ServerSocketChannel.open();
      channel.configureBlocking(false);
      channel.socket().bind(host == null ? new InetSocketAddress(port) : new InetSocketAddress(host, port), 128);
      protocolDriver.register(channel, SelectionKey.OP_ACCEPT, this);
    }
    catch (IOException io) {
      handleException(io);
      if (channel != null && channel.isOpen()) {
        try {
          channel.close();
        }
        catch (IOException ie) {
          handleException(ie);
        }
      }
    }
  }

  @Override
  public void stopServer()
  {
    protocolDriver.stop(this);
  }

  @Override
  public void handleSelectedKey(SelectionKey sk) throws IOException
  {
    switch (sk.readyOps()) {
      case SelectionKey.OP_ACCEPT:
        ServerSocketChannel ssc = (ServerSocketChannel)sk.channel();
        SocketChannel sc = ssc.accept();
        sc.configureBlocking(false);
        final ClientListener l = listener.getClientConnection(sc, (ServerSocketChannel)sk.channel());
        TcpClientHandler clientHandler = new TcpClientHandler(l);
        clientHandler.init(protocolDriver);
        protocolDriver.register(sc, SelectionKey.OP_READ | SelectionKey.OP_WRITE, clientHandler);
        break;

      default:
        logger.warn("!!!!!! not sure what interest this is {} !!!!!!", Integer.toBinaryString(sk.readyOps()));
        break;
    }
  }
}
