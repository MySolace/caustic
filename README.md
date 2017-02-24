# Schema
Distributed, transactional cache.

## Introduction
Suppose that you have some variable ```x = 0``` that is persisted in some shared storage and used by two machines. Each machine would like to read the value of ```x```, increment it, and then write the new value of ```x``` back to storage. For example, suppose that the first machine reads the value of ```x``` to be ```0```, it then increments it to ```1```, and then writes ```x = 1``` back to storage. However, if the second machine reads the value of ```x``` before the first machine finishes writing the new value, it will also read the value of ```x``` to be ```0```, perform an increment, and attempt to write ```x = 1``` back to storage. Therefore, two increments have been performed but only one increment was recorded.

This is a classic example of a race condition, and arises whenever the order of execution affects the outcome of a concurrent operation. One technique for preventing race conditions is [compare-and-swap](https://en.wikipedia.org/wiki/Compare-and-swap). According to Wikipedia, compare-and-swap "compares the contents of a memory location to a given value and, only if they are the same, modifies the contents of that memory location to a given new value." In our previous example, when the first and second machine write the new value of ```x``` back to storage they first check that the current value of ```x``` is what they think it should be. If it is not, then the write fails. Clearly, compare-and-swap eliminates the race condition because one of the writes in the previous example must fail.

The goal of this project is to perform transactional modifications to a distributed data model. We'll show that we can perform transactional modifications to any ```Map[String, Any]``` using a variation compare-and-swap, loosely based on [Tango](http://www.cs.cornell.edu/~taozou/sosp13/tangososp.pdf). We'll also show that we can represent any object graph as a ```Map[String, Any]``` while still ensuring static-type safety using [Shapeless](https://github.com/milessabin/shapeless). Furthermore, we'll show that we can independently store the fields of objects in this ```Map[String, Any]``` which guarantees field-level concurrency; in other words, different fields of the same object may be concurrently modified.