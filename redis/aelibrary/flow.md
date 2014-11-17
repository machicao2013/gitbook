redis事件库的初始化流程
============

**先上一张图**

<center>![](../../imgs/init_flow.png)</center>
<center>redis事件库的初始化</center>

在redisServer中有一个成员`aeEventLoop *el`，该变量记录了事件状态。

在main函数中，与事件库相关的调用如下：
```c
int main(int argc, char **argv) {
    // ----------------------省略三百行-----------------------------
    // 初始化服务器功能
    initServer();
    // ----------------------省略三百行-----------------------------
    // 设置事件执行前要运行的函数
    aeSetBeforeSleepProc(server.el,beforeSleep);
    // 启动服务器循环
    aeMain(server.el);
    // 关闭服务器，删除事件
    aeDeleteEventLoop(server.el);
    return 0;
}
```

**初始化aeEventLoop**

初始化aeEventLoop在initServer()函数中，主要是生成一个aeEventLoop对象.
```c
void initServer() {
    // ----------------------省略三百行-----------------------------
    // 初始化事件状态
    server.el = aeCreateEventLoop(server.maxclients+1024);
    // ----------------------省略三百行-----------------------------
    // 初始化网络连接
    if (server.port != 0) {
        server.ipfd = anetTcpServer(server.neterr,server.port,server.bindaddr);
        if (server.ipfd == ANET_ERR) {
            redisLog(REDIS_WARNING, "Opening port %d: %s",
                server.port, server.neterr);
            exit(1);
        }
    }
    // ----------------------省略三百行-----------------------------
    // 关联 server cron 到时间事件
    aeCreateTimeEvent(server.el, 1, serverCron, NULL, NULL);
    // 关联网络连接事件
    if (server.ipfd > 0 && aeCreateFileEvent(server.el,server.ipfd,AE_READABLE,
            acceptTcpHandler,NULL) == AE_ERR) redisPanic("Unrecoverable error creating server.ipfd file event.");
    // ----------------------省略三百行-----------------------------
}
```

**时间事件的处理**

aeSetBeforeSleepProc会注册一个回调函数，在每次执行事件循环前执行该回调。该函数在redis中是serverCron，会进行超时检测，rdb和aof的处理等。

**事件循环**

事件循环函数是在aeMain中执行的，该函数定义如下:
```c
void aeMain(aeEventLoop *eventLoop) {
    eventLoop->stop = 0;
    while (!eventLoop->stop) {
        // 如果有需要在事件处理前执行的函数，那么运行它
        if (eventLoop->beforesleep != NULL)
            eventLoop->beforesleep(eventLoop);
        // 开始处理事件
        aeProcessEvents(eventLoop, AE_ALL_EVENTS);
    }
}
```

在aeProcessEvents函数中，会从底层的epoll/select/**中获取已经有事件的fd，然后执行该fd对应事件的回调函数.
