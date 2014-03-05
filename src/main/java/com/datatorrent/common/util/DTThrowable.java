/*
 *  Copyright (c) 2012-2014 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.datatorrent.common.util;

/**
 * Helper method to ensure that exceptions are propertly thrown.
 * If the cause is of type Error or RuntimeException then the
 * cause it thrown as it is. Otherwise the cause is wrapped in
 * a RuntimeException and the later is thrown.
 *
 * @since 0.9.3
 */
public class DTThrowable
{
  public static void rethrow(Throwable cause)
  {
    if (cause instanceof Error) {
      throw (Error)cause;
    }

    if (cause instanceof RuntimeException) {
      throw (RuntimeException)cause;
    }

    throw new RuntimeException(cause);
  }

  public static void rethrow(Exception exception)
  {
    if (exception instanceof RuntimeException) {
      throw (RuntimeException)exception;
    }

    throw new RuntimeException(exception);
  }

  /**
   *
   * @param error
   * @deprecated Instead "DTThrowable.rethrow(error);" use "throw error;" directly.
   */
  @Deprecated
  public static void rethrow(Error error)
  {
    throw error;
  }

  /**
   *
   * @param exception
   * @deprecated Instead "DTThrowable.rethrow(runtime_exception);" use "throw runtime_exception;" directly.
   */
  @Deprecated
  public static void rethrow(RuntimeException exception)
  {
    throw exception;
  }

}
