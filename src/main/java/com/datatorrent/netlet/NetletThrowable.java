/*
 *  Copyright (c) 2012-2014 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.datatorrent.netlet;

/**
 *
 * @author Chetan Narsude <chetan@datatorrent.com>
 */
public interface NetletThrowable
{
  EventLoop getEventLoop();

  Throwable getCause();

  class Util
  {
    public static NetletThrowable rewrap(Throwable th, EventLoop el)
    {
      if (th instanceof NetletThrowable) {
        NetletThrowable nth = (NetletThrowable)th;
        if (el == nth.getEventLoop()) {
          return nth;
        }

        return rewrap(nth.getCause(), el);
      }

      if (th instanceof Error) {
        return new NetletError((Error)th, el);
      }

      if (th instanceof RuntimeException) {
        return new NetletRuntimeException((RuntimeException)th, el);
      }

      return new NetletException((Exception)th, el);
    }

    public static void rethrow(NetletThrowable cause) throws NetletException
    {
      if (cause instanceof NetletError) {
        throw (NetletError)cause;
      }

      if (cause instanceof RuntimeException) {
        throw (NetletRuntimeException)cause;
      }

      throw (NetletException)cause;
    }

  }

  class NetletError extends Error implements NetletThrowable
  {
    public final transient EventLoop eventloop;

    public NetletError(Error error, EventLoop el)
    {
      super(error);
      eventloop = el;
    }

    @Override
    public EventLoop getEventLoop()
    {
      return eventloop;
    }

    private static final long serialVersionUID = 201401221632L;
  }

  class NetletException extends Exception implements NetletThrowable
  {
    public final transient EventLoop eventloop;

    public NetletException(Exception exception, EventLoop el)
    {
      super(exception);
      eventloop = el;
    }

    @Override
    public EventLoop getEventLoop()
    {
      return eventloop;
    }

    private static final long serialVersionUID = 201401221635L;
  }

  class NetletRuntimeException extends RuntimeException implements NetletThrowable
  {
    public final transient EventLoop eventloop;

    public NetletRuntimeException(RuntimeException exception, EventLoop el)
    {
      super(exception);
      eventloop = el;
    }

    @Override
    public EventLoop getEventLoop()
    {
      return eventloop;
    }

    private static final long serialVersionUID = 201401221638L;
  }

}
