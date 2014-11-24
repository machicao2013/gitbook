replication同步原理
===============

## 同步的过程 ##

## master和slave同步交互的过程 ##
1. 无论是初次连接还是重新连接，当建立一个服务器时，从服务器都将向主服务器发送一个sync命令。
2. 主服务器在收到sync命令后，开始执行bgsave，并在保存操作执行期间，将所有新执行的写入命令都保存到一个缓冲区里面。
3. 当bgsave执行完毕后，主服务器将执行保存操作所得的.rdb文件发送给从服务器，从服务器接收到这个.rdb文件，并将文件中的数据载入内存中。
4. 之后主服务器会以Redis命令协议的格式，将写命令缓冲区中累积的所有内容都发送给从服务器。

## master和slave同步过程中，状态的变化 ##
1. 如果redis作为slave运行，则全局变量server.repl_state的状态有REDIS_REPL_NODE(不处于复制状态)，REDIS_REPL_CONNECT(需要和master建立连接)，REDIS_REPL_CONNECTING(正在建立连接),REDIS_REPL_CONNECTED(已和master建立连接)四种。在读入slaveof配置或者发布slaveof命令后，server.repl_state取值为REDIS_REPL_CONNECT，在connectWithMaster调用后，状态变为REDIS_REPL_CONNECTING状态，然后在syncWithMaster跟master执行第一次同步后，取值变为REDIS_REPL_CONNECTED。
2. 如果redis作为master运行，则对应某个客户端连接的变量slave.repl_state的状态有REDIS_REPL_WAIT_BGSAVE_START(等待bgsave运行),REDIS_REPL_WAIT_BGSAVE_END(bgsave已dump db,该bulk传输了),REDIS_REPL_SEND_BULK(正在传输bulk)，REDIS_REPL_ONLINE(已完成开始的bulk传输，以后只需要发送更新了)。对于slave客户端(发布sync命令)，一开始slave.repl_state都处于REDIS_REPL_WAIT_BGSAVE_START状态(后面详解syncCommand函数),然后在后台dump db后(backgroundSaveDoneHandler函数),处于REDIS_REPL_WAIT_BGSAVE_END状态，然后updateSlavesWaitingBgsave会将状态置为REDIS_REPL_SEND_BULK，并设置write事件的函数sendBulkToSlave,在sendBulkToSlave运行后，状态变为REDIS_REPL_ONLINE了，此后master会一直调用replicationFeedSlaves给处于REDIS_REPL_ONLINE状态的slave发送新命令。

## 源码分析 ##

### redis作为slave时的行为分析 ###

redis作为slave时，需要在配置文件中指定masterip,在loadServerConfig读取配置文件的时候，会将server.repl_state设置位REDIS_REPL_CONNECT状态。处于此状态的redis需要运行到serverCron后才能使用replicationCron()来和master进行初始化同步，replicationCron会调用syncWithMaster，syncWithMaster中会使用sync命令来建立主从关系，另外syncWithMaster中使用syncRead和syncWrite两个阻塞函数来接收和发送数据，因此，redis作为slave在最初建立主从关系时是阻塞的。

```c
// redis.c/serverCron函数中的部分
run_with_period(1000) replicationCron();
```
```c
// replication.c/replicationCron()
void replicationCron(void) {
    // ----------------------省略300行----------------------------
    /* Check if we should connect to a MASTER */
    if (server.repl_state == REDIS_REPL_CONNECT) {
        redisLog(REDIS_NOTICE,"Connecting to MASTER...");
        if (connectWithMaster() == REDIS_OK) {
            redisLog(REDIS_NOTICE,"MASTER <-> SLAVE sync started");
        }
    }
    // ----------------------省略300行----------------------------
}
```
```c
// replication.c/connectWithMaster()
int connectWithMaster(void) {
    int fd;

    fd = anetTcpNonBlockConnect(NULL,server.masterhost,server.masterport);
    if (fd == -1) {
        redisLog(REDIS_WARNING,"Unable to connect to MASTER: %s",
            strerror(errno));
        return REDIS_ERR;
    }

    // 会调用syncWithMaster
    if (aeCreateFileEvent(server.el,fd,AE_READABLE|AE_WRITABLE,syncWithMaster,NULL) ==
            AE_ERR)
    {
        close(fd);
        redisLog(REDIS_WARNING,"Can't create readable event for SYNC");
        return REDIS_ERR;
    }

    server.repl_transfer_lastio = server.unixtime;
    server.repl_transfer_s = fd;
    // 状态发生了变化
    server.repl_state = REDIS_REPL_CONNECTING;
    return REDIS_OK;
}
```
```c
// replication.c/syncWithMaster()
void syncWithMaster(aeEventLoop *el, int fd, void *privdata, int mask) {
    char tmpfile[256], *err;
    int dfd, maxtries = 5;
    int sockerr = 0;
    socklen_t errlen = sizeof(sockerr);
    REDIS_NOTUSED(el);
    REDIS_NOTUSED(privdata);
    REDIS_NOTUSED(mask);

    /* If this event fired after the user turned the instance into a master
     * with SLAVEOF NO ONE we must just return ASAP. */
    if (server.repl_state == REDIS_REPL_NONE) {
        close(fd);
        return;
    }

    /* Check for errors in the socket. */
    if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &sockerr, &errlen) == -1)
        sockerr = errno;
    if (sockerr) {
        aeDeleteFileEvent(server.el,fd,AE_READABLE|AE_WRITABLE);
        redisLog(REDIS_WARNING,"Error condition on socket for SYNC: %s",
            strerror(sockerr));
        goto error;
    }

    /* If we were connecting, it's time to send a non blocking PING, we want to
     * make sure the master is able to reply before going into the actual
     * replication process where we have long timeouts in the order of
     * seconds (in the meantime the slave would block). */
    if (server.repl_state == REDIS_REPL_CONNECTING) {
        redisLog(REDIS_NOTICE,"Non blocking connect for SYNC fired the event.");
        /* Delete the writable event so that the readable event remains
         * registered and we can wait for the PONG reply. */
        aeDeleteFileEvent(server.el,fd,AE_WRITABLE);
        // 状态发生改变
        server.repl_state = REDIS_REPL_RECEIVE_PONG;
        /* Send the PING, don't check for errors at all, we have the timeout
         * that will take care about this. */
        syncWrite(fd,"PING\r\n",6,100);
        return;
    }

    /* Receive the PONG command. */
    if (server.repl_state == REDIS_REPL_RECEIVE_PONG) {
        char buf[1024];

        /* Delete the readable event, we no longer need it now that there is
         * the PING reply to read. */
        aeDeleteFileEvent(server.el,fd,AE_READABLE);

        /* Read the reply with explicit timeout. */
        buf[0] = '\0';
        if (syncReadLine(fd,buf,sizeof(buf),
            server.repl_syncio_timeout*1000) == -1)
        {
            redisLog(REDIS_WARNING,
                "I/O error reading PING reply from master: %s",
                strerror(errno));
            goto error;
        }

        /* We don't care about the reply, it can be +PONG or an error since
         * the server requires AUTH. As long as it replies correctly, it's
         * fine from our point of view. */
        if (buf[0] != '-' && buf[0] != '+') {
            redisLog(REDIS_WARNING,"Unexpected reply to PING from master.");
            goto error;
        } else {
            redisLog(REDIS_NOTICE,
                "Master replied to PING, replication can continue...");
        }
    }

    /* AUTH with the master if required. */
    if(server.masterauth) {
        err = sendSynchronousCommand(fd,"AUTH",server.masterauth,NULL);
        if (err) {
            redisLog(REDIS_WARNING,"Unable to AUTH to MASTER: %s",err);
            sdsfree(err);
            goto error;
        }
    }

    /* Set the slave port, so that Master's INFO command can list the
     * slave listening port correctly. */
    {
        sds port = sdsfromlonglong(server.port);
        err = sendSynchronousCommand(fd,"REPLCONF","listening-port",port,
                                         NULL);
        sdsfree(port);
        /* Ignore the error if any, not all the Redis versions support
         * REPLCONF listening-port. */
        if (err) {
            redisLog(REDIS_NOTICE,"(non critical): Master does not understand
                    REPLCONF listening-port: %s", err);
            sdsfree(err);
        }
    }

    /* Issue the SYNC command */
    if (syncWrite(fd,"SYNC\r\n",6,server.repl_syncio_timeout*1000) == -1) {
        redisLog(REDIS_WARNING,"I/O error writing to MASTER: %s",
            strerror(errno));
        goto error;
    }

    /* Prepare a suitable temp file for bulk transfer */
    while(maxtries--) {
        snprintf(tmpfile,256,
            "temp-%d.%ld.rdb",(int)server.unixtime,(long int)getpid());
        dfd = open(tmpfile,O_CREAT|O_WRONLY|O_EXCL,0644);
        if (dfd != -1) break;
        sleep(1);
    }
    if (dfd == -1) {
        redisLog(REDIS_WARNING,"Opening the temp file needed for MASTER <-> SLAVE
                synchronization: %s",strerror(errno));
        goto error;
    }

    /* Setup the non blocking download of the bulk file. */
    if (aeCreateFileEvent(server.el,fd, AE_READABLE,readSyncBulkPayload,NULL)
            == AE_ERR)
    {
        redisLog(REDIS_WARNING,"Can't create readable event for SYNC");
        goto error;
    }

    // 状态发生了变化
    server.repl_state = REDIS_REPL_TRANSFER;
    server.repl_transfer_size = -1;
    server.repl_transfer_read = 0;
    server.repl_transfer_last_fsync_off = 0;
    server.repl_transfer_fd = dfd;
    server.repl_transfer_lastio = server.unixtime;
    server.repl_transfer_tmpfile = zstrdup(tmpfile);
    return;

error:
    close(fd);
    server.repl_transfer_s = -1;
    server.repl_state = REDIS_REPL_CONNECT;
    return;
}
```
```c
// replication.c/readSyncBulkPayload()
/* Asynchronously read the SYNC payload we receive from a master */
#define REPL_MAX_WRITTEN_BEFORE_FSYNC (1024*1024*8) /* 8 MB */
void readSyncBulkPayload(aeEventLoop *el, int fd, void *privdata, int mask) {
    char buf[4096];
    ssize_t nread, readlen;
    off_t left;
    REDIS_NOTUSED(el);
    REDIS_NOTUSED(privdata);
    REDIS_NOTUSED(mask);

    // 首先读取服务器端传过来的rdb文件大小
    if (server.repl_transfer_size == -1) {
        if (syncReadLine(fd,buf,1024,server.repl_syncio_timeout*1000) == -1) {
            redisLog(REDIS_WARNING,
                "I/O error reading bulk count from MASTER: %s",
                strerror(errno));
            goto error;
        }
        // ----------------------省略300行(错误处理)----------------------------
        server.repl_transfer_size = strtol(buf+1,NULL,10);
        redisLog(REDIS_NOTICE,
            "MASTER <-> SLAVE sync: receiving %ld bytes from master",
            server.repl_transfer_size);
        return;
    }

    /* Read bulk data */
    left = server.repl_transfer_size - server.repl_transfer_read;
    readlen = (left < (signed)sizeof(buf)) ? left : (signed)sizeof(buf);
    nread = read(fd,buf,readlen);
    if (nread <= 0) {
        redisLog(REDIS_WARNING,"I/O error trying to sync with MASTER: %s",
            (nread == -1) ? strerror(errno) : "connection lost");
        replicationAbortSyncTransfer();
        return;
    }
    server.repl_transfer_lastio = server.unixtime;
    if (write(server.repl_transfer_fd,buf,nread) != nread) {
        redisLog(REDIS_WARNING,"Write error or short write writing to the DB"
            "dump file needed for MASTER <-> SLAVE synchronization: %s", strerror(errno));
        goto error;
    }
    server.repl_transfer_read += nread;

    // 刷新部分数据，如果最后全部接收完成后在刷新，并且文件较大时，会导致比较大的延迟
    if (server.repl_transfer_read >=
        server.repl_transfer_last_fsync_off + REPL_MAX_WRITTEN_BEFORE_FSYNC)
    {
        off_t sync_size = server.repl_transfer_read -
            server.repl_transfer_last_fsync_off;
        rdb_fsync_range(server.repl_transfer_fd,
            server.repl_transfer_last_fsync_off, sync_size);
        server.repl_transfer_last_fsync_off += sync_size;
    }

    // 检查是否传输完成
    if (server.repl_transfer_read == server.repl_transfer_size) {
        if (rename(server.repl_transfer_tmpfile,server.rdb_filename) == -1) {
            redisLog(REDIS_WARNING,"Failed trying to rename the temp DB into dump.rdb"
                    "in MASTER <-> SLAVE synchronization: %s", strerror(errno));
            replicationAbortSyncTransfer();
            return;
        }
        redisLog(REDIS_NOTICE, "MASTER <-> SLAVE sync: Loading DB in memory");
        emptyDb();
        /* Before loading the DB into memory we need to delete the readable
         * handler, otherwise it will get called recursively since
         * rdbLoad() will call the event loop to process events from time to
         * time for non blocking loading. */
        aeDeleteFileEvent(server.el,server.repl_transfer_s,AE_READABLE);
        if (rdbLoad(server.rdb_filename) != REDIS_OK) {
            redisLog(REDIS_WARNING,"Failed trying to load the MASTER synchronization DB from disk");
            replicationAbortSyncTransfer();
            return;
        }
        /* Final setup of the connected slave <- master link */
        zfree(server.repl_transfer_tmpfile);
        close(server.repl_transfer_fd);
        server.master = createClient(server.repl_transfer_s);
        server.master->flags |= REDIS_MASTER;
        server.master->authenticated = 1;
        // 状态发生了变化
        server.repl_state = REDIS_REPL_CONNECTED;
        redisLog(REDIS_NOTICE, "MASTER <-> SLAVE sync: Finished with success");
        /* Restart the AOF subsystem now that we finished the sync. This
         * will trigger an AOF rewrite, and when done will start appending
         * to the new file. */
        if (server.aof_state != REDIS_AOF_OFF) {
            int retry = 10;

            stopAppendOnly();
            while (retry-- && startAppendOnly() == REDIS_ERR) {
                redisLog(REDIS_WARNING,"Failed enabling the AOF after successful"
                        "master synchrnization! Trying it again in one second.");
                sleep(1);
            }
            if (!retry) {
                redisLog(REDIS_WARNING,"FATAL: this slave instance finished the synchronization"
                    "with its master, but the AOF can't be turned on. Exiting now.");
                exit(1);
            }
        }
    }

    return;

error:
    replicationAbortSyncTransfer();
    return;
}
```

### redis作为master时的行为分析 ###

redis作为master时，当收到slave发过来的sync命令后，会执行syncCommand,如果rdb的持久化没有执行，则执行rdbSaveBackground()函数(该函数会启动一个进程)，slave.repl_state的状态变为REDIS_REPL_WAIT_BGSAVE_END,进程结束后，会在serverCron()中执行backgroundSaveDoneHandler()函数，backgroundSaveDoneHandler()函数会执行updateSlavesWaitingBgsave()函数，该函数会注册一个写事件(对应的回调函数位sendBulkToSlave)，向slave同步rdb文件的数据，updateSlavesWaitingBgsave函数执行完毕后，slave.repl_state的状态变为REDIS_REPL_SEND_BULK,sendBulkToSlave函数在发送完rdb文件的数据后，slave.repl_state的状态变为REDIS_REPL_ONLINE。

```c
// redis.c/syncCommand()
void syncCommand(redisClient *c) {
    // ----------------------省略300行----------------------------
    // 检查是否已经有 BGSAVE 在执行，否则就创建一个新的 BGSAVE 任务
    if (server.rdb_child_pid != -1) {
        // 已有 BGSAVE 在执行，检查它能否用于当前客户端的 SYNC 操作
        redisClient *slave;
        listNode *ln;
        listIter li;

        // 检查是否有其他客户端在等待 SYNC 进行
        listRewind(server.slaves,&li);
        while((ln = listNext(&li))) {
            slave = ln->value;
            if (slave->replstate == REDIS_REPL_WAIT_BGSAVE_END) break;
        }
        if (ln) {
            // 找到一个同样在等到 SYNC 的客户端
            // 设置当前客户端的状态，并复制 buffer 。
            copyClientOutputBuffer(c,slave);
            c->replstate = REDIS_REPL_WAIT_BGSAVE_END;
            redisLog(REDIS_NOTICE,"Waiting for end of BGSAVE for SYNC");
        } else {
            // 没有客户端在等待 SYNC ，当前客户端只能等待下次 BGSAVE 进行
            c->replstate = REDIS_REPL_WAIT_BGSAVE_START;
            redisLog(REDIS_NOTICE,"Waiting for next BGSAVE for SYNC");
        }
    } else {
        // 没有 BGSAVE 在进行，自己启动一个。
        /* Ok we don't have a BGSAVE in progress, let's start one */
        redisLog(REDIS_NOTICE,"Starting BGSAVE for SYNC");
        // 后台重写rdb
        if (rdbSaveBackground(server.rdb_filename) != REDIS_OK) {
            redisLog(REDIS_NOTICE,"Replication failed, can't BGSAVE");
            addReplyError(c,"Unable to perform background save");
            return;
        }
        // 等待 BGSAVE 结束,状态发生了变化
        c->replstate = REDIS_REPL_WAIT_BGSAVE_END;
    }
    c->repldbfd = -1;
    c->flags |= REDIS_SLAVE;
    c->slaveseldb = 0;
    listAddNodeTail(server.slaves,c);

    return;
}
```
```c
// rdbSaveBackground()会启动一个进程，进行rdb的持久化，持久化完成后，serverCron会
// 执行backgroundSaveDoneHandler,该函数会调用updateSlavesWaitingBgsave()函数
void updateSlavesWaitingBgsave(int bgsaveerr) {
    listNode *ln;
    int startbgsave = 0;
    listIter li;

    // 遍历所有附属节点
    listRewind(server.slaves,&li);
    while((ln = listNext(&li))) {
        redisClient *slave = ln->value;

        if (slave->replstate == REDIS_REPL_WAIT_BGSAVE_START) {
            // 告诉那些这次不能同步的客户端，可以等待下次 BGSAVE 了。
            startbgsave = 1;
            slave->replstate = REDIS_REPL_WAIT_BGSAVE_END;
        } else if (slave->replstate == REDIS_REPL_WAIT_BGSAVE_END) {
            // 这些是本次可以同步的客户端

            struct redis_stat buf;

            // 如果 BGSAVE 失败，释放 slave 节点
            if (bgsaveerr != REDIS_OK) {
                freeClient(slave);
                redisLog(REDIS_WARNING,"SYNC failed. BGSAVE child returned an error");
                continue;
            }
            // 打开 .rdb 文件
            if ((slave->repldbfd = open(server.rdb_filename,O_RDONLY)) == -1 ||
                // 如果打开失败，释放并清除
                redis_fstat(slave->repldbfd,&buf) == -1) {
                freeClient(slave);
                redisLog(REDIS_WARNING,"SYNC failed. Can't open/stat DB after BGSAVE: %s",
                        strerror(errno));
                continue;
            }
            // 偏移量
            slave->repldboff = 0;
            // 数据库大小（.rdb 文件的大小）
            slave->repldbsize = buf.st_size;
            // 状态发生了变化,开始要传输文件内容
            slave->replstate = REDIS_REPL_SEND_BULK;
            // 清除 slave->fd 的写事件
            aeDeleteFileEvent(server.el,slave->fd,AE_WRITABLE);
            // 创建一个将 .rdb 文件内容发送到附属节点的写事件
            if (aeCreateFileEvent(server.el, slave->fd, AE_WRITABLE, sendBulkToSlave,
                                    slave) == AE_ERR) {
                freeClient(slave);
                continue;
            }
        }
    }
    //-----------------此处省略三百行----------------------
}
```
```c
// replication.c/sendBulkToSlave实现
void sendBulkToSlave(aeEventLoop *el, int fd, void *privdata, int mask) {
    redisClient *slave = privdata;
    REDIS_NOTUSED(el);
    REDIS_NOTUSED(mask);
    char buf[REDIS_IOBUF_LEN];
    ssize_t nwritten, buflen;

    // 刚开始执行 .rdb 文件的发送？
    if (slave->repldboff == 0) {
        /* Write the bulk write count before to transfer the DB. In theory here
         * we don't know how much room there is in the output buffer of the
         * socket, but in pratice SO_SNDLOWAT (the minimum count for output
         * operations) will never be smaller than the few bytes we need. */
        sds bulkcount;

        // 首先将主节点 .rdb 文件的大小发送到附属节点
        bulkcount = sdscatprintf(sdsempty(),"$%lld\r\n",(unsigned long long)
            slave->repldbsize);
        if (write(fd,bulkcount,sdslen(bulkcount)) != (signed)sdslen(bulkcount))
        {
            sdsfree(bulkcount);
            freeClient(slave);
            return;
        }
        sdsfree(bulkcount);
    }

    // 设置主节点 .rdb 文件的偏移量
    lseek(slave->repldbfd,slave->repldboff,SEEK_SET);

    // 读取主节点 .rdb 文件的数据到 buf
    buflen = read(slave->repldbfd,buf,REDIS_IOBUF_LEN);
    if (buflen <= 0) {
        // 主节点 .rdb 文件读取错误，返回
        redisLog(REDIS_WARNING,"Read error sending DB to slave: %s",
            (buflen == 0) ? "premature EOF" : strerror(errno));
        freeClient(slave);
        return;
    }

    // 将 buf 发送给附属节点
    if ((nwritten = write(fd,buf,buflen)) == -1) {
        // 附属节点写入出错，返回
        redisLog(REDIS_VERBOSE,"Write error sending DB to slave: %s",
            strerror(errno));
        freeClient(slave);
        return;
    }

    // 更新偏移量
    slave->repldboff += nwritten;

    // .rdb 文件全部发送完毕
    if (slave->repldboff == slave->repldbsize) {
        // 关闭 .rdb 文件
        close(slave->repldbfd);
        // 重置
        slave->repldbfd = -1;
        // 删除发送事件
        aeDeleteFileEvent(server.el,slave->fd,AE_WRITABLE);
        // 状态发生了变化
        slave->replstate = REDIS_REPL_ONLINE;
        // TODO：
        if (aeCreateFileEvent(server.el, slave->fd, AE_WRITABLE,
            sendReplyToClient, slave) == AE_ERR) {
            freeClient(slave);
            return;
        }
        redisLog(REDIS_NOTICE,"Synchronization with slave succeeded");
    }
}
```
```c
// slave.repl_state的状态变成REDIS_REPL_ONLINE后，master只需要同步发送数据到slave，
// 这是在redis.c/propagate中实现的,具体的实现是在replicationFeedSlaves中实现
void propagate(struct redisCommand *cmd, int dbid, robj **argv, int argc, int flags)
{
    if (server.aof_state != REDIS_AOF_OFF && flags & REDIS_PROPAGATE_AOF)
        feedAppendOnlyFile(cmd,dbid,argv,argc);
    if (flags & REDIS_PROPAGATE_REPL && listLength(server.slaves))
        replicationFeedSlaves(server.slaves,dbid,argv,argc);
}
```
