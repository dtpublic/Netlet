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
 * @since 1.1.0
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

    public void forEach(final DefaultEventLoop defaultEventLoop)
    {
      while (defaultEventLoop.alive && pos > 0) {
        final SelectionKey sk = keys[--pos];
      keys[pos] = null;
        if (!sk.isValid()) {
          continue;
        }
        try {
          defaultEventLoop.handleSelectedKey(sk);
        } catch (Exception ex) {
          Listener l = (Listener)sk.attachment();
          if (l != null) {
            l.handleException(ex, defaultEventLoop);
          } else {
            logger.warn("Exception on unattached SelectionKey {} ", sk, ex);
          }
        }
      }
    }

    @Override
    public boolean add(SelectionKey key)
    {
      if (key == null) {
        return false;
      }
      keys[pos++] = key;
      if (pos == keys.length) {
        SelectionKey[] lKeys = new SelectionKey[this.keys.length << 1];
        System.arraycopy(this.keys, 0, lKeys, 0, pos);
        this.keys = lKeys;
      }
      return true;
    }

    @Override
    public int size()
    {
      return pos;
    }

    @Override
    public boolean remove(Object o) {
      return false;
    }

    @Override
    public boolean contains(Object o) {
      if (o == null) {
        return false;
      }
      int i = pos;
      while (i > 0) {
        if (o.equals(keys[--i])) {
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

  @SuppressWarnings( {"deprecation", "UseSpecificCatch"})
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
  @Override
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
    while (alive) {
          int size = tasks.size();
      try {
        if (size > 0) {
          while (alive && size > 0) {
            Runnable task = tasks.pollUnsafe();
            try {
              task.run();
            }
            catch (Exception e) {
              logger.error("Unexpected exception in task {}", task);
              throw new RuntimeException(e);
            }
            size--;
          }
          if (selector.selectNow() == 0) {
            continue;
          }
        } else if (selector.select() == 0) {
              continue;
            }
      } catch (IOException e) {
        logger.error("Unexpected exception in selector {}", selector, e);
        throw new RuntimeException(e);
          }
      keys.forEach(this);
        }
    //logger.debug("Terminated {}", this);
  }

  private static final Logger logger = LoggerFactory.getLogger(OptimizedEventLoop.class);
}
