rdb持久化
====

****rdb相关的配置****

```bash
save none
save 900 1
save 300 10
save 60  1000
```

save none表示不生成rdb，save m n表示在m秒内有n次以上改变则会进行rdb

**redisServer中与rdb相关的项**
```c
long long dirty;                /* Changes to DB from the last save */
long long dirty_before_bgsave;  /* Used to restore dirty on failed BGSAVE */
pid_t rdb_child_pid;            /* PID of RDB saving child */
struct saveparam *saveparams;   /* Save points array for RDB,即保存上面rdb相关的配置 */
int saveparamslen;              /* Number of saving points */
char *rdb_filename;             /* Name of RDB file */
int rdb_compression;            /* Use compression in RDB? */
int rdb_checksum;               /* Use RDB checksum? */
time_t lastsave;                /* Unix time of last save succeeede */
time_t rdb_save_time_last;      /* Time used by last RDB save run. */
time_t rdb_save_time_start;     /* Current RDB save start time. */
int lastbgsave_status;          /* REDIS_OK or REDIS_ERR */
int stop_writes_on_bgsave_err;  /* Don't allow writes if can't BGSAVE */
```

**rdb触发条件**

- save
- bgsave
- serverCron

rdb相关的处理都在rdb.h和rdb.c中

**save处理**

客户端输入save指令后，服务端会调用saveCommand进行相应的处理，如果后台保存工作正在进行，则直接返回，否则回调用rdbSave作相应的处理，此时会*阻塞进程*
```c
void saveCommand(redisClient *c) {
    // 如果后台正在保存rdb文件，则当前保存回失败
    if (server.rdb_child_pid != -1) {
        addReplyError(c,"Background save already in progress");
        return;
    }

    // 保存所有数据库数据到指定的文件
    if (rdbSave(server.rdb_filename) == REDIS_OK) {
        addReply(c,shared.ok);
    } else {
        addReply(c,shared.err);
    }
}

```

**bgsave处理**

客户端输入bgsave指令后，服务端会调用bgsaveCommand进行处理，如果后台保存工作正在进行，则直接返回，否在调用rdbSaveBackground作相应处理，此时rdbSaveBackground会启动一个专门的进程来进行后台保存工作.

```c
void bgsaveCommand(redisClient *c) {
    // 如果后台正在重写rdb，或者重写aof，则此次重写rdb会失败
    if (server.rdb_child_pid != -1) {
        addReplyError(c,"Background save already in progress");
    } else if (server.aof_child_pid != -1) {
        addReplyError(c,"Can't BGSAVE while AOF log rewriting is in progress");
        // 开始后台写入
    } else if (rdbSaveBackground(server.rdb_filename) == REDIS_OK) {
        addReplyStatus(c,"Background saving started");
    } else {
        addReply(c,shared.err);
    }
}

```

**serverCron处理**

serverCron里面执行的是redis的定时任务，每隔一定的周期都会执行一次，此函数里面与rdb相关的操作如下

```c
for (j = 0; j < server.saveparamslen; j++) {
    struct saveparam *sp = server.saveparams+j;

    if (server.dirty >= sp->changes &&
        server.unixtime-server.lastsave > sp->seconds) {
        redisLog(REDIS_NOTICE,"%d changes in %d seconds. Saving...",
            sp->changes, sp->seconds);
        rdbSaveBackground(server.rdb_filename);
        break;
    }
}

```
