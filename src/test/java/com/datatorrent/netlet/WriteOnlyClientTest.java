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
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.datatorrent.netlet.util.Slice;

import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
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
@PrepareForTest(WriteOnlyClient.class)
@PowerMockIgnore("org.apache.*")
public class WriteOnlyClientTest
{
  @Mock
  SelectionKey key;
  SocketChannel channel;

  private WriteOnlyClient connect(final int ops) throws Exception
  {
    final WriteOnlyClient writeOnlyClient = spy(new WriteOnlyClient());

    channel = mock(SocketChannel.class);
    when(key.interestOps()).thenReturn(ops);
    when(key.channel()).thenReturn(channel);
    when(key.isValid()).thenReturn(true);
    when(key.interestOps(anyInt())).thenReturn(key);
    when(key.selector()).thenReturn(mock(Selector.class));
    when(channel.isConnected()).thenReturn(true);

    assertFalse(writeOnlyClient.isConnected());
    writeOnlyClient.registered(key);
    writeOnlyClient.connected();
    verify(channel).shutdownInput();
    verify(channel, never()).shutdownOutput();
    if ((ops & OP_READ) == OP_READ) {
      ArgumentMatcher<Integer> matcher = new ArgumentMatcher<Integer>()
      {
        @Override
        public boolean matches(Object argument)
        {
          return ((Integer)argument).intValue() == (ops & ~OP_READ);
        }
      };
      verify(key).interestOps(intThat(matcher));
    }
    assertTrue(writeOnlyClient.isConnected());
    return writeOnlyClient;
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
    final WriteOnlyClient writeOnlyClient = connect(OP_READ);

    writeOnlyClient.read();
    verify(key, times(2)).interestOps(0);

    when(key.interestOps()).thenReturn(0);
    writeOnlyClient.read();
    verify(key, times(2)).interestOps(0);
    verify(channel, never()).read(any(ByteBuffer.class));
    verify(channel, never()).write(writeOnlyClient.writeBuffer);
    verify(channel, never()).close();
  }

  @Test
  public void write() throws Exception
  {
    final WriteOnlyClient writeOnlyClient = connect(OP_WRITE);

    writeOnlyClient.write();
    verify(key).interestOps(0);
    verify(channel, never()).write(any(ByteBuffer.class));
    verify(channel, never()).close();

    writeOnlyClient.writeBuffer.put((byte)0);
    writeOnlyClient.write();
    verify(key).interestOps(0);
    verify(channel).write(writeOnlyClient.writeBuffer);
    verify(channel, never()).close();

    final Slice slice = new Slice(new byte[] {0});
    writeOnlyClient.sendQueue.add(slice);
    writeOnlyClient.write();
    assertSame(slice, writeOnlyClient.freeQueue.poll());
    assertNull(slice.buffer);
    verify(key).interestOps(0);
    verify(channel, times(2)).write(writeOnlyClient.writeBuffer);
    verify(channel, never()).close();
  }

  @Test
  public void send() throws Exception
  {
    final WriteOnlyClient writeOnlyClient = connect(0);
    assertTrue(writeOnlyClient.send(new byte[1]));
    Slice f = writeOnlyClient.sendQueue.peek();
    assertNotNull(f);
    assertNotNull(f.buffer);
    assertEquals(1, f.length);
    assertEquals(0, f.offset);
    assertTrue(writeOnlyClient.isWriteEnabled);
    verify(key).interestOps(OP_WRITE);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void resumeReadIfSuspended() throws Exception
  {
    final WriteOnlyClient writeOnlyClient = connect(OP_READ);
    writeOnlyClient.resumeReadIfSuspended();
    fail();
  }
}
