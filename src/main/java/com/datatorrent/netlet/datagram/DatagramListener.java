package com.datatorrent.netlet.datagram;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;

import com.datatorrent.netlet.EventLoop;
import com.datatorrent.netlet.Listener;

/**
 *
 * DatagramListener provider a virtual channel de-multiplexer on top of a DatagramChannel
 * DatagramChannel cannot be "read" from till it is connected but connecting it ties it down to one peer-client.
 * Furthermore reading from the channel returns all packets from all peers.
 * The server listener provides the ability to have separate virtual connections for packets from separate peers.
 *
 */
public class DatagramListener implements Listener.ClientListener, DatagramConnectionChannel.ConnectionAdapter
{

  UDPServerListener serverListener;
  DatagramChannel channel;

  Map<SocketAddress, DatagramSelectionKey> clientKeys = new HashMap<SocketAddress, DatagramSelectionKey>();
  private ClientListener pendingReadClient;
  boolean writeSpaceAvailable;

  ByteBuffer readBuffer;
  public int MAX_SIZE = 32 * 1024;

  public DatagramListener(UDPServerListener serverListener) {
    this.serverListener = serverListener;
  }

  @Override
  public void handleException(Exception exception, EventLoop eventloop)
  {
    // There was an exception unregistering the key
  }

  @Override
  public void registered(SelectionKey key)
  {
    channel = (DatagramChannel)key.channel();
    readBuffer = ByteBuffer.allocate(MAX_SIZE);
  }

  @Override
  public void unregistered(SelectionKey key)
  {
    for (Map.Entry<SocketAddress, DatagramSelectionKey> entry : clientKeys.entrySet()) {
      DatagramSelectionKey selKey = entry.getValue();
      ClientListener listener = (ClientListener)selKey.attachment();
      listener.unregistered(selKey);
    }
  }

  @Override
  public void read() throws IOException
  {
    if (pendingReadClient == null) {
      SocketAddress address = channel.receive(readBuffer);
      DatagramSelectionKey selectionKey = clientKeys.get(address);
      if (selectionKey == null) {
        selectionKey = newConnection(address);
      }
      pendingReadClient = (ClientListener) selectionKey.attachment();
      readBuffer.flip();
    }
    //DatagramConnectionChannel connectionChannel = (DatagramConnectionChannel)selectionKey.channel();
    pendingReadClient.read();
    if (!readBuffer.hasRemaining()) {
      pendingReadClient = null;
      readBuffer.clear();
    }
  }

  @Override
  public void write() throws IOException
  {
    writeSpaceAvailable = true;
    for (Map.Entry<SocketAddress, DatagramSelectionKey> entry : clientKeys.entrySet()) {
      ((ClientListener)entry.getValue().attachment()).write();
      if (!writeSpaceAvailable) {
        // Give a chance for underlying os buffers to clear up some space
        // In the meantime give up control so other connections can be processed
        break;
      }
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

  @Override
  public ByteBuffer getReadBuffer(DatagramConnectionChannel channel)
  {
    return readBuffer;
  }

  @Override
  public int write(ByteBuffer b, DatagramConnectionChannel channel) throws IOException
  {
    SocketAddress socketAddress = channel.getSocketAddress();
    int length = b.remaining();
    int sent = this.channel.send(b, socketAddress);
    if (sent < length) {
      writeSpaceAvailable = false;
    }
    return sent;
  }

  private DatagramSelectionKey newConnection(SocketAddress address) {
    DatagramConnectionChannel connectionChannel = new DatagramConnectionChannel(address, this);
    DatagramSelectionKey key = new DatagramSelectionKey(connectionChannel);
    ClientListener clientListener = serverListener.getClientConnection(connectionChannel);
    key.attach(clientListener);
    clientKeys.put(address, key);
    clientListener.registered(key);
    return key;
  }
}
