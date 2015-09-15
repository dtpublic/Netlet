# Change Log

## [1.2.0](https://github.com/DataTorrent/Netlet/tree/v1.2.0) (2015-09-15)
[Full Changelog](https://github.com/DataTorrent/Netlet/compare/v1.1.0...v1.2.0)

**Enhancements**

- Changed policy for handling tasks; They are executed before IO operations in the event loop.
- Introduced createEventLoop factory method for creating the concrete EventLoop class.
- Added Iterable and Cloneable interfaces to the CircularBuffer.FrozenIterator.
- Deprecated suspendRead and resumeRead; Replaced them with suspendReadIfResumed and resumeReadIfSuspended.
- Instrumented the NIO's select call to remove the garbage creation totally with OptimizedEventLoop.

**Integration**

- Tailored Coral Reactor benchmarks for Netty and Netlet and added maven plugins for executing those.
- Added japicmp plugin for checking semantic versioning.
- Added README, CHANGELOG, and travis-ci.org build automation.

## [1.1.0](https://github.com/DataTorrent/Netlet/tree/v1.1.0) (2015-07-30)
[Full Changelog](https://github.com/DataTorrent/Netlet/compare/v1.0.0...v1.1.0)

**Enhancements**

- Introduced OptimizedEventLoop which uses mix of select and selectNow to optimize latency.
- Flipped the order of read/write. Now read is invoked before write so that remote disconnections are handled gracefully.
- AbstractClient.discardReadBuffer introduced to encapsulate the logic to discard data read so far. Useful when replacing the business logic to process the data.
- Added OP_READ to interest ops along with OP_CONNECT to address apparent bug in Java NIO of missing OP_CONNECT.

## [1.0.0](https://github.com/DataTorrent/Netlet/tree/v1.0.0) (2015-06-30)

- The very first publicly available version of Netlet!
