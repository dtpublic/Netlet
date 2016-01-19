/**
 * Copyright (C) 2015 DataTorrent, Inc.
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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SuspendedReadTest
{
  private static final Logger logger = LoggerFactory.getLogger(SuspendedReadTest.class);

  private static class Server extends AbstractServer
  {
    @Override
    public ClientListener getClientConnection(SocketChannel client, ServerSocketChannel server)
    {
      return new AbstractClient()
      {
        @Override
        public ByteBuffer buffer()
        {
          return null;
        }

        @Override
        public void read(int len)
        {
          fail();
        }

        @Override
        public void registered(SelectionKey key)
        {
          super.registered(key);
          assertFalse(isReadSuspended());
          suspendReadIfResumed();
          assertTrue(isReadSuspended());
        }
      };
    }
  }

  private static class Client extends AbstractClient
  {
    @Override
    public ByteBuffer buffer()
    {
      fail();
      return null;
    }

    @Override
    public void read(int len)
    {
      fail();
    }
  }

  @Test
  public void blockingWriteTest() throws IOException, InterruptedException
  {
    Server server = new Server();
    Client client = new Client();
    DefaultEventLoop eventLoop = DefaultEventLoop.createEventLoop("test");
    eventLoop.start();
    eventLoop.start(new InetSocketAddress("localhost", 5035), server);
    eventLoop.connect(new InetSocketAddress("localhost", 5035), client);
    byte[] data = new byte[1024];
    int i = 0;
    while(client.send(data))
    {
      i++;
    }
    logger.debug("sent {} KB of data.", i);
    assertFalse(client.send(data));
  }
}
