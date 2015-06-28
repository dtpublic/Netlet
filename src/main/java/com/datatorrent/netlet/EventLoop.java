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

import java.net.InetSocketAddress;

import com.datatorrent.netlet.Listener.ClientListener;
import com.datatorrent.netlet.Listener.ServerListener;
import com.datatorrent.netlet.Listener.UDPServerListener;

/**
 * <p>EventLoop interface.</p>
 *
 * @since 1.0.0
 */
public interface EventLoop
{
  void connect(final InetSocketAddress address, final Listener l);

  void connect(final InetSocketAddress address, final Listener l, ConnectionType connectionType);

  void disconnect(final ClientListener l);

  //void register(ServerSocketChannel channel, Listener l);

  //void register(SocketChannel channel, int ops, Listener l);

  void start(final String host, final int port, final ServerListener l);

  void startUDP(final String host, final int port, final UDPServerListener l);

  void stop(final ServerListener l);

  void stopUDP(Listener l);

  void submit(Runnable r);

  //void unregister(final SelectableChannel c);

}
