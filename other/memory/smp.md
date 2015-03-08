SMP for Android reading note
====================

1. Most uni-processors, including x86 and ARM, are sequentially consistent. Most SMP systems, including x86 and ARM, are not.
2. x86 SMP provides processor consistency, which is slightly weaker than sequential.While the architecture guarantees that loads are not reordered with respect to other loads, and stores are not reordered with respect to other stores, it does not guarantee that a store followed by a load will be observed in the expected order.
3.  A write-through cache will initiate a write to memory immediately, while a write-back cache will wait until it runs out of space and has to evict some entries.
4. While the write-through cache has a policy of immediately forwarding the data to main memory, it only initiates the write. It does not have to wait for it to finish.
5. CPU caches don’t operate on individual bytes. Data is read or written as cache lines; for many ARM CPUs these are 32 bytes. If you read data from a location in main memory, you will also be reading some adjacent values. Writing data will cause the cache line to be read from memory and updated. As a result, you can cause a value to be loaded into cache as a side-effect of reading or writing something nearby, adding to the general aura of mystery.
6. Observability:
	- I have observed your write when I can read what you wrote
	- I have observed your read when I can no longer affect the value you read
7. ARM SMP provides weak memory consistency guarantees. It does not guarantee that loads or stores are ordered with respect to each other.
8. Memory barriers provide a way for your code to tell the CPU that memory access ordering matters. ARM/x86 uniprocessors offer sequential consistency, and thus have no need for them.
9. It is important to recognize that the only thing guaranteed by barrier instructions is ordering.
10. The key thing to remember about barriers is that they define ordering. Don’t think of them as a “flush” call that causes a series of actions to happen. Instead, think of them as a dividing line in time for operations on the current CPU core.
11. Atomic operations guarantee that an operation that requires a series of steps always behaves as if it were a single operation.
12. The most fundamental operations — loading and storing 32-bit values — are inherently atomic on ARM so long as the data is aligned on a 32-bit boundary.
13. The atomicity guarantee is lost if the data isn’t aligned. Misaligned data could straddle a cache line, so other cores could see the halves update independently.
14. Consequently, the ARMv7 documentation declares that it provides “single-copy atomicity” for all byte accesses, halfword accesses to halfword-aligned locations, and word accesses to word-aligned locations. Doubleword (64-bit) accesses are not atomic, unless the location is doubleword-aligned and special load/store instructions are used. This behavior is important to understand when multiple threads are performing unsynchronized updates to packed structures or arrays of primitive types.
15. There is no need for 32-bit “atomic read” or “atomic write” functions on ARM or x86. Where one is provided for completeness, it just does a trivial load or store.
16. The memory barrier is necessary to ensure that other threads observe the acquisition of the lock before they observe any loads or stores in the critical section.
17. we didn’t use a memory barrier, and atomic and non-atomic operations can be reordered.
18. In java, It should be mentioned that, while loads and stores of object references and most primitive types are atomic, long and double fields are not accessed atomically unless they are marked as volatile. Multi-threaded updates to non-volatile 64-bit fields are problematic even on uniprocessors.
19. In C/C++, use the pthread operations, like mutexes and semaphores. These include the proper memory barriers, providing correct and efficient behavior on all Android platform versions. Be sure to use them correctly, for example be wary of signaling a condition variable without holding the corresponding mutex.
20. [引用](http://developer.android.com/training/articles/smp.html)


