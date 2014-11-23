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
            redisLog(REDIS_NOTICE,"(non critical): Master does not understand REPLCONF listening-port: %s", err);
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
        redisLog(REDIS_WARNING,"Opening the temp file needed for MASTER <-> SLAVE synchronization: %s",strerror(errno));
        goto error;
    }

    /* Setup the non blocking download of the bulk file. */
    if (aeCreateFileEvent(server.el,fd, AE_READABLE,readSyncBulkPayload,NULL)
            == AE_ERR)
    {
        redisLog(REDIS_WARNING,"Can't create readable event for SYNC");
        goto error;
    }

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

### redis作为master时的行为分析 ###
