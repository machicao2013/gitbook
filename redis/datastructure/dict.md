redis字典的实现
==============

hashtable在redis中占有非常重要的地位。其db就是一个大的字典。其实现的渐进式rehash更是值得我们学习。

##redis dict涉及的数据结构##

dict主要涉及到的数据结构如下:

```c
// hash table的项
typedef struct dictEntry {
    void *key;
    union {
        void *val;
        uint64_t u64;
        int64_t s64;
    } v;
    struct dictEntry *next;
} dictEntry;
```
```c
// hash的类型,不同的字典有不同的hash类型
typedef struct dictType {
    unsigned int (*hashFunction)(const void *key);
    void *(*keyDup)(void *privdata, const void *key);
    void *(*valDup)(void *privdata, const void *obj);
    int (*keyCompare)(void *privdata, const void *key1, const void *key2);
    void (*keyDestructor)(void *privdata, void *key);
    void (*valDestructor)(void *privdata, void *obj);
} dictType;
```
```c
// hash表
typedef struct dictht {
    dictEntry **table;
    // 指针数组的大小
    unsigned long size;
    // 指针数组的长度掩码，用于计算索引值
    unsigned long sizemask;
    // 哈希表现有的节点数量
    unsigned long used;
} dictht;
```
```c
// 一个字典有两个hash表，用于渐进式的rehash
typedef struct dict {
    // 特定于类型的处理函数
    dictType *type;
    // 类型处理函数的私有数据
    void *privdata;
    // 哈希表（2个）
    dictht ht[2];
    // 记录 rehash 进度的标志，值为-1 表示 rehash 未进行
    int rehashidx;
    // 当前正在运作的安全迭代器数量
    int iterators;
} dict;
```

##redis dict的部分操作##

### dict的扩容 ###

**什么时候会扩容**

每次在添加元素的时候(dictAdd,dictAddRaw)时候，会调用_dictExpandIfNeeded函数,_dictExpandIfNeeded函数会调用dictExpand函数，该函数会将rehashidx置为0，然后就会启动rehash过程。
```c
static int _dictExpandIfNeeded(dict *d)
{
    // 已经在渐进式 rehash 当中，直接返回
    if (dictIsRehashing(d)) return DICT_OK;

    // 如果哈希表为空，那么将它扩展为初始大小
    if (d->ht[0].size == 0) return dictExpand(d, DICT_HT_INITIAL_SIZE);

    // 如果哈希表ç=acp#onPopupPost()
    // 已用节点数 >= 哈希表的大小，
    // 并且以下条件任一个为真：
    //   1) dict_can_resize 为真
    //   2) 已用节点数除以哈希表大小之比大于
    //      dict_force_resize_ratio(默认5)
    // 那么调用 dictExpand 对哈希表进行扩展
    // 扩展的体积至少为已使用节点数的两倍
    if (d->ht[0].used >= d->ht[0].size &&
        (dict_can_resize ||
         d->ht[0].used/d->ht[0].size > dict_force_resize_ratio))
    {
        return dictExpand(d, d->ht[0].used*2);
    }
    return DICT_OK;
}
```
```c
int dictExpand(dict *d, unsigned long size)
{
    dictht n; /* the new hash table */

    // 计算哈希表的真实大小
    unsigned long realsize = _dictNextPower(size);
    /* the size is invalid if it is smaller than the number of
     * elements already inside the hash table */
    if (dictIsRehashing(d) || d->ht[0].used > size)
        return DICT_ERR;

    /* Allocate the new hash table and initialize all pointers to NULL */
    n.size = realsize;
    n.sizemask = realsize-1;
    n.table = zcalloc(realsize*sizeof(dictEntry*));
    n.used = 0;

    /* Is this the first initialization? If so it's not really a rehashing
     * we just set the first hash table so that it can accept keys. */
    // 如果 ht[0] 为空，那么这就是一次创建新哈希表行为
    // 将新哈希表设置为 ht[0] ，然后返回
    if (d->ht[0].table == NULL) {
        d->ht[0] = n;
        return DICT_OK;
    }

    /* Prepare a second hash table for incremental rehashing */
    // 如果 ht[0] 不为空，那么这就是一次扩展字典的行为
    // 将新哈希表设置为 ht[1] ，并打开 rehash 标识
    d->ht[1] = n;
    d->rehashidx = 0;

    return DICT_OK;
}
```
**扩容过程什么时候进行**

当rehashidx在dictExpand中被置为0时，由下面三个函数完成扩容的工作。
1. int dictRehash(dict *d, int n);
2. int dictRehashMilliseconds(dict *d, int ms);
3. void _dictRehashStep(dict *d);

dictRehash执行的是n步rehash,即dictRehash每执行一次，会移动n个hash桶. dictRehashMilliseconds在serverCron中会被调用. _dictRehashStep在每次增，删，查的过程都会被执行一次。

**扩容进行的过程**

扩容的具体过程是在dictRehash函数中完成的。
```c
int dictRehash(dict *d, int n) {
    if (!dictIsRehashing(d)) return 0;
    while(n--) {
        dictEntry *de, *nextde;

        // 如果 ht[0] 已经为空，那么迁移完毕
        // 用 ht[1] 代替原来的 ht[0]
        if (d->ht[0].used == 0) {

            // 释放 ht[0] 的哈希表数组
            zfree(d->ht[0].table);

            // 将 ht[0] 指向 ht[1]
            d->ht[0] = d->ht[1];

            // 清空 ht[1] 的指针
            _dictReset(&d->ht[1]);

            // 关闭 rehash 标识
            d->rehashidx = -1;

            // 通知调用者， rehash 完毕
            return 0;
        }

        assert(d->ht[0].size > (unsigned)d->rehashidx);
        // 移动到数组中首个不为 NULL 链表的索引上
        while(d->ht[0].table[d->rehashidx] == NULL) d->rehashidx++;
        // 指向链表头
        de = d->ht[0].table[d->rehashidx];
        // 将链表内的所有元素从 ht[0] 迁移到 ht[1]
        // 因为桶内的元素通常只有一个，或者不多于某个特定比率
        // 所以可以将这个操作看作 O(1)
        while(de) {
            unsigned int h;

            nextde = de->next;

            /* Get the index in the new hash table */
            // 计算元素在 ht[1] 的哈希值
            h = dictHashKey(d, de->key) & d->ht[1].sizemask;

            // 添加节点到 ht[1] ，调整指针
            de->next = d->ht[1].table[h];
            d->ht[1].table[h] = de;

            // 更新计数器
            d->ht[0].used--;
            d->ht[1].used++;

            de = nextde;
        }

        // 设置指针为 NULL ，方便下次 rehash 时跳过
        d->ht[0].table[d->rehashidx] = NULL;

        // 前进至下一索引
        d->rehashidx++;
    }

    // 通知调用者，还有元素等待 rehash
    return 1;
}
```

**others**

1. 对于查找,删除操作，如果正在进行rehash，则查找操作会涉及到ht[0]和ht[1]。
2. 对于添加操作，如果正在进行rehash，则直接添加到ht[1]。
3. dict.c中有个变量dict_can_resize控制着是否允许自动调整hash表的大小，该变量的改变主要靠如下两个函数：
    ```c
    void dictEnableResize(void) {
        dict_can_resize = 1;
    }
    void dictDisableResize(void) {
        dict_can_resize = 0;
    }
    ```
    在系统运行有后台进程时，不允许自动自动调整大小，这是为了为了使得类linux系统的copy-on-write有更好的性能（没有调整大小， 就没有rehash，这样父进程的db没有改变，子进程就不需要真的copy）。在后台子进程退出后，又会允许resize。

###dict缩容###

在redis中，hashtable不是只能扩容，还能够缩容。其过程和扩容一样：
1. 创建一个比ht[0]->table小的ht[1]->table
2. 将ht[0]->table中的所有数据迁移到ht[1]->table
3. 将ht[0]的数据清空，并将ht[1]替换为ht[0]

判断字典的缩容的条件是在redis.c/htNeedsResize函数中，缩容是在dictReSize中完成。最后会在serverCron中被调用。
```c
int htNeedsResize(dict *dict) {
    long long size, used;

    // 哈希表大小
    size = dictSlots(dict);

    // 哈希表已用节点数量
    used = dictSize(dict);

    // 当哈希表的大小大于 DICT_HT_INITIAL_SIZE(4)
    // 并且字典的填充率低于 REDIS_HT_MINFILL(10) 时
    // 返回 1
    return (size && used && size > DICT_HT_INITIAL_SIZE &&
        (used*100/size < REDIS_HT_MINFILL));
}
```

###小结###

1. Redis字典的底层实现是哈希表，每个字典使用两个哈希表，一般情况下只使用0号哈希表，只有在rehash进行时，才会同时使用0号和1号哈希表。
2. 哈希表使用链地址法来解决冲突的问题。
3. rehash可以用于扩容或者缩容哈希表。
4. 对哈希表的rehash是分多次、渐进式地进行的。
