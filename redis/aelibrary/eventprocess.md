redis处理命令的一般流程
================

**先上一张图**

<center>![](../../imgs/command_process.png)</center>
<center>redis处理命令的一般流程</center>

**网络部分的处理**

一个请求到达redis server后，redis首先会接收这个连接(在networking.c/acceptTcpHandler中处理)，然后生成一个redisClient对象,并向aeEventLoop中添加一个读事件。大致的代码如下:
```c
//接收链接
static void acceptCommonHandler(int fd, int flags) {
    redisClient *c;
    // 创建新客户端
    createClient(fd);
    // 如果超过最大打开客户端数量，那么关闭这个客户端
    if (listLength(server.clients) > server.maxclients) {
        if (write(c->fd,err,strlen(err)) == -1) {
        }
        server.stat_rejected_conn++;
        // 释放客户端
        freeClient(c);
        return;
    }
    server.stat_numconnections++;
    c->flags |= flags;
}
```
```c
// 创建新客户端的处理
lient *createClient(int fd) {
    redisClient *c = zmalloc(sizeof(redisClient));
    // 因为 Redis 命令总在客户端的上下文中执行，
    // 有时候为了在服务器内部执行命令，需要使用伪客户端来执行命令
    // 在 fd == -1 时，创建的客户端为伪终端
    if (fd != -1) {
        anetNonBlock(NULL,fd);
        anetTcpNoDelay(NULL,fd);
        // 接收客户端数据为readQueryFromClient
        if (aeCreateFileEvent(server.el,fd,AE_READABLE, readQueryFromClient, c) == AE_ERR)
        {
            close(fd);
            zfree(c);
            return NULL;
        }
    }
    // ----------------------省略三百行-----------------------------
}
```
```c
// 接收客户端数据
void readQueryFromClient(aeEventLoop *el, int fd, void *privdata, int mask) {
    redisClient *c = (redisClient*) privdata;
    // ----------------------省略三百行-----------------------------
    // 读入到 buf
    nread = read(fd, c->querybuf+qblen, readlen);
    // 读入缓存不能超过限制，否则断开并清除客户端
    if (sdslen(c->querybuf) > server.client_max_querybuf_len) {
        sds ci = getClientInfoString(c), bytes = sdsempty();

        bytes = sdscatrepr(bytes,c->querybuf,64);
        redisLog(REDIS_WARNING,"Closing client that reached max query buffer length: %s (qbuf initial bytes: %s)", ci, bytes);
        sdsfree(ci);
        sdsfree(bytes);
        freeClient(c);
        return;
    }
    // 执行命令
    processInputBuffer(c);
    server.current_client = NULL;
}
```
```c
// 判断命令是否接收完成
void processInputBuffer(redisClient *c) {
    while(sdslen(c->querybuf)) {
        /* Immediately abort if the client is in the middle of something. */
        if (c->flags & REDIS_BLOCKED) return;
        if (c->flags & REDIS_CLOSE_AFTER_REPLY) return;
        /* Determine request type when unknown. */
        if (!c->reqtype) {
            // querybuf[0]='*'为redis新的协议
            if (c->querybuf[0] == '*') {
                c->reqtype = REDIS_REQ_MULTIBULK;
            } else {
                c->reqtype = REDIS_REQ_INLINE;
            }
        }
        // 判断协议数据是否接收完整
        if (c->reqtype == REDIS_REQ_INLINE) {
            if (processInlineBuffer(c) != REDIS_OK) break;
        } else if (c->reqtype == REDIS_REQ_MULTIBULK) {
            if (processMultibulkBuffer(c) != REDIS_OK) break;
        } else {
            redisPanic("Unknown request type");
        }

        /* Multibulk processing could see a <= 0 length. */
        if (c->argc == 0) {
            resetClient(c);
        } else {
            // 数据接收完整执行具体的命令
            if (processCommand(c) == REDIS_OK)
                resetClient(c);
        }
    }
}
```

**具体命令的处理**

具体命令处理是从processCommand函数开始执行的

