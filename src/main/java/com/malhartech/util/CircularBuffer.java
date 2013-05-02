/*
 *  Copyright (c) 2012 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.malhartech.util;

/**
 *
 * @author Chetan Narsude <chetan@malhar-inc.com>
 */
import static java.lang.Thread.sleep;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a premium implementation of circular buffer<p>
 * <br>
 *
 * @param <T> type of the objects in this buffer.
 */
public class CircularBuffer<T> implements UnsafeBlockingQueue<T>
{
  private final T[] buffer;
  private final int buffermask;
  private final int spinMillis;
  protected volatile long tail;
  protected volatile long head;

  /**
   *
   * Constructing a circular buffer of 'n' integers<p>
   * <br>
   *
   * @param n size of the buffer to be constructed
   * @param spin time in milliseconds for which to wait before checking for expected value if it's missing
   * <br>
   */
  @SuppressWarnings("unchecked")
  public CircularBuffer(int n, int spin)
  {
    int i = 1;
    while (i < n) {
      i <<= 1;
    }

    buffer = (T[])new Object[i];
    buffermask = i - 1;

    spinMillis = spin;
  }

  private CircularBuffer(T[] buffer, int buffermask, int spinMillis)
  {
    this.buffer = buffer;
    this.buffermask = buffermask;
    this.spinMillis = spinMillis;
  }

  /**
   *
   * Constructing a circular buffer of 'n' integers<p>
   * <br>
   *
   * @param n size of the buffer to be constructed
   * <br>
   */
  public CircularBuffer(int n)
  {
    this(n, 10);
  }

  @Override
  public boolean add(T e)
  {
    if (head - tail <= buffermask) {
      buffer[(int)(head & buffermask)] = e;
      head++;
      return true;
    }

    throw new IllegalStateException("Collection is full");
  }

  @Override
  public T remove()
  {
    if (head > tail) {
      T t = buffer[(int)(tail & buffermask)];
      tail++;
      return t;
    }

    throw new IllegalStateException("Collection is empty");
  }

  @Override
  public T peek()
  {
    if (head > tail) {
      return buffer[(int)(tail & buffermask)];
    }

    return null;
  }

  @Override
  public int size()
  {
    return (int)(head - tail);
  }

  /**
   *
   * Total design capacity of the buffer<p>
   * <br>
   *
   * @return Total return capacity of the buffer
   * <br>
   */
  public int capacity()
  {
    return buffermask + 1;
  }

  @Override
  public int drainTo(Collection<? super T> container)
  {
    int size = size();

    while (head > tail) {
      container.add(buffer[(int)(tail & buffermask)]);
      tail++;
    }

    return size;
  }

  @Override
  public String toString()
  {
    return "head=" + head + ", tail=" + tail + ", capacity=" + (buffermask + 1);
  }

  @Override
  public boolean offer(T e)
  {
    if (head - tail <= buffermask) {
      buffer[(int)(head & buffermask)] = e;
      head++;
      return true;
    }

    return false;
  }

  @Override
  @SuppressWarnings("SleepWhileInLoop")
  public void put(T e) throws InterruptedException
  {
    do {
      if (head - tail < buffermask) {
        buffer[(int)(head & buffermask)] = e;
        head++;
        return;
      }

      Thread.sleep(spinMillis);
    }
    while (true);
  }

  @Override
  @SuppressWarnings("SleepWhileInLoop")
  public boolean offer(T e, long timeout, TimeUnit unit) throws InterruptedException
  {
    long millis = unit.toMillis(timeout);
    do {
      if (head - tail < buffermask) {
        buffer[(int)(head & buffermask)] = e;
        head++;
        return true;
      }

      Thread.sleep(spinMillis);
    }
    while ((millis -= spinMillis) >= 0);

    return false;
  }

  @Override
  @SuppressWarnings("SleepWhileInLoop")
  public T take() throws InterruptedException
  {
    do {
      if (head > tail) {
        T t = buffer[(int)(tail & buffermask)];
        tail++;
        return t;
      }

      Thread.sleep(spinMillis);
    }
    while (true);
  }

  @Override
  @SuppressWarnings("SleepWhileInLoop")
  public T poll(long timeout, TimeUnit unit) throws InterruptedException
  {
    long millis = unit.toMillis(timeout);
    do {
      if (head > tail) {
        T t = buffer[(int)(tail & buffermask)];
        tail++;
        return t;
      }

      Thread.sleep(spinMillis);
    }
    while ((millis -= spinMillis) >= 0);

    return null;
  }

  @Override
  public int remainingCapacity()
  {
    return buffermask + 1 - (int)(head - tail);
  }

  @Override
  public boolean remove(Object o)
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean contains(Object o)
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public int drainTo(final Collection<? super T> collection, final int maxElements)
  {
    int i = -1;
    while (i++ < maxElements && head > tail) {
      collection.add(buffer[(int)(tail & buffermask)]);
      tail++;
    }

    return i;
  }

  @Override
  public T poll()
  {
    if (head > tail) {
      T t = buffer[(int)(tail & buffermask)];
      tail++;
      return t;
    }

    return null;
  }

  @Override
  public T pollUnsafe()
  {
    T t = buffer[(int)(tail & buffermask)];
    tail++;
    return t;
  }

  @Override
  public T element()
  {
    if (head > tail) {
      return buffer[(int)(tail & buffermask)];
    }

    throw new IllegalStateException("Collection is empty");
  }

  @Override
  public boolean isEmpty()
  {
    return head == tail;
  }

  public Iterator<T> getFrozenIterator()
  {
    return new Iterator<T>()
    {
      private final long head = CircularBuffer.this.head;
      private long tail = CircularBuffer.this.tail;

      @Override
      public boolean hasNext()
      {
        return tail < head;
      }

      @Override
      public T next()
      {
        return buffer[(int)(tail++ & buffermask)];
      }

      @Override
      public void remove()
      {
        buffer[(int)((tail - 1) & buffermask)] = null;
      }

    };
  }

  @Override
  public Iterator<T> iterator()
  {
    return new Iterator<T>()
    {
      @Override
      public boolean hasNext()
      {
        return head > tail;
      }

      @Override
      public T next()
      {
        T t = buffer[(int)(tail & buffermask)];
        tail++;
        return t;
      }

      @Override
      public void remove()
      {
      }

    };
  }

  @Override
  public Object[] toArray()
  {
    final int count = (int)(head - tail);
    Object[] array = new Object[count];
    for (int i = 0; i < count; i++) {
      array[i] = buffer[(int)(tail & buffermask)];
      tail++;
    }

    return array;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T[] toArray(T[] a)
  {
    int count = (int)(head - tail);
    if (a.length < count) {
      a = (T[])new Object[count];
    }

    for (int i = 0; i < count; i++) {
      a[i] = (T)buffer[(int)(tail & buffermask)];
      tail++;
    }

    return a;
  }

  @Override
  public boolean containsAll(Collection<?> c)
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean addAll(Collection<? extends T> c)
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean removeAll(Collection<?> c)
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean retainAll(Collection<?> c)
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void clear()
  {
    head = 0;
    tail = 0;
    Arrays.fill(buffer, null);
  }

  @Override
  public T peekUnsafe()
  {
    return buffer[(int)(tail & buffermask)];
  }

  public CircularBuffer<T> getWhitehole(final String exceptionMessage)
  {
    CircularBuffer<T> cb = new CircularBuffer<T>(buffer, buffermask, spinMillis)
    {
      @Override
      public boolean add(T e)
      {
        throw new IllegalStateException(exceptionMessage);
      }

      @Override
      @SuppressWarnings("SleepWhileInLoop")
      public void put(T e) throws InterruptedException
      {
        while (true) {
          sleep(spinMillis);
        }
      }

      @Override
      public boolean offer(T e)
      {
        return false;
      }

      @Override
      public boolean offer(T e, long timeout, TimeUnit unit) throws InterruptedException
      {
        long millis = unit.toMillis(timeout);
        sleep(millis);
        return false;
      }

      @Override
      public int remainingCapacity()
      {
        return 0;
      }

      @Override
      public boolean addAll(Collection<? extends T> c)
      {
        throw new IllegalStateException(exceptionMessage);
      }

    };
    cb.head = head;
    cb.tail = tail;

    return cb;
  }

  private static final Logger logger = LoggerFactory.getLogger(CircularBuffer.class);
}
