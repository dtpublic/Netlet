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
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import static com.datatorrent.netlet.AbstractClientTest.verifyExceptionsInClientCallback;
import static com.datatorrent.netlet.AbstractClientTest.verifyUnresolvedException;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class AbstractLengthPrependerClientTest
{
  @Test
  public void testUnresolvedException() throws IOException, InterruptedException
  {
    final DefaultEventLoop eventLoop = DefaultEventLoop.createEventLoop("test");
    final CountDownLatch handled = new CountDownLatch(1);
    final AbstractLengthPrependerClient ci = new AbstractLengthPrependerClient()
    {
      @Override
      public void onMessage(byte[] buffer, int offset, int size)
      {
        fail();
      }

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
    verifyUnresolvedException(ci, eventLoop, handled);
  }

  @Test
  public void testExceptionsInClientCallback() throws IOException, InterruptedException
  {
    final DefaultEventLoop eventLoop = DefaultEventLoop.createEventLoop("test");
    final CountDownLatch handled = new CountDownLatch(1);

    final AbstractLengthPrependerClient client = new AbstractLengthPrependerClient()
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

      @Override
      public void onMessage(byte[] buffer, int offset, int size)
      {

      }
    };
    verifyExceptionsInClientCallback(client, eventLoop, handled);
  }
}

