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

}
