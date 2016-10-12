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

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

class RestrictedOpsSelectionKey extends SelectionKey
{
  private final SelectionKey key;
  private final int ops;

  public RestrictedOpsSelectionKey(SelectionKey key, int ops)
  {
    this.key = key;
    this.ops = ops;
  }

  @Override
  public SelectableChannel channel()
  {
    return key.channel();
  }

  @Override
  public Selector selector()
  {
    return key.selector();
  }

  @Override
  public boolean isValid()
  {
    return key.isValid();
  }

  @Override
  public void cancel()
  {
    key.cancel();
  }

  @Override
  public int interestOps()
  {
    return key.interestOps();
  }

  @Override
  public SelectionKey interestOps(int ops)
  {
    if ((ops & this.ops) == 0) {
      key.interestOps(ops);
    } else {
      throw new IllegalArgumentException();
    }
    return this;
  }

  @Override
  public int readyOps()
  {
    return key.readyOps();
  }
}
