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

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultServerTest
{
  private static class ClientWithPrivateConstructor extends AbstractClient
  {
    private ClientWithPrivateConstructor()
    {

    }

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
  public void getClientConnection() throws Exception
  {
    final DefaultServer<ClientWithPrivateConstructor> server = new DefaultServer<ClientWithPrivateConstructor>(ClientWithPrivateConstructor.class);
    final Listener.ClientListener clientConnection = server.getClientConnection(null, null);
    assertTrue(clientConnection instanceof ClientWithPrivateConstructor);
  }

}
