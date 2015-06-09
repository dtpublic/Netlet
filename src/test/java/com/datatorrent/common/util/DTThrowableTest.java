/*
 *  Copyright (c) 2014 DataTorrent, Inc. ALL Rights Reserved.
 */
package com.datatorrent.common.util;

import org.junit.Test;

import static com.datatorrent.common.util.DTThrowable.rethrow;

/**
 *
 * @author Chetan Narsude <chetan@datatorrent.com>
 */
public class DTThrowableTest
{
  @Test
  public void testRethrow_Throwable()
  {
    try {
    }
    catch (Throwable th) {
      rethrow(th);
    }
  }

  @Test
  public void testRethrow_Exception()
  {
    try {
    }
    catch (Exception th) {
      rethrow(th);
    }
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testRethrow_Error()
  {
    try {
    }
    catch (Error th) {
      rethrow(th);
    }
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testRethrow_RuntimeException()
  {
    try {
    }
    catch (RuntimeException th) {
      rethrow(th);
    }
  }

}