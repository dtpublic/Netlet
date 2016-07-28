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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultServer<T extends Listener.ClientListener> extends AbstractServer
{
  private static final Logger logger = LoggerFactory.getLogger(DefaultServer.class);

  private final Constructor<T> constructor;

  public DefaultServer(Class<T> clientListenerClass)
  {
    try {
      constructor = clientListenerClass.getDeclaredConstructor();
      constructor.setAccessible(true);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public ClientListener getClientConnection(SocketChannel client, ServerSocketChannel server)
  {
    try {
      return constructor.newInstance();
    } catch (InvocationTargetException e) {
      logger.error("{}", constructor, e);
      throw new RuntimeException(e.getTargetException());
    } catch (InstantiationException e) {
      logger.error("{}", constructor, e);
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      logger.error("{}", constructor, e);
      throw new RuntimeException(e);
    }
  }
}
