/**
 * Copyright (C) 2016 DataTorrent, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.netlet;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import static com.datatorrent.netlet.DefaultServerTest.startDefaultServer;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AbstractClientListenerTest
{
  private static final class Client extends AbstractClientListener
  {
    private final CountDownLatch connected;

    @SuppressWarnings("unused")
    private Client()
    {
      this(null);
    }

    private Client(CountDownLatch connected)
    {
      this.connected = connected;
    }

    @Override
    public void read() throws IOException
    {
      fail();
    }

    @Override
    public void write() throws IOException
    {
      fail();
    }

    @Override
    public void registered(SelectionKey key)
    {
      super.registered(key);
      if (connected == null) {
        key.interestOps(0);
      }
    }

    @Override
    public void connected()
    {
      super.connected();
      if (connected != null) {
        key.interestOps(0);
        connected.countDown();
      }
    }
  }

  @Test
  public void shutdownIO() throws Exception
  {
    final DefaultEventLoop eventLoop = DefaultEventLoop.createEventLoop("AbstractClientListenerTest");
    final Thread thread = eventLoop.start();

    final CountDownLatch connected = new CountDownLatch(1);
    final AbstractClientListener clientListener = new Client(connected);
    assertFalse(clientListener.isConnected());
    eventLoop.connect(startDefaultServer(eventLoop, Client.class), clientListener);
    connected.await();
    assertTrue(clientListener.isConnected());
    final Socket socket = ((SocketChannel)clientListener.key.channel()).socket();
    assertFalse(socket.isInputShutdown());
    assertFalse(socket.isOutputShutdown());
    clientListener.shutdownIO(true);
    assertTrue(socket.isInputShutdown());
    clientListener.shutdownIO(false);
    assertTrue(socket.isOutputShutdown());
    eventLoop.stop();
    thread.join();
    assertFalse(clientListener.isConnected());
  }

}
