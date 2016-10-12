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

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.intThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.spy;

@SuppressWarnings("Since15")
@RunWith(PowerMockRunner.class)
@PrepareForTest(AbstractReadOnlyClient.class)
@PowerMockIgnore("org.apache.*")
public class AbstractReadOnlyClientTest
{
  @Mock
  SelectionKey key;
  SocketChannel channel;

  private AbstractReadOnlyClient connect(final int ops) throws Exception
  {
    final AbstractReadOnlyClient abstractReadOnlyClient = new AbstractReadOnlyClient()
    {
      @Override
      public ByteBuffer buffer()
      {
        return null;
      }

      @Override
      public void read(int len)
      {

      }
    };

    final AbstractReadOnlyClient readOnlyClient = spy(abstractReadOnlyClient);
    channel = mock(SocketChannel.class);
    when(key.interestOps()).thenReturn(ops);
    when(key.channel()).thenReturn(channel);
    when(key.isValid()).thenReturn(true);
    when(channel.isConnected()).thenReturn(true);

    assertFalse(readOnlyClient.isConnected());
    readOnlyClient.registered(key);
    readOnlyClient.connected();
    verify(channel).shutdownOutput();
    verify(channel, never()).shutdownInput();
    if ((ops & OP_WRITE) == OP_WRITE) {
      ArgumentMatcher<Integer> matcher = new ArgumentMatcher<Integer>()
      {
        @Override
        public boolean matches(Object argument)
        {
          return ((Integer)argument).intValue() == (ops & ~OP_WRITE);
        }
      };
      verify(key).interestOps(intThat(matcher));
    }
    assertTrue(readOnlyClient.isConnected());
    return readOnlyClient;
  }

  @Test
  public void connected() throws Exception
  {
    connect(0);

    connect(OP_READ);

    connect(OP_WRITE);

    connect(OP_CONNECT | OP_READ);

    connect(OP_CONNECT | OP_WRITE);

    connect(OP_READ | OP_WRITE);
  }

  @Test
  public void read() throws Exception
  {
    final AbstractReadOnlyClient readOnlyClient = connect(OP_READ);

    ByteBuffer byteBuffer = mock(ByteBuffer.class);
    when(readOnlyClient.buffer()).thenReturn(byteBuffer);

    when(readOnlyClient.buffer().remaining()).thenReturn(1);
    readOnlyClient.read();
    verify(key, never()).interestOps(0);
    verify(readOnlyClient, never()).read(anyInt());
    verify(channel, never()).close();

    when(readOnlyClient.buffer().remaining()).thenReturn(0);
    readOnlyClient.read();
    verify(key).interestOps(0);
    verify(readOnlyClient, never()).read(anyInt());
    verify(channel, never()).close();

    when(channel.read(byteBuffer)).thenReturn(1);
    readOnlyClient.read();
    verify(key).interestOps(0);
    verify(readOnlyClient).read(1);
    verify(channel, never()).close();

    when(channel.read(byteBuffer)).thenReturn(-1);
    readOnlyClient.read();
    verify(key).interestOps(0);
    verify(channel).close();
    assertFalse(readOnlyClient.isConnected());
  }

  @Test
  public void write() throws Exception
  {
    final AbstractReadOnlyClient readOnlyClient = connect(OP_WRITE);
    readOnlyClient.write();
    verify(key, times(2)).interestOps(0);
    when(key.interestOps()).thenReturn(0);
    readOnlyClient.write();
    verify(key, times(2)).interestOps(0);
    verify(readOnlyClient, never()).buffer();
    verify(readOnlyClient, never()).read();
    verify(readOnlyClient, never()).read(anyInt());
    verify(channel, never()).close();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void resumeWriteIfSuspended() throws Exception
  {
    final AbstractReadOnlyClient readOnlyClient = connect(OP_WRITE);
    readOnlyClient.resumeWriteIfSuspended();
    fail();
  }
}
