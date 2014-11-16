aof持久化
====

**aof相关的配置**

```bash
appendonly no
appendfsync always
appendfsync everysec
appendfsync no
```

appendonly no表示关闭aof功能
appendfsync always表示每一次都要写aof,会阻塞进程
appendfsync everysec表示每一秒写aof，后台线程执行,如果需要aof生效，则配置此种方式
appendfsync no表示不主动写aof,只有在redis被关闭，或者aof功能被关闭，或者在定时任务里面才会执行

**redisServer中与aof相关的配置
```bash
int aof_state;                  /* REDIS_AOF_(ON|OFF|WAIT_REWRITE) */
int aof_fsync;                  /* Kind of fsync() policy */
char *aof_filename;             /* Name of the AOF file */
int aof_no_fsync_on_rewrite;    /* Don't fsync if a rewrite is in prog. */
int aof_rewrite_perc;           /* Rewrite AOF if % growth is > M and... */
off_t aof_rewrite_min_size;     /* the AOF file is at least N bytes. */
off_t aof_rewrite_base_size;    /* AOF size on latest startup or rewrite. */
off_t aof_current_size;         /* AOF current size. */
int aof_rewrite_scheduled;      /* Rewrite once BGSAVE terminates. */
pid_t aof_child_pid;            /* PID if rewriting process */
list *aof_rewrite_buf_blocks;   /* Hold changes during an AOF rewrite. */
sds aof_buf;      /* AOF buffer, written before entering the event loop */
int aof_fd;       /* File descriptor of currently selected AOF file */
int aof_selected_db; /* Currently selected DB in AOF */
time_t aof_flush_postponed_start; /* UNIX time of postponed AOF flush */
time_t aof_last_fsync;            /* UNIX time of last fsync() */
time_t aof_rewrite_time_last;   /* Time used by last AOF rewrite run. */
time_t aof_rewrite_time_start;  /* Current AOF rewrite start time. */
int aof_lastbgrewrite_status;   /* REDIS_OK or REDIS_ERR */
unsigned long aof_delayed_fsync;  /* delayed AOF fsync() counter */
```

**aof触发条件**

- bgrewrite
- serverCron

**bgrewriteaof处理**

bgwriteaof命令对应的是bgwriteaofCommand函数，bgwriteaofCommand的处理流程如下：

```c
void bgrewriteaofCommand(redisClient *c) {
    // 在aof和rdb没有进行的情况下才会执行重写，即调用rewriteAppendOnlyFileBackground函数
    if (server.aof_child_pid != -1) {
        addReplyError(c,"Background append only file rewriting already in progress");
    } else if (server.rdb_child_pid != -1) {
        // 如果此时正在执行rdb，则将aof_rewrite_scheduled值为1，在serverCron函数中回进行处理，也就是延迟处理aof
        server.aof_rewrite_scheduled = 1;
        addReplyStatus(c,"Background append only file rewriting scheduled");
    } else if (rewriteAppendOnlyFileBackground() == REDIS_OK) {
        addReplyStatus(c,"Background append only file rewriting started");
    } else {
        addReply(c,shared.err);
    }
}
```

从上面的处理来看，在aof没有进行,或者没有正在进行rdb的情况下才会执行重写，即调用rewriteAppendOnlyFileBackground函数,该函数会重启一个进程，进行重写aof的工作，具体的代码如下：

```c
int rewriteAppendOnlyFileBackground(void) {
    // ....
    // 后台重写正在执行
    if (server.aof_child_pid != -1) return REDIS_ERR;

    // 开始时间
    start = ustime();
    if ((childpid = fork()) == 0) {
        char tmpfile[256];

        /* Child */
        // 关闭网络连接
        if (server.ipfd > 0) close(server.ipfd);
        if (server.sofd > 0) close(server.sofd);

        // 创建临时文件
        snprintf(tmpfile,256,"temp-rewriteaof-bg-%d.aof", (int) getpid());
        // 重写
        if (rewriteAppendOnlyFile(tmpfile) == REDIS_OK) {
            // 向父进程发送信号, exitFromChild 定义于 redis.c
            exitFromChild(0);
        } else {
            exitFromChild(1);
        }
    } else {
        /* Parent */
        server.stat_fork_time = ustime()-start;

        // 如果创建子进程失败，直接返回
        if (childpid == -1) {
            // log
            return REDIS_ERR;
        }

        //.....,此处省略三百行
        return REDIS_OK;
    }
    return REDIS_OK; /* unreached */
}
```

**serverCron处理**

在aof功能开启的情况下，会维持以下三个变量：
- 记录当前aof文件大小的变量aof_current_size。
- 记录最后一次aof重写之后，aof文件大小的变量aof_rewrite_base_size
- 增长百分比变量aof_rewrite_perc

在serverCron中，会检查以下条件是否全部满足，如果是的话，就会触发自动的aof重写：

- 没有bgsave命令在执行
- 没有bgwriteaof在进行
- 当前aof文件和最后一次aof重写后的大小之间的比率大于等于指定的增长百分比

具体的处理如下：

```c
// 如果用户执行 BGREWRITEAOF 命令的话(如果此时rdb正在执行，则不会立即执行
// bgrewrtieaof，而会将aof_rewrite_scheduled值为1)，在后台开始 AOF 重写
if (server.rdb_child_pid == -1 && server.aof_child_pid == -1 &&
    server.aof_rewrite_scheduled)
{
    rewriteAppendOnlyFileBackground();
}
```
