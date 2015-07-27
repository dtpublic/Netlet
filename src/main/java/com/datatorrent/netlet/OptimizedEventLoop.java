/**
 * Copyright (C) 2015 DataTorrent, Inc.
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
import java.lang.reflect.Field;
import java.nio.channels.SelectionKey;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * OptimizedEventLoop class.</p>
 *
 * @since 1.0.1
 */
public class OptimizedEventLoop extends DefaultEventLoop
{
  private final static class SelectedSelectionKeySet extends AbstractSet<SelectionKey>
  {
    private SelectionKey[] keys;
    private int pos;

    private SelectedSelectionKeySet(int size)
    {
      pos = 0;
      keys = new SelectionKey[size];
    }

    private SelectionKey[] getKeys()
    {
      keys[pos] = null;
      pos = 0;
      return keys;
    }

    @Override
    public boolean add(SelectionKey key)
    {
      if (key == null) {
        return false;
      }
      if (pos >= keys.length) {
        SelectionKey[] keys = new SelectionKey[this.keys.length << 1];
        System.arraycopy(this.keys, 0, keys, 0, this.keys.length);
        this.keys = keys;
      }
      keys[pos++] = key;
      return true;
    }

    @Override
    public int size()
    {
      return pos;
    }

    public boolean remove(Object o) {
      return false;
    }

    @Override
    public boolean contains(Object o) {
      if (o == null) {
        return false;
      }
      for (int i = 0; i < pos; i++) {
        if (o.equals(keys[i])) {
          return true;
        }
      }
      return false;
    }

    @Override
    public Iterator<SelectionKey> iterator() {
      throw new UnsupportedOperationException();
    }
  }

  public OptimizedEventLoop(String id) throws IOException
  {
    super(id);
    try {
      ClassLoader systemClassLoader;
      if (System.getSecurityManager() == null) {
        systemClassLoader = ClassLoader.getSystemClassLoader();
      } else {
        systemClassLoader = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>()
        {
          @Override
          public ClassLoader run()
          {
            return ClassLoader.getSystemClassLoader();
          }
        });
      }

      final Class<?> selectorClass = Class.forName("sun.nio.ch.SelectorImpl", false, systemClassLoader);
      if (selectorClass.isAssignableFrom(selector.getClass())) {
        Field selectedKeys = selectorClass.getDeclaredField("selectedKeys");
        Field publicSelectedKeys = selectorClass.getDeclaredField("publicSelectedKeys");
        selectedKeys.setAccessible(true);
        publicSelectedKeys.setAccessible(true);
        SelectedSelectionKeySet keys = new SelectedSelectionKeySet(1024);
        selectedKeys.set(selector, keys);
        publicSelectedKeys.set(selector, keys);
        logger.debug("Instrumented an optimized java.util.Set into: {}", selector);
      }
    }
    catch (Exception e) {
      logger.debug("Failed to instrument an optimized java.util.Set into: {}", selector, e);
    }
  }

  @SuppressWarnings({"SleepWhileInLoop", "ConstantConditions"})
  protected void runEventLoop()
  {
    Set<SelectionKey> selectedKeys = selector.selectedKeys();
    if (selectedKeys instanceof SelectedSelectionKeySet) {
      runEventLoop((SelectedSelectionKeySet) selectedKeys);
    } else
      super.runEventLoop();
  }

  private void runEventLoop(SelectedSelectionKeySet keys)
  {
    do {
      SelectionKey sk = null;
      try {
        do {
          int size = tasks.size();
          while (alive && size > 0 && selector.selectNow() == 0) {
            tasks.pollUnsafe().run();
            size--;
          }
          if (alive && size == 0 && selector.select() == 0) {
            continue;
          }

          SelectionKey[] selectedKeys = keys.getKeys();
          for (int i = 0; alive; i++) {
            sk = selectedKeys[i];
            if (sk == null) {
              break;
            } else {
              selectedKeys[i] = null;
            }
            if (!sk.isValid()) {
              continue;
            }
            handleSelectedKey(sk);
          }
        }
        while (alive);
      }
      catch (Exception ex) {
        if (sk == null) {
          logger.warn("Unexpected exception not related to SelectionKey", ex);
        }
        else {
          logger.warn("Exception on unregistered SelectionKey {}", sk, ex);
          Listener l = (Listener)sk.attachment();
          if (l != null) {
            l.handleException(ex, this);
          }
        }
      }
    }
    while (alive);
    //logger.debug("Terminated {}", this);
  }

  private static final Logger logger = LoggerFactory.getLogger(OptimizedEventLoop.class);
}
