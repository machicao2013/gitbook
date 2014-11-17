reactor模式简介
========

**reactor模式**

reactor是在事件处理模型中常用的一种设计模式。主要的角色以及它们之间的关系如下图所示：

<center>![](../../imgs/reactor.jpg)</center>

从上图可以看出，reactor主要有四种角色：

1. Reactor是reactor模式中最为重要的角色，它是该模式向用户提供接口的类。用户可以向reactor中注册EventHandler，当Reactor发现用户注册的fd上有事件发生时，会回调用户注册的事件处理函数。
2. SynchrousEventDemultiplexer用来检测用户注册的fd上是否有事件发生，然后通知reactor有什么事件发生(可读或者可写)。
3. EventHandler是用户和Reactor打交道的工具，用户通过Reactor注册自己的EventHandler，可以告诉reactor在特定的事件发生时帮助用户做什么。
4. ConcreteEventHandler是EventHandler的子类。

**reactor模式与redis事件库的对应关系**
1. redis ae库的Reactor就是aeEventLoop结构体和相关的函数(因为redis是C语言写的)。具体的代码如下：
```c
struct aeEventLoop {
    // 目前已注册的最大描述符
    int maxfd;   /* highest file descriptor currently registered */
    // 目前已追踪的最大描述符
    int setsize; /* max number of file descriptors tracked */
    // 用于生成时间事件 id
    long long timeEventNextId;
    // 最后一次执行时间事件的时间
    time_t lastTime;     /* Used to detect system clock skew */
    // 已注册的文件事件
    aeFileEvent *events; /* Registered events */
    // 已就绪的文件事件
    aeFiredEvent *fired; /* Fired events */
    // 时间事件
    aeTimeEvent *timeEventHead;
    // 事件处理器的开关
    int stop;
    // 多路复用库的私有数据
    void *apidata; /* This is used for polling API specific data */
    // 在处理事件前要执行的函数
    aeBeforeSleepProc *beforesleep;
} aeEventLoop;
```
```c
// 相关的函数
int aeCreateFileEvent(aeEventLoop *eventLoop, int fd, int mask,
    aeFileProc *proc, void *clientData);
void aeDeleteFileEvent(aeEventLoop *eventLoop, int fd, int mask);
int aeGetFileEvents(aeEventLoop *eventLoop, int fd);
long long aeCreateTimeEvent(aeEventLoop *eventLoop, long long milliseconds,
    aeTimeProc *proc, void *clientData,
    aeEventFinalizerProc *finalizerProc);
int aeDeleteTimeEvent(aeEventLoop *eventLoop, long long id);
int aeProcessEvents(aeEventLoop *eventLoop, int flags);
```
2. SynchrousEventDemultiplexer在redis中的实现是通过epoll/select/poll实现，组织方式如下：
```c
#ifdef HAVE_EVPORT
    #include "ae_evport.c"
#else
    #ifdef HAVE_EPOLL
        #include "ae_epoll.c"
    #else
        #ifdef HAVE_KQUEUE
            #include "ae_kqueue.c"
        #else
            #include "ae_select.c"
        #endif
    #endif
#endif
```
3. EventHandler在redis中没有抽象。
4. ConcreteEventHandler在redis中对应两个，时间事件aeTimeEvent和文件事件aeFileEvent，具体的定义如下。
```c
struct aeTimeEvent {
    long long id; // time event identifier.
    long when_sec; // seconds
    long when_ms; // milliseconds
    aeTimeProc *timeProc;
    aeEventFinalizerProc *finalizerProc;
    void *clientData;
    struct aeTimeEvent *next;
} aeTimeEvent;
```
```c
struct aeFileEvent {
    int mask; // one of AE_(READABLE|WRITABLE)
    aeFileProc *rfileProc;
    aeFileProc *wfileProc;
    void *clientData;
} aeFileEvent;
```
