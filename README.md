#Netlet#

Netlet is a java NIO abstraction for TCP Client-Server applications. It simplifies the application code to something
like:

```
EventLoop el = OptimizedEventLoop.createEventLoop("MyLoop");
el.start(); /* start the event based driver */

ServerImpl si = new AbstractServer() { /* business logic here ... */ };
el.start(hostname, port, si); /* start the server */

ClientImpl ci = new ClientImpl() { /* business logic here ... */ };
el.connect(hostname, port, ci); /* connect to the server */

/* let the dragons be here */

el.disconnect(ci); /* disconnect the client */
el.stop(si); /* stop listening */
el.stop(); /* stop the driver */
```

netlet is available in the Maven Central Repository:

```
  <dependency>
    <groupId>com.datatorrent</groupId>
    <artifactId>netlet</artifactId>
    <version>1.2.1</version>
  </dependency>
```

##Motivation##

In late 2012, after writing the first version of Apex network buffer server using Netty-4, I realized that the network
throughput had touched ceiling while it was using just 25% of the NIC capacity. More attempts to tune the peformance
just resulted in hitting various pitfalls of using Netty and resulting in setbacks. The Netty development team was more
that helpful in resolving the issues I faced while using Netty. Yet the more I worked with Netty, I realized that the
strength of Netty comes from a stellar support for application level protocols but unfortunately it comes with complexity
and lack of efficiency which is unavoidable for having such a robust support.

Buffer Server needs were however very different. It needed something that abstracted the java's NIO interface to even
higher yet more efficient level. I needed a networking java library which was similar to something that I had scribbled
in C++ back in 2008. The closest I found was connectlet. It had its own stability issues but the biggest was that it
was LGPL. Since we wanted to release Apex under Apache, it was a no go.

Hence I decided to write my own NIO Abstaction and later released it as Netlet. With netlet, I was able to refactor the
code that I wrote for Netty and was able to push the performance to more than 500% compared to that with Netty.

##Features##

* Very fast - does away with many inefficiencies of typical networking libraries.
* Better abstraction of NIO compared to a few other well known libraries, results in fewer lines of application code
* Abstraction of difference between the client and the server code, results in reuse of the client code on serverside
* Debugging friendly - fewer classes, fewer threads, smaller footprint
* Explicit thread management - does away with need for unnecessary synchronization

##Drawbacks##

* Support only TCP/IP - UDP/Multicast planned.
* Limited in number of application protocols supported - Provides only LengthPrepended protocol out of the box.
* Explicit thread management - see above.

##Examples##

Various tests are included in the test directory. There are 2 types of tests. Unit Tests and Performance Tests.

###Unit Tests###

Unit Tests provide a sample client/server code and test specific feature per unit test. Those can be executed as
```
mvn test
```

###Performance Tests###

Performance tests were written to compare the performance of Netlet to that of Netty. 

Netlet server and Netlet client can be run by executing the following commands in sequence.
```
mvn exec:exec -Dbenchmark=netlet.server
mvn exec:exec -Dbenchmark=netlet.client
```

Similarly if you want to benchmark the Netty Client server
```
mvn exec:exec -Dbenchmark=netty.server
mvn exec:exec -Dbenchmark=netty.client
```

And you can mix and match server and client programs 
```
mvn exec:exec -Dbenchmark=netlet.server
mvn exec:exec -Dbenchmark=netty.client
```

##Downloads##

You can download the latest version from the [release page](https://github.com/DataTorrent/Netlet/releases) or directly from the [maven central repository](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22netlet%22).

##Development##

* ![Build Status](https://travis-ci.org/DataTorrent/Netlet.svg?branch=master)

###Reports###

Use the maven site plugin (`mvn site`) to generate the following reports:
 * findbugs
 * checkstyle
 * japicmp
 * cobertura test coverage

###Release###

This is the release procedure:
* Increment version in pom.xml
* Run release build (substitute passphrase with your GPG password):
```
mvn clean deploy -Prelease -Dgpg.passphrase=passphrase
```
* Login to [Sonatype's Nexus repository](https://oss.sonatype.org/)
  * Download released artifact from staging repository.
  * Close and release staging repository if sanity checks are successful.

##Contributions

Pull requests are welcome, but please follow these rules:

* I have not hooked up the coding convention enforcer plugin, so please respect the convention of the file which you edit.
* Provide a unit test for every change.
* Name classes/methods/fields expressively.
* Fork the repo and create a pull request (see [GitHub Flow](https://guides.github.com/introduction/flow/index.html)).

##Related work##

The following projects have related goals:

* [Netty](http://netty.io/): Netty is an asynchronous event-driven network application framework for rapid development of maintainable high performance protocol servers & clients.
* [tuna](https://github.com/xqbase/tuna): a Lightweight and High Performance Java Network Framework. Formerly Connectlet, while renaming it has also been released under Apache 2.0!
* [Grizzly](https://grizzly.java.net/): The Grizzly NIO framework has been designed to help developers to take advantage of the Java™ NIO API. Grizzly’s goal is to help developers to build scalable and robust servers using NIO as well as offering extended framework components.
