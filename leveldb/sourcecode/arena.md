leveldb的内存池
============

1. leveldb的内存池的实现在leveldb/util/arena.[h,cc]中。
2. arena的实现原理：
    - 申请到的内存使用std::vector<char *>管理，在arena的生命周期结束时全部释放。
    - 遇到大于2kb的内存，直接向系统申请，然后挂到vector的后面。
    - vector中的节点默认会存储一个指向4kb内存块的指针，申请小内存时会在当前块中申请，不足会直接向系统申请新的内存，然后挂载到vector上。如vector[0]挂载的4kb内存还剩余1kb，但是我现在要申请1.5kb的内存，则会向系统申请一个4kb的内存挂载到vector[1]上，如果接下来要申请2字节内存，则会在vector[1]上申请，vector[0]上的内存则会成为碎片。
3. arena只提供了两个接口
    - char* Allocate(size_t bytes);
    - char* AllocateAligned(size_t bytes);
4. Arena实现的是粗粒度的内存池，每个Block内都可能产生剩余部分内存不能用的问题，且不存在中间释放内存和提供内存复用机制，不适用于在全局使用，且容易造成系统内存碎片。
