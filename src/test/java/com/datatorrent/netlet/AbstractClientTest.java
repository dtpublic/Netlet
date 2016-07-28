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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.ThreadDeathWatcher;

import com.datatorrent.netlet.util.CircularBuffer;
import com.datatorrent.netlet.util.Slice;

import static com.datatorrent.netlet.Listener.ClientListener;
import static com.datatorrent.netlet.Listener.ServerListener;

import static java.lang.Thread.sleep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AbstractClientTest
{
  public AbstractClientTest()
  {
  }

  @Override
  public String toString()
  {
    return "ClientTest{" + '}';
  }

  public class ClientImpl extends AbstractClient
  {
    public static final int BUFFER_CAPACITY = 8 * 1024 + 1;
    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_CAPACITY);
    boolean read;

    @Override
    public String toString()
    {
      return "ClientImpl{" + "buffer=" + buffer + ", read=" + read + '}';
    }

    @Override
    public ByteBuffer buffer()
    {
      return buffer;
    }

    @Override
    public void read(int len)
    {
      if (buffer.position() == buffer.capacity()) {
        buffer.flip();
        read = true;

        int i = 0;
        LongBuffer lb = buffer.asLongBuffer();
        while (lb.hasRemaining()) {
          assertEquals(i++, lb.get());
        }

        assert (i == BUFFER_CAPACITY / 8);
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

    private CircularBuffer<Slice> getSendBuffer4Polls()
    {
      return sendBuffer4Polls;
    }

    private void setSendBuffer4Polls(CircularBuffer<Slice> circularBuffer)
    {
      sendBuffer4Polls = circularBuffer;
    }

    private CircularBuffer<Slice> getSendBuffer4Offers()
    {
      return sendBuffer4Offers;
    }

    private void setSendBuffer4Offers(CircularBuffer<Slice> circularBuffer)
    {
      sendBuffer4Offers = circularBuffer;
    }

    private ByteBuffer getWriteBuffer()
    {
      return writeBuffer;
    }

  }

  public void sendData()
  {
  }

  @SuppressWarnings("SleepWhileInLoop")
  private void verifySendReceive(final DefaultEventLoop el, final int port) throws IOException, InterruptedException
  {
    ServerListener si = new DefaultServer(ServerTest.EchoClient.class);
    ClientImpl ci = new ClientImpl();

    new Thread(el).start();

    el.start("localhost", port, si);
    el.connect(new InetSocketAddress("localhost", port), ci);

    ByteBuffer outboundBuffer = ByteBuffer.allocate(ClientImpl.BUFFER_CAPACITY);
    LongBuffer lb = outboundBuffer.asLongBuffer();

    int i = 0;
    while (lb.hasRemaining()) {
      lb.put(i++);
    }

    boolean odd = false;
    outboundBuffer.position(i * 8);
    while (outboundBuffer.hasRemaining()) {
      outboundBuffer.put((byte)(odd ? 0x55 : 0xaa));
      odd = !odd;
    }

    byte[] array = outboundBuffer.array();
    while (!ci.send(array, 0, array.length)) {
      sleep(5);
    }

    sleep(100);

    el.disconnect(ci);
    el.stop(si);
    el.stop();
    assertTrue(ci.read);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testWithDefault() throws IOException, InterruptedException
  {
    verifySendReceive(new DefaultEventLoop("test"), 5033);
  }

  @Test
  public void testWithOptimized() throws IOException, InterruptedException
  {
    verifySendReceive(new OptimizedEventLoop("test"), 5034);
  }

  @Test
  public void testCreateEventLoop() throws IOException
  {
    assertEquals(OptimizedEventLoop.class, DefaultEventLoop.createEventLoop("test").getClass());
    System.setProperty(DefaultEventLoop.eventLoopPropertyName, "");
    assertEquals(DefaultEventLoop.class, DefaultEventLoop.createEventLoop("test").getClass());
    System.setProperty(DefaultEventLoop.eventLoopPropertyName, "false");
    assertEquals(OptimizedEventLoop.class, DefaultEventLoop.createEventLoop("test").getClass());
    System.setProperty(DefaultEventLoop.eventLoopPropertyName, "true");
    assertEquals(DefaultEventLoop.class, DefaultEventLoop.createEventLoop("test").getClass());
    System.setProperty(DefaultEventLoop.eventLoopPropertyName, "no");
    assertEquals(OptimizedEventLoop.class, DefaultEventLoop.createEventLoop("test").getClass());
    System.setProperty(DefaultEventLoop.eventLoopPropertyName, "yes");
    assertEquals(DefaultEventLoop.class, DefaultEventLoop.createEventLoop("test").getClass());
    System.clearProperty(DefaultEventLoop.eventLoopPropertyName);
  }

  @Test
  public void testOneSlice() throws IOException
  {
    ClientImpl ci = new ClientImpl();
    ci.setSendBuffer4Polls(new CircularBufferWrapper(1));
    ci.setSendBuffer4Offers(ci.getSendBuffer4Polls());
    ci.key = new SelectionKey()
    {
      private int interestOps;

      @SuppressWarnings("Since15")
      SocketChannel channel = new SocketChannel(null)
      {
        @Override
        public SocketChannel bind(SocketAddress local) throws IOException
        {
          return null;
        }

        @Override
        public <T> SocketChannel setOption(java.net.SocketOption<T> name, T value) throws IOException
        {
          return null;
        }

        @Override
        public SocketChannel shutdownInput() throws IOException
        {
          return null;
        }

        @Override
        public SocketChannel shutdownOutput() throws IOException
        {
          return null;
        }

        @Override
        public Socket socket()
        {
          return null;
        }

        @Override
        public boolean isConnected()
        {
          return false;
        }

        @Override
        public boolean isConnectionPending()
        {
          return false;
        }

        @Override
        public boolean connect(SocketAddress remote) throws IOException
        {
          return false;
        }

        @Override
        public boolean finishConnect() throws IOException
        {
          return false;
        }

        @Override
        public SocketAddress getRemoteAddress() throws IOException
        {
          return null;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException
        {
          return 0;
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException
        {
          return 0;
        }

        @Override
        public int write(ByteBuffer src) throws IOException
        {
          final int remaining = src.remaining();
          src.position(src.position() + remaining);
          return remaining;
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException
        {
          return 0;
        }

        @Override
        protected void implCloseSelectableChannel() throws IOException
        {

        }

        @Override
        protected void implConfigureBlocking(boolean block) throws IOException
        {

        }

        @Override
        public SocketAddress getLocalAddress() throws IOException
        {
          return null;
        }

        @Override
        public <T> T getOption(java.net.SocketOption<T> name) throws IOException
        {
          return null;
        }

        @Override
        public Set<java.net.SocketOption<?>> supportedOptions()
        {
          return null;
        }
      };

      @Override
      public SelectableChannel channel()
      {
        return channel;
      }

      @Override
      public Selector selector()
      {
        return null;
      }

      @Override
      public boolean isValid()
      {
        return true;
      }

      @Override
      public void cancel()
      {

      }

      @Override
      public int interestOps()
      {
        return interestOps;
      }

      @Override
      public SelectionKey interestOps(int ops)
      {
        if ((ops & ~OP_WRITE) != 0) {
          throw new IllegalArgumentException();
        }
        interestOps = ops;
        return this;
      }

      @Override
      public int readyOps()
      {
        return OP_WRITE;
      }
    };
    ci.send(new byte[ci.getWriteBuffer().remaining()]);
    ci.write();
  }

  private static class CircularBufferWrapper extends CircularBuffer<Slice>
  {
    private CircularBufferWrapper(int capacity)
    {
      super(capacity);
    }

    @Override
    public Slice pollUnsafe()
    {
      Slice f = super.pollUnsafe();
      assertTrue("Unexpected slice length: " + f.length, f.length > 0);
      return f;
    }

    @Override
    public Slice peekUnsafe()
    {
      Slice f = super.peekUnsafe();
      assertTrue("Unexpected slice length: " + f.length, f.length > 0);
      return f;
    }

  }

  static void verifyUnresolvedException(final AbstractClient client, final DefaultEventLoop eventLoop,
      final CountDownLatch handled) throws IOException, InterruptedException
  {
    final Thread thread = watchForDefaultEventLoopThread(eventLoop.start(), handled);

    eventLoop.connect(new InetSocketAddress("not a valid host name", 5035), client);
    handled.await();

    NetletThrowable netletThrowable = client.throwables.poll();
    assertFalse("Client is connected to invalid address", client.isConnected());
    assertTrue("Default event loop thread is not alive", thread.isAlive());
    assertNotNull(netletThrowable);
    eventLoop.stop();
  }

  @Test
  public void testUnresolvedException() throws IOException, InterruptedException
  {
    final DefaultEventLoop eventLoop = DefaultEventLoop.createEventLoop("test");
    final CountDownLatch handled = new CountDownLatch(1);
    final ClientImpl client = new ClientImpl()
    {
      @Override
      public void handleException(Exception cce, EventLoop el)
      {
        assertSame(el, eventLoop);
        assertTrue(cce instanceof RuntimeException);
        assertTrue(cce.getCause() instanceof UnresolvedAddressException);
        super.handleException(cce, el);
        handled.countDown();
      }
    };
    verifyUnresolvedException(client, eventLoop, handled);
  }

  static void verifyExceptionsInClientCallback(final AbstractClient client, final DefaultEventLoop eventLoop,
      final CountDownLatch handled) throws IOException, InterruptedException
  {
    final Thread thread = watchForDefaultEventLoopThread(eventLoop.start(), handled);

    final CountDownLatch registered = new CountDownLatch(1);
    AbstractServer server = new DefaultServer(ServerTest.EchoClient.class)
    {
      @Override
      public void registered(SelectionKey key)
      {
        super.registered(key);
        registered.countDown();
      }
    };
    eventLoop.start(null, 0, server);
    registered.await();

    eventLoop.connect((InetSocketAddress)server.boundAddress, client);
    try {
      client.send("test".getBytes());
      handled.await();
      assertTrue("Default event loop thread is not alive", thread.isAlive());
      // TODO: replace with fail
      assertNotNull(client.throwables.peek());
      //fail("No exception was thrown");
    } catch (Exception e) {
      assertTrue(e instanceof NetletThrowable.NetletRuntimeException);
    } finally {
      if (thread.isAlive()) {
        eventLoop.stop(server);
        eventLoop.disconnect(client);
        eventLoop.stop();
      }
    }
  }

  @Test
  public void testExceptionsInClientCallback() throws IOException, InterruptedException
  {
    final DefaultEventLoop eventLoop = DefaultEventLoop.createEventLoop("test");
    final CountDownLatch handled = new CountDownLatch(1);

    ClientImpl client = new ClientImpl()
    {
      RuntimeException exception;
      @Override
      public void connected()
      {
        exception = new RuntimeException();
        throw exception;
      }

      @Override
      public void handleException(Exception cce, EventLoop el)
      {
        assertSame(exception, cce);
        assertSame(eventLoop, el);
        super.handleException(cce, el);
        handled.countDown();
      }
    };
    verifyExceptionsInClientCallback(client, eventLoop, handled);
  }

  private static Thread watchForDefaultEventLoopThread(final Thread thread, final CountDownLatch latch)
  {
    ThreadDeathWatcher.watch(thread, new Runnable()
    {
      @Override
      public void run()
      {
        latch.countDown();
      }
    });
    return thread;
  }

  private static class SuspendedReadClient extends AbstractClient
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

    @Override
    public void registered(SelectionKey key)
    {
      super.registered(key);
      assertFalse(isReadSuspended());
      assertTrue(suspendReadIfResumed());
      assertTrue(isReadSuspended());
    }
  }

  private static class ResumedReadClient extends AbstractClient
  {
    private final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1024);
    private int count;

    @Override
    public ByteBuffer buffer()
    {
      return byteBuffer;
    }

    @Override
    public void read(int len)
    {
      count += len;
      byteBuffer.clear();
    }

    @Override
    public void registered(SelectionKey key)
    {
      super.registered(key);
      assertTrue(isReadSuspended());
      assertTrue(resumeReadIfSuspended());
      assertFalse(isReadSuspended());
    }
  }

  private static class SuspendedReadServer extends DefaultServer<SuspendedReadClient>
  {
    private final Map<SocketChannel, ClientListener> listeners = new HashMap<SocketChannel, ClientListener>();
    private final DefaultEventLoop eventLoop;
    private final CountDownLatch latch = new CountDownLatch(1);

    private SuspendedReadServer(DefaultEventLoop eventLoop)
    {
      super(SuspendedReadClient.class);
      this.eventLoop = eventLoop;
    }

    @Override
    public ClientListener getClientConnection(SocketChannel client, ServerSocketChannel server)
    {
      final ClientListener listener = super.getClientConnection(client, server);
      listeners.put(client, listener);
      latch.countDown();
      return listener;
    }

    private void resumeListeners()
    {
      for (Map.Entry<SocketChannel, ClientListener> listener : listeners.entrySet()) {
        listener.setValue(new ResumedReadClient());
        eventLoop.register(listener.getKey(), 0, listener.getValue());
      }
    }

    private void waitForConnections() throws InterruptedException
    {
      latch.await();
    }
  }

  @Test
  public void suspendedReadTest() throws Exception
  {
    final DefaultEventLoop eventLoop = DefaultEventLoop.createEventLoop("suspendedReadTest");
    final Thread thread = eventLoop.start();
    final SuspendedReadServer server = new SuspendedReadServer(eventLoop);
    eventLoop.start("localhost", 5035, server);
    final AbstractClient client = new SuspendedReadClient();
    eventLoop.connect(new InetSocketAddress("localhost", 5035), client);
    server.waitForConnections();
    byte[] data = new byte[1024];
    int i = 0;
    while (client.send(data)) {
      i++;
    }
    logger.debug("sent {} KB of data.", i);
    assertFalse(client.send(data));
    server.resumeListeners();
    sleep(2000);
    for (Map.Entry<SocketChannel, Listener.ClientListener> listener : server.listeners.entrySet()) {
      final ClientListener clientListener = listener.getValue();
      assertTrue(clientListener instanceof ResumedReadClient);
      assertEquals(i * 1024, ((ResumedReadClient)clientListener).count);
    }
    eventLoop.stop();
    thread.join();
  }

  private static final Logger logger = LoggerFactory.getLogger(AbstractClientTest.class);
}
