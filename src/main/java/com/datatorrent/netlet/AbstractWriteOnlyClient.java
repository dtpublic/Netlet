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
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.jctools.queues.SpscArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.netlet.util.Slice;

public class AbstractWriteOnlyClient extends AbstractClientListener
{
  private static final Logger logger = LoggerFactory.getLogger(AbstractWriteOnlyClient.class);

  protected final ByteBuffer writeBuffer;
  protected final SpscArrayQueue<Slice> sendQueue;
  protected final SpscArrayQueue<Slice> freeQueue;
  protected boolean write = true;

  public AbstractWriteOnlyClient()
  {
    this(64 * 1024, 1024);
  }

  public AbstractWriteOnlyClient(final int writeBufferCapacity, final int sendQueueCapacity)
  {
    this(ByteBuffer.allocateDirect(writeBufferCapacity), sendQueueCapacity);
  }

  public AbstractWriteOnlyClient(final ByteBuffer writeBuffer, final int sendQueueCapacity)
  {
    this.writeBuffer = writeBuffer;
    sendQueue = new SpscArrayQueue<Slice>(sendQueueCapacity);
    freeQueue = new SpscArrayQueue<Slice>(sendQueueCapacity);
  }

  @Override
  public void connected()
  {
    super.connected();
    shutdownIO(true);
    suspendReadIfResumed();
  }

  @Override
  public final void read() throws IOException
  {
    if (suspendReadIfResumed()) {
      logger.warn("{} OP_READ should be disabled", this);
    } else {
      logger.error("{} read is not expected", this);
    }
  }

  @Override
  public void write() throws IOException
  {
    /*
     * at first when we enter this function, our buffer is in fill mode.
     */
    int remaining = writeBuffer.remaining();
    if (remaining == 0) {
      channelWrite();
      return;
    }
    Slice f = sendQueue.peek();
    if (f == null) {
      synchronized (sendQueue) {
        f = sendQueue.peek();
        if (f == null) {
          key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
          write = false;
          return;
        }
      }
    }
    do {
      if (remaining < f.length) {
        writeBuffer.put(f.buffer, f.offset, remaining);
        f.offset += remaining;
        f.length -= remaining;
        channelWrite();
        return;
      } else {
        writeBuffer.put(f.buffer, f.offset, f.length);
        remaining -= f.length;
        freeQueue.offer(sendQueue.poll());
      }
    } while ((f = sendQueue.peek()) != null);
    channelWrite();
  }

  protected void channelWrite() throws IOException
  {
    writeBuffer.flip();
    final SocketChannel channel = (SocketChannel)key.channel();
    channel.write(writeBuffer);
    writeBuffer.compact();
  }

  public boolean send(byte[] array)
  {
    return send(array, 0, array.length);
  }

  public boolean send(byte[] array, int offset, int len)
  {
    /*
    if (!throwables.isEmpty()) {
      NetletThrowable.Util.throwRuntime(throwables.pollUnsafe());
    }
    */

    Slice f = freeQueue.poll();
    if (f == null) {
      f = new Slice(array, offset, len);
    } else {
      f.buffer = array;
      f.offset = offset;
      f.length = len;
    }

    if (sendQueue.offer(f)) {
      synchronized (sendQueue) {
        if (!write) {
          key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
          write = true;
          key.selector().wakeup();
        }
      }
      return true;
    }

    return false;
  }
}
