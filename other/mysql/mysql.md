mysql性能优化
==========

**Mysql性能优化可从如下几个方面着手**

1. SQL优化
2. 索引优化
3. 数据库(表)结构优化
4. 系统配置优化

**SQL优化**

1. 开启慢查询日志，查找问题(很多时候都是一些慢查询拖累了这个数据库的性能)
    - 在配置文件中配置(my.cnf),配置完毕需要重启，不适合线上数据库
        ```
            #path可修改为绝对或者相对路径
            log_slow_queries=slow-queries.log
            #查询时间超过2s记录
            long_query_time=2
            #没有使用索引的查询记录
            log_queries_not_using_indexs
        ```
    - mysql命令行下配置
        ```
            #查看log_query_time变量的值
            show variables like '%long%';
            #如果long_query_time的值不是期望值，重新设定
            set global long_query_time=2;
            #查询 slow_query_log 和 slow_query_log_file的值
            show variables like "%slow%";
            #开启慢查询日志 on或者ON都可以，不区分大小写
            set global slow_query_log='on';
            #慢查询日志文件路径可修改
            set global slow_query_log='/data/mysql/slow.log';
        ```
2. explain分析sql的执行
    - table查询的数据表
    - type:
        - const:主键或者唯一索引一般是const
        - eq_reg:性能最好是eq_reg，是一种范围查询，唯一索引，主键可能是此种查找
        - ref:常见于连接查询，一个表基于另外一个索引的查找
        - range:基于索引的范围查找
        - index:通常是对index的扫描
        - all:表扫描
    - possible_keys: 查询中可以使用的索引
    - key: 查询中实际使用到的索引，为null表示没有使用索引
    - key_len: 索引长度，越小越好
    - ref: 显示索引的那一列被使用了，最好是一个常数
    - rows:扫描的行数
    - extra: 出现using filesort查询需要优化(group by),出现using temporary需要优化(order by时容易出现)

** 索引优化 **

1. 选择合适的列建立索引(在where中经常出现的查询条件的列应当创建索引,group by, order by, on)
    - 索引字段越小越好
    - 离散度大的列放在联合索引的前面(离散度越大，过滤的数据越多),判断列的离散度可以根据select count(distinct col1), count(distinct col2) from table
2. 索引优化SQL的方法，增加索引会影响写入效率(insert, update, delete)删除重复和冗余的索引。
    - 使用工具pt-duplicate-key-checker分析使用pt-index-usgae工具配合慢查询日志来分析不再使用的索引

** 数据库(表)结构优化 **

1. 选择合适的(列)数据类型
    - 选择可以存下数据的最小的数据类型
    - 选择尽量简单的数据类型
    - 尽可能对列加上 not null(Innodb特性)，给出default
    - 尽快能不使用text等大的数据类型，如果要用，尽量和其它字段分开，单独成表
2. 表的垂直拆分
    - 把原来有很多列的表拆分成多个表，降低表的宽度拆分原则：不经常使用的字段放在一个表,很大的字段放在一个表,常用的字段放在一个表
3. 表的水平拆分
    - 水平拆分解决单表数据量过大的问题，水平拆分之后的每一张表结构相同常用拆分方法：取模，hash等分表带来的挑战：跨分区表数据查询；统计及后台操作。使用汇总表,前后台业务分开

