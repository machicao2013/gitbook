replication的配置
==================

## replication在配置文件中的配置 ##

配置一个slave非常的简单，有两种方法：
1. 在配置文件添加一行: slaveof 192.168.1.1 6379
2. 使用redis-cli连接上master,执行：SLAVEOF 192.168.1.1 10086

redis.conf中和replication相关的配置(关于各项的配置，注释已经解释的非常的清楚，不作过多的介绍了)：
```sh
# Master-Slave replication. Use slaveof to make a Redis instance a copy of
# another Redis server. Note that the configuration is local to the slave
# so for example it is possible to configure the slave to save the DB with a
# different interval, or to listen to another port, and so on.
#
slaveof <masterip> <masterport>

# If the master is password protected (using the "requirepass" configuration
# directive below) it is possible to tell the slave to authenticate before
# starting the replication synchronization process, otherwise the master will
# refuse the slave request.
#
# masterauth <master-password>

# When a slave lost the connection with the master, or when the replication
# is still in progress, the slave can act in two different ways:
#
# 1) if slave-serve-stale-data is set to 'yes' (the default) the slave will
#    still reply to client requests, possibly with out of date data, or the
#    data set may just be empty if this is the first synchronization.
#
# 2) if slave-serve-stale data is set to 'no' the slave will reply with
#    an error "SYNC with master in progress" to all the kind of commands
#    but to INFO and SLAVEOF.
#
slave-serve-stale-data yes

# You can configure a slave instance to accept writes or not. Writing against
# a slave instance may be useful to store some ephemeral data (because data
# written on a slave will be easily deleted after resync with the master) but
# may also cause problems if clients are writing to it because of a
# misconfiguration.
#
# Since Redis 2.6 by default slaves are read-only.
#
# Note: read only slaves are not designed to be exposed to untrusted clients
# on the internet. It's just a protection layer against misuse of the instance.
# Still a read only slave exports by default all the administrative commands
# such as CONFIG, DEBUG, and so forth. To a limited extend you can improve
# security of read only slaves using 'rename-command' to shadow all the
# administrative / dangerous commands.
slave-read-only yes

# Slaves send PINGs to server in a predefined interval. It's possible to change
# this interval with the repl_ping_slave_period option. The default value is 10
# seconds.
#
repl-ping-slave-period 10

# The following option sets a timeout for both Bulk transfer I/O timeout and
# master data or ping response timeout. The default value is 60 seconds.
#
# It is important to make sure that this value is greater than the value
# specified for repl-ping-slave-period otherwise a timeout will be detected
# every time there is low traffic between the master and the slave.
#
repl-timeout 60

# The slave priority is an integer number published by Redis in the INFO output.
# It is used by Redis Sentinel in order to select a slave to promote into a
# master if the master is no longer working correctly.
#
# A slave with a low priority number is considered better for promotion, so
# for instance if there are three slaves with priority 10, 100, 25 Sentinel will
# pick the one wtih priority 10, that is the lowest.
#
# However a special priority of 0 marks the slave as not able to perform the
# role of master, so a slave with priority of 0 will never be selected by
# Redis Sentinel for promotion.
#
# By default the priority is 100.
slave-priority 100
```

## replication在redisServer中相关的项 ##

redisServer中关于replication的项比较多，包括：

```c
/* Slave specific fields */
char *masterauth;               /* AUTH with this password with master */
char *masterhost;               /* Hostname of master */
int masterport;                 /* Port of master */
int repl_ping_slave_period;     /* Master pings the slave every N seconds */
int repl_timeout;               /* Timeout after N seconds of master idle */
redisClient *master;     /* Client that is master for this slave */
int repl_syncio_timeout; /* Timeout for synchronous I/O calls */
int repl_state;          /* Replication status if the instance is a slave */
off_t repl_transfer_size; /* Size of RDB to read from master during sync. */
off_t repl_transfer_read; /* Amount of RDB read from master during sync. */
off_t repl_transfer_last_fsync_off; /* Offset when we fsync-ed last time. */
int repl_transfer_s;     /* Slave -> Master SYNC socket */
int repl_transfer_fd;    /* Slave -> Master SYNC temp file descriptor */
char *repl_transfer_tmpfile; /* Slave-> master SYNC temp file name */
time_t repl_transfer_lastio; /* Unix time of the latest read, for timeout */
int repl_serve_stale_data; /* Slave down掉后，是否使用旧(stale)数据，配置参数slave-serve-stale-data决定 */
int repl_slave_ro;          /* Slave是否为只读，配置参数slave-read-only决定  */
time_t repl_down_since; /* Unix time at which link with master went down */
int slave_priority;             /* Reported in INFO and used by Sentinel. */
```
