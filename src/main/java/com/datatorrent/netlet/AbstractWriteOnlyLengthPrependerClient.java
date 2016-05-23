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
import java.nio.channels.SelectionKey;

import com.datatorrent.netlet.util.Slice;
import com.datatorrent.netlet.util.VarInt;

public class AbstractWriteOnlyLengthPrependerClient extends AbstractWriteOnlyClient
{
  private boolean newMessage = true;

  public AbstractWriteOnlyLengthPrependerClient(final int sendBufferCapacity)
  {
    this(64 * 1024, sendBufferCapacity);
  }

  public AbstractWriteOnlyLengthPrependerClient(final int writeBufferCapacity, final int sendBufferCapacity)
  {
    super(writeBufferCapacity, sendBufferCapacity);
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
      if (newMessage) {
        if (remaining < 5) {
          channelWrite();
          return;
        } else {
          VarInt.write(f.length, writeBuffer);
          newMessage = false;
        }
      }
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
        newMessage = true;
      }
    } while ((f = sendQueue.peek()) != null);
    channelWrite();
  }

}
