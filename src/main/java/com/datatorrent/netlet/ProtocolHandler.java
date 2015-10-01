/*
 * Copyright (c) 2015 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.netlet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;

/**
 * <p>ProtocolHandler interface.</p>
 *
 */
public interface ProtocolHandler
{
  void init(ProtocolDriver protocolDriver);
  void handleSelectedKey(final SelectionKey sk) throws IOException;
  ProtocolDriver getProtocolDriver();
  void registered(SelectionKey key);
  void unregistered(SelectionKey key);
  void handleException(Exception e);

  interface ServerProtocolHandler extends ProtocolHandler {
    void start(final String host, final int port);
    void stopServer();
  }

  interface ClientProtocolHandler extends ProtocolHandler {
    void connect(InetSocketAddress address);
    // The following methods are listener view and will be separated into a different interface
    void disconnectFromDriver();
  }

  ProtocolHandler NOOP_HANDLER = new ProtocolHandler()
  {
    @Override
    public void init(ProtocolDriver protocolDriver)
    {

    }

    @Override
    public void handleSelectedKey(SelectionKey sk) throws IOException
    {

    }

    @Override
    public ProtocolDriver getProtocolDriver()
    {
      return null;
    }

    @Override
    public void registered(SelectionKey key)
    {

    }

    @Override
    public void unregistered(SelectionKey key)
    {

    }

    @Override
    public void handleException(Exception e)
    {

    }
  };

  ClientProtocolHandler NOOP_CLIENT_HANDLER = new ClientProtocolHandler() {

    @Override
    public void handleException(Exception exception)
    {

    }

    @Override
    public void disconnectFromDriver()
    {

    }

    @Override
    public void registered(SelectionKey key)
    {

    }

    @Override
    public void unregistered(SelectionKey key)
    {

    }

    @Override
    public void init(ProtocolDriver protocolDriver)
    {

    }

    @Override
    public void handleSelectedKey(SelectionKey sk) throws IOException
    {

    }

    @Override
    public ProtocolDriver getProtocolDriver()
    {
      return null;
    }

    @Override
    public void connect(InetSocketAddress address)
    {

    }
  };

  /**
   * Listener to facilitate graceful handling of the events when the disconnection
   * is initiated by the local end of the connection.
   * When local end initiates the disconnection, it's possible that before it actually
   * disconnects, it may receive data, or disconnection request from the other end. It's
   * also possible that various writers to the connections are not properly talking
   * to each other and some may continue to write to the connection after after it's
   * disconnected. This listener ensures that only the writes requested before disconnection
   * request are processed and others are rejected.
   */
  /*
  TO BE DONE
  static class DisconnectingClientHandler implements ClientProtocolHandler
  {

    private final ClientProtocolHandler previous;
    private final SelectionKey key;

    public DisconnectingClientHandler(SelectionKey key)
    {
      this.key = key;
      previous = (ClientProtocolHandler)key.attachment();
    }

    @Override
    public void read() throws IOException
    {
      disconnect();
    }

    /--**
     * Disconnect if there is no write interest on this socket.
     *--/
    private void disconnect()
    {
      if (!key.isValid() || (key.interestOps() & SelectionKey.OP_WRITE) == 0) {
        previous.disconnected();
        key.attach(null);
        try {
          key.channel().close();
        }
        catch (IOException ie) {
          logger.warn("exception while closing socket", ie);
        }
      }
    }

    @Override
    public void write() throws IOException
    {
      try {
        previous.write();
      }
      catch (IOException ex) {
        key.cancel();
        throw ex;
      }
      catch (RuntimeException re) {
        key.cancel();
        throw re;
      }
      finally {
        disconnect();
      }
    }

    @Override
    public void handleException(Exception cce, EventLoop el)
    {
      previous.handleException(cce, el);
    }

    @Override
    public void registered(SelectionKey key)
    {
    }

    @Override
    public void unregistered(SelectionKey key)
    {
    }

    @Override
    public void connected()
    {
    }

    @Override
    public void disconnected()
    {
    }

    private static final Logger logger = LoggerFactory.getLogger(DisconnectingClientHandler.class);
  }
  */


}
