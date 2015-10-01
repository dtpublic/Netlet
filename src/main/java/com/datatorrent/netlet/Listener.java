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
import java.nio.channels.SelectionKey;

/**
 * An interface common to all the listeners interested in the network events.
 *
 * @since 1.0.0
 */
public interface Listener
{
  /**
   * Address the exception thrown which was caught while performing some operation
   * (networking or application logic) related to underlying connection the listener
   * is interested in.
   *
   * @param exception The exception that was caught by the networking library.
   * @param eventloop The eventloop with which the connection is registered.
   */
  public void handleException(Exception exception, EventLoop eventloop);

  /**
   * Notify the listener as soon as the underlying connection is registered with
   * the eventloop to track various events on the connection.
   * For server, the listener is notified as soon as server binds to the given
   * interface and is ready to accept new connections.
   * For clients, the listener is notified as soon as client sends the connect
   * request to the server. On the server side, as soon as a connection is accepted,
   * the listener corresponding to the connection is notified. This connection is
   * treated as if it's a client connection.
   *
   * @param key key associated with selectable channel supporting this connection.
   */
  public void registered(SelectionKey key);

  /**
   * Notify the listener that underlying channel has ceased tracking various events.
   * This typically happens with the server when it stops listening, happens with
   * the client when they disconnect. It's also possible to explicitly unregister
   * with the eventloop without disconnecting.
   *
   * @param key key associated with the selectable channel supporting this connection.
   */
  public void unregistered(SelectionKey key);

  /**
   * Interface that listener who is interested in client events must implement.
   * There are two types of clients which must implement this listener. The first type
   * is needed when a connection is established from client software using connect call.
   * The second type is needed when a server accepts a connection from a remote client.
   * The local port of this remote client is also treated as a client.
   */
  public static interface ClientListener extends Listener
  {
    /**
     * Callback to notify the listener that underlying channel has some data to read.
     * Irrespective of the interest, this callback is made on the listener every time
     * there is data to read. The reason for this decision is that remote disconnections
     * also result in read event on the local port. So it's listener's responsibility
     * to take appropriate action if the read was not expected or disconnect to release
     * local resources if remote disconnection cause the read call.
     *
     * @throws IOException
     */
    public void read() throws IOException;

    /**
     * Callback to notify that listener that underlying channel has room to write more data.
     * This callback is made only if the listener has expressed interest in the write
     * events using selection key provided during registration.
     *
     * @throws IOException
     */
    public void write() throws IOException;

    /**
     * When a connection is established between client and the server, listeners at both ends
     * of such a connection are notified.
     */
    public void connected();

    /**
     * When the connections between two clients ceases to exist, listeners at both the ends
     * are notified of this event.
     */
    public void disconnected();

  }

}
