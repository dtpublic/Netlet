/*
 * Copyright (c) 2013 DataTorrent, Inc. ALL Rights Reserved.
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

import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import static java.lang.Thread.sleep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ServerTest
{
  public ServerTest()
  {
  }

  static class ServerImpl extends AbstractServer
  {
    @Override
    public ClientListener getClientConnection(SocketChannel sc, ServerSocketChannel ssc)
    {
      return new EchoClient();
    }

    @Override
    public String toString()
    {
      return "ServerImpl{" + '}';
    }

  }

  static class EchoClient extends AbstractClient
  {
    ByteBuffer buffer = ByteBuffer.allocate(1024 * 4);

    @Override
    public ByteBuffer buffer()
    {
      buffer.clear();
      return buffer;
    }

    @Override
    public String toString()
    {
      return "EchoClient{" + "buffer=" + buffer + '}';
    }

    @Override
    @SuppressWarnings("SleepWhileInLoop")
    public void read(int len)
    {
      byte[] array = new byte[len];
      System.arraycopy(buffer.array(), 0, array, 0, len);
      try {
        while (!send(array, 0, len)) {
          sleep(5);
        }
      }
      catch (InterruptedException ie) {
        throw new RuntimeException(ie);
      }
    }

    @Override
    public void connected()
    {
    }

    @Override
    public void disconnected()
    {
    }

  }

  private static final Logger logger = LoggerFactory.getLogger(ServerTest.class);
}