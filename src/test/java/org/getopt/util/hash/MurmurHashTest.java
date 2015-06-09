/*
 *  Copyright (c) 2012-2013 Malhar, Inc.
 *  All Rights Reserved.
 */
package org.getopt.util.hash;


import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chetan Narsude <chetan@datatorrent.com>
 */
public class MurmurHashTest
{
  static int NUM = 1000;


  @Test
  public void testHash()
  {
    byte[] bytes = new byte[4];
    for (int i = 0; i < NUM; i++) {
      bytes[0] = (byte)(i & 0xff);
      bytes[1] = (byte)((i & 0xff00) >> 8);
      bytes[2] = (byte)((i & 0xff0000) >> 16);
      bytes[3] = (byte)((i & 0xff000000) >> 24);
      logger.debug(Integer.toHexString(i) + " " + Integer.toHexString(MurmurHash.hash(bytes, 1)));
      // do some kind of test here!
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(MurmurHashTest.class);
}