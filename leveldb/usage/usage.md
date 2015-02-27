leveldb的基本操作
================

## 打开数据库 ##

1. 下面的例子显示如何打开一个数据库
    ```c
    // open db
    leveldb::DB *db;
    leveldb::Options options;
    options.create_if_missing = true;
    // options.error_if_exists = true;
    leveldb::Status status = leveldb::DB::Open(options, "./testdb.db", &db);
    EXPECT_TRUE(status.ok());
    if (!status.ok()) {
        cout << "******" << status.ToString() << endl;
    }
    ```
2. 当数据库已经存在的时候，如果你想产生一个错误，执行语句：options.error_if_exists = true;

## 状态 ##

1. leveldb中的大部分函数会返回leveldb::Status这种类型的结果，你可以通过status.ok()方法检测操作是否ok,并答应错误消息
    ```c
    leveldb::Status status = ...;
    if(!status.ok())
        cout << status.ToString() << endl;
    ```

## 关闭数据库 ##

1. 当对数据库的操作完毕时，通过delete db操作就可以关闭数据库。

## 读写操作 ##

1. leveldb提供了Put, Delete和Get方法修改/查询数据库。
    ```c
        std::string value;
        leveldb::Status s = db->Get(leveldb::ReadOptions(), key1, &value);
        if (s.ok()) s = db->Put(leveldb::WriteOptions(), key2, value);
        if (s.ok()) s = db->Delete(leveldb::WriteOptions(), key1);
    ```

## 原子更新 ##

1. 原子操作通过WriteBatch完成，例子如下：
    ```c
    leveldb::DB *db;
    leveldb::Options options;
    options.create_if_missing = true;
    leveldb::Status status = leveldb::DB::Open(options, "./testdb.db", &db);
    ASSERT_TRUE(status.ok());

    std::string key1 = "name";
    std::string key2 = "first name";
    std::string value;
    status = db->Get(leveldb::ReadOptions(), key1, &value);
    EXPECT_TRUE(status.ok());
    leveldb::WriteBatch batch;
    batch.Delete(key1);
    batch.Put(key2, value);
    status = db->Write(leveldb::WriteOptions(), &batch);
    ASSERT_TRUE(status.ok());
    delete db;
    ```

## 同步写 ##

1. 默认情况下，leveldb的写操作都是异步的，用户进程执行write操作后就返回了。操作系统内存到底层持久化存储是异步发生的。sync标识可以执行同步操作，阻塞进程直到将数据存储到存储系统。
    ```c
    leveldb::WriteOptions write_options;
    write_options.sync = true;
    db->Put(write_options, key, value);
    ```
2. Asynchronous writes are often more than a thousand times as fast as synchronous writes. The downside of asynchronous writes is that a crash of the machine may cause the last few updates to be lost. Note that a crash of just the writing process (i.e., not a reboot) will not cause any loss since even when sync is false, an update is pushed from the process memory into the operating system before it is considered done.
3. Asynchronous writes can often be used safely. For example, when loading a large amount of data into the database you can handle lost updates by restarting the bulk load after a crash. **A hybrid(混合) scheme is also possible where every Nth write is synchronous, and in the event of a crash, the bulk load is restarted just after the last synchronous write finished by the previous run. (The synchronous write can update a marker that describes where to restart on a crash.)**

## 并发(Concurrency) ##

1. 在某一时刻，一个数据库可以只能被一个进程打开。leveldb的实现需要从操作系统获取锁防止被误用。在单个进程里面，leveldb::DB对象可以被多个并发的线程安全共享。不同的线程可以在相同的数据库上执行写入或者fetch iterators或者调用Get等操作而不需要额外的同步操作(leveldb的实现会自动的获取同步).然而其它的对象(像Iterator和WriteBatch)则可能需要额外的同步。如果两个线程共享一个对象，它们必须使用自己的锁协议访问。

## 迭代器 ##

1. example:
    ```c
    leveldb::DB *db;
    leveldb::Options options;
    options.create_if_missing = true;
    leveldb::Status status = leveldb::DB::Open(options, "./testdb.db", &db);
    ASSERT_TRUE(status.ok());

    leveldb::Iterator *itr = db->NewIterator(leveldb::ReadOptions());
    for(itr->SeekToFirst(); itr->Valid(); itr->Next()) {
        cout << itr->key().ToString() << ":" << itr->value().ToString() << endl;
    }
    delete db;
    ```

## 快照 ##

1. snapshots在整个key-value存储状态上提供了一致的只读视图。ReadOptions::snapshot可能non-NULL暗示在一个特定的数据库版本上执行。如果ReadOptions::snapshots是NULL，读操作则在一个隐式的快照上进行。
    ```c
    leveldb::DB *db;
    leveldb::Options options;
    options.create_if_missing = true;
    leveldb::Status status = leveldb::DB::Open(options, "./testdb.db", &db);
    ASSERT_TRUE(status.ok());

    leveldb::ReadOptions read_options;
    read_options.snapshot = db->GetSnapshot();
    leveldb::Iterator *itr = db->NewIterator(read_options);
    for(itr->SeekToLast(); itr->Valid(); itr->Prev()) {
        cout << itr->key().ToString() << ":" << itr->value().ToString() << endl;
    }
    delete itr;

    db->ReleaseSnapshot(read_options.snapshot);

    delete db;
    ```
2. 注意：当snapshot不再需要的时候，应该通过DB:ReleaseSnapshot接口进行释放.This allows the implementation to get rid of state that was being maintained just to support reading as of that snapshot.

## 切片(Slice) ##

1. 上面的itr->key()和itr->value()放回的类型为leveldb::Slice。Slice结构体包含长度和一个指向额外字节数据的指针。返回一个Slice比返回std::string要廉价，因为不需要拷贝潜在的大key和大value。
2. **note**: 当使用Slices时，需要由调用者确保Slices里面_data所指向的数据的存活范围，Slice实现的代码片段如下：
    ```c
    Slice() : data_(""), size_(0) { }

    // Create a slice that refers to d[0,n-1].
    Slice(const char* d, size_t n) : data_(d), size_(n) { }

    // Create a slice that refers to the contents of "s"
    Slice(const std::string& s) : data_(s.data()), size_(s.size()) { }

    // Create a slice that refers to s[0,strlen(s)-1]
    Slice(const char* s) : data_(s), size_(strlen(s)) { }
    ```
3. 下面代码有bug:
    ```c
    leveldb::Slice slice1 = "hello";
    // have bug, str outof range
    if(!slice1.empty()) {
        std::string str = "world";
        slice1 = str;
    }
    print(slice1);
    ```

## Comparators ##

1. 前面的例子都是用默认的排序算法(字典序)进行存储的。我们可以自己定义比较函数，并在创建数据库的时候设置新建的比较器。
    ```c
    TwoPartComparator cmp;
    leveldb::DB* db;
    leveldb::Options options;
    options.create_if_missing = true;
    options.comparator = &cmp;
    leveldb::Status status = leveldb::DB::Open(options, "/tmp/testdb", &db);
    ```

## Performance ##

Performance can be tuned by changing the default values of the types defined in include/leveldb/options.h.

### BlockSize ###

1. leveldb会将相邻的key放在一个块(block)里面，这些块作为从持久化层(persistent storage)传输或者获取的单元。
2. 默认块的大小为4kb，没有压缩。
3. 应用程序可以通过leveldb::Options::block_size进行调优。
    - Applications that mostly do bulk scans over the contents of the database may wish to increase this size.
    - Applications that do a lot of point reads of small values may wish to switch to a smaller block size if performance measurements indicate an improvement.
4. 使用block_size<1kb或者大于几兆字节都没有好处。
5. compression will be more effective with larger block sizes.

### Compression ###

1. 每一个块在写入持久化存储系统前都会被压缩。
2. 在默认情况下，压缩是打开的。可以通过leveldb::Options::compression选项设置为kNoCompression关闭。

### Cache ###

1. 数据库的内容都存储在文件系统上的一系列文件里，并且每个文件存储一系列压缩的块。如果options.cache不为NULL，在该cache可以cache被频繁使用的未经压缩的块数据。
    ```c
    #include"leveldb/cache.h"
    leveldb::Options options;
    options.cache = leveldb::NewLRUCache(100 * 1048576);  // 100MB cache
    leveldb::DB* db;
    leveldb::DB::Open(options, name, &db);
    // ... use the db ...
    delete db
    delete options.cache;
    ```

### Key Layout ###


### Filters ###


