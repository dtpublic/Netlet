/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.datatorrent.netlet;

import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import static java.lang.Thread.sleep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author Chetan Narsude <chetan@datatorrent.com>
 */
public class ServerTest
{
  public ServerTest()
  {
  }

  static class ServerImpl extends Server
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