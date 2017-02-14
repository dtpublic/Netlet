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
package com.datatorrent.netlet.util;

import java.util.ArrayList;
import java.util.List;

import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.datatorrent.netlet.util.CircularBuffer.MAX_SIZE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CircularBufferTest
{
  private static final Logger logger = LoggerFactory.getLogger(CircularBufferTest.class);
  private static final long waitMillis = 500;

  public CircularBufferTest()
  {
  }

  @BeforeClass
  public static void setUpClass() throws Exception
  {
  }

  @AfterClass
  public static void tearDownClass() throws Exception
  {
  }

  @Before
  public void setUp()
  {
  }

  @After
  public void tearDown()
  {
  }

  /**
   * Test larger than allowed size of buffer; we expect an exception
   */
  @Test(expected = IllegalArgumentException.class)
  public void testLargeSize()
  {
    CircularBuffer<Integer> instance = new CircularBuffer<Integer>(1 + MAX_SIZE);
  }

  // helper routine to test that argument works and yields the same size
  // as the old method
  //
  private void testSize(final int size)
  {
    final CircularBuffer<Integer> instance = new CircularBuffer<Integer>(size);

    // get size the old way and check that it matches
    int i = 1;
    while (i < size) {
      i <<= 1;
    }
    assertEquals(i, instance.capacity());

    // check that the instance is actually functional
    instance.add(99);
    final int v = instance.poll();
    assertEquals(99, v);
    assertNull(instance.peek());
  }

 /**
  * Test values of the form 2**k, (2**k - 1) and (2**k + 1) as the size of the buffer
  */
  @Test
  public void testSize()
  {
    testSize(0);
    testSize(1);
    testSize(2);

    final int max_size = MAX_SIZE >> 3;      // MAX_SIZE runs out of memory
    for (int size = 4; size < max_size; size <<= 1) {
      testSize(size - 1);
      testSize(size);
      testSize(size + 1);
    }
  }

  /**
   * Test of add method, of class CircularBuffer.
   */
  @Test
  public void testAdd()
  {
    String oldName = Thread.currentThread().getName();
    Thread.currentThread().setName("TestAdd");

    CircularBuffer<Integer> instance = new CircularBuffer<Integer>(0);
    Assert.assertEquals("capacity", instance.capacity(), 1);

    for (int i = 0; i < instance.capacity(); i++) {
      instance.add(i);
    }

    try {
      instance.add(new Integer(0));
      Assert.fail("exception should be raised for adding to buffer which does not have room");
    }
    catch (Exception bue) {
      assert (bue instanceof IllegalStateException);
    }

    instance = new CircularBuffer<Integer>(10);
    for (int i = 0; i < 10; i++) {
      instance.add(i);
    }
    assert (instance.size() == 10);

    for (int i = 10; i < instance.capacity(); i++) {
      instance.add(i);
    }

    try {
      instance.add(new Integer(0));
      Assert.fail("exception should have been thrown");
    }
    catch (Exception e) {
      assert (e instanceof IllegalStateException);
      instance.remove();
      instance.add(new Integer(0));
    }

    assert (instance.size() == instance.capacity());
    Thread.currentThread().setName(oldName);
  }

  /**
   * Test of remove method, of class CircularBuffer.
   */
  @Test
  public void testGet()
  {
    String oldName = Thread.currentThread().getName();
    Thread.currentThread().setName("TestGet");

    CircularBuffer<Integer> instance = new CircularBuffer<Integer>(0);
    try {
      instance.remove();
      Assert.fail("exception should be raised for getting from buffer which does not have data");
    }
    catch (Exception bue) {
      assert (bue instanceof IllegalStateException);
      assert (bue.getMessage().equals("Collection is empty"));
    }

    instance = new CircularBuffer<Integer>(10);
    try {
      instance.remove();
      Assert.fail("exception should be raised for getting from buffer which does not have data");
    }
    catch (Exception bue) {
      assert (bue instanceof IllegalStateException);
      assert (bue.getMessage().equals("Collection is empty"));
    }

    for (int i = 0; i < 10; i++) {
      instance.add(i);
    }

    Integer i = instance.remove();
    Integer j = instance.remove();
    assert (i == 0 && j == 1);

    instance.add(10);

    assert (instance.size() == 9);
    assert (instance.remove() == 2);
    Thread.currentThread().setName(oldName);
  }

  @Test
  public void testDrainToMax() {
    CircularBuffer<Integer> instance = new CircularBuffer<Integer>(10);
    for (int i = 0; i < 10; ++i) {
      instance.offer(i);
    }
    List<Integer> list = new ArrayList<Integer>(10);
    instance.drainTo(list, 5);
    assert (list.size() == 5);
    for (int i = 0; i < list.size(); ++i) {
      assert(list.get(i) == i);
    }
  }

  @Test
  public void testPerformanceOfCircularBuffer() throws InterruptedException
  {
    testPerformanceOf(new CircularBuffer<Long>(1024 * 1024), 100);
    testPerformanceOf(new CircularBuffer<Long>(1024 * 1024), waitMillis);
  }

  @Test
  public void testPerformanceOfSynchronizedCircularBuffer() throws InterruptedException
  {
    testPerformanceOf(new SynchronizedCircularBuffer<Long>(1024 * 1024), 100);
    testPerformanceOf(new SynchronizedCircularBuffer<Long>(1024 * 1024), waitMillis);
  }

  private <T extends UnsafeBlockingQueue<Long>> void testPerformanceOf(final T buffer, long millis) throws InterruptedException
  {
    Thread producer = new Thread("Producer")
    {
      @Override
      @SuppressWarnings("SleepWhileInLoop")
      public void run()
      {
        long l = 0;
        try {
          do {
            int i = 0;
            while (i++ < 1024 && buffer.offer(l++)) {
            }
            if (i != 1025) {
              l--;
              Thread.sleep(10);
            }
          }
          while (!interrupted());
        }
        catch (InterruptedException ex1) {
        }
      }
    };

    Thread consumer = new Thread("Consumer")
    {
      @Override
      @SuppressWarnings("SleepWhileInLoop")
      public void run()
      {
        long l = 0;
        try {
          int size;
          do {
            if ((size = buffer.size()) == 0) {
              sleep(10);
            }
            else {
              while (size-- > 0) {
                Assert.assertEquals(l++, buffer.pollUnsafe().longValue());
              }
            }
          }
          while (!interrupted());
        }
        catch (InterruptedException ex1) {
        }
      }
    };

    producer.start();
    consumer.start();

    Thread.sleep(millis);

    producer.interrupt();
    consumer.interrupt();

    producer.join();
    consumer.join();

    logger.debug(buffer.getClass().getSimpleName() + "(" + buffer.toString() + ")");
  }
}
