package com.datatorrent.netlet.protocols.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.netlet.Listener.ClientListener;
import com.datatorrent.netlet.ProtocolHandler;
import com.datatorrent.netlet.ProtocolHandler.ClientProtocolHandler;
import com.datatorrent.netlet.protocols.ProtocolListenerAdapter;

/**
 * Created by pramod on 10/1/15.
 */
public class TcpClientHandler extends ProtocolListenerAdapter<ClientListener> implements ClientProtocolHandler
{
  private static final Logger logger = LoggerFactory.getLogger(TcpClientHandler.class);

  public TcpClientHandler(ClientListener listener) {
    super(listener);
  }

  @Override
  public void handleSelectedKey(SelectionKey sk) throws IOException
  {
    switch (sk.readyOps()) {
      case SelectionKey.OP_CONNECT:
        if (((SocketChannel)sk.channel()).finishConnect()) {
          listener.connected();
        }
        break;

      case SelectionKey.OP_READ:
        listener.read();
        break;

      case SelectionKey.OP_WRITE:
        listener.write();
        break;

      case SelectionKey.OP_READ | SelectionKey.OP_WRITE:
        listener.read();
        listener.write();
        break;

      case SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT:
      case SelectionKey.OP_READ | SelectionKey.OP_CONNECT:
      case SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT:
        if (((SocketChannel)sk.channel()).finishConnect()) {
          listener.connected();
          if (sk.isReadable()) {
            listener.read();
          }
          if (sk.isWritable()) {
            listener.write();
          }
        }
        break;

      default:
        logger.warn("!!!!!! not sure what interest this is {} !!!!!!", Integer.toBinaryString(sk.readyOps()));
        break;
    }

  }

  public void connect(InetSocketAddress address) {
    SocketChannel channel = null;
    try {
      channel = SocketChannel.open();
      channel.configureBlocking(false);
      if (channel.connect(address)) {
        listener.connected();
        protocolDriver.register(channel, SelectionKey.OP_READ, this);
      }
      else {
            /*
             * According to the spec SelectionKey.OP_READ is not necessary here, but without it
             * occasionally channel key will not be selected after connection is established and finishConnect()
             * will return true.
             */
        final ClientListener listener = this.listener;
        ClientListener clientListener = new ClientListener()
        {
          private SelectionKey key;

          @Override
          public void read() throws IOException
          {
            logger.debug("missing OP_CONNECT");
            connected();
            listener.read();
          }

          @Override
          public void write() throws IOException
          {
            logger.debug("missing OP_CONNECT");
            connected();
            listener.write();
          }

          @Override
          public void connected()
          {
            logger.debug("{}", this);
            //key.attach(this);
            listener.connected();
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
          }

          @Override
          public void disconnected()
          {
                /*
                 * Expectation is that connected() or read() will be called and this ClientListener will be replaced
                 * by the original ClientListener in the key attachment before disconnect is initiated. In any case
                 * as original Client Listener was never attached to the key, this method will never be called. Please
                 * see DefaultEventLoop.disconnect().
                 */
            logger.debug("missing OP_CONNECT {}", this);
            throw new NotYetConnectedException();
          }

          @Override
          public void handleException(Exception exception, ProtocolHandler handler)
          {
            key.attach(this);
            listener.handleException(exception, handler);
          }

          @Override
          public void registered(SelectionKey key)
          {
            listener.registered(this.key = key);
          }

          @Override
          public void unregistered(SelectionKey key)
          {
            listener.unregistered(key);
          }

          @Override
          public String toString()
          {
            return "Pre-connect Client listener for " + listener.toString();
          }

        };
        this.listener = clientListener;
        protocolDriver.register(channel, SelectionKey.OP_CONNECT | SelectionKey.OP_READ, this);
      }
    }
    catch (IOException ie) {
      handleException(ie);
      if (channel != null && channel.isOpen()) {
        try {
          channel.close();
        }
        catch (IOException io) {
          handleException(io);
        }
      }
    }

  }

  @Override
  public void disconnectConnection()
  {
    protocolDriver.disconnect(this);
  }
}
