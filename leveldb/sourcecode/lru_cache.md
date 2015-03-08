LRU Cache
==========

1. leveldb的lru cache实现使用的是一个双向循环链表和一个hashtable,hashtable没有使用编译器自带的实现，而是自己实现的。

## 设计的数据结构 ##

### Cache ###

1. Cache定义了Cache所提供的接口(增删改查)，是一个虚基类。

### LRUHandle ###

1. LRUHandle是hashtable和双向循环链表共有的entry.具体的定义如下：
    ```c
    struct LRUHandle {
        void* value;
        void (*deleter)(const Slice&, void* value);
        LRUHandle* next_hash;   // hashtable中的下一个元素，使用链表解决hash冲突
        LRUHandle* next;        // 双向链表的下一个元素
        LRUHandle* prev;        // 双向链表的前一个元素
        size_t charge;      // TODO(opt): Only allow uint32_t?
        size_t key_length;
        uint32_t refs;
        uint32_t hash;      // Hash of key(); used for fast sharding and comparisons
        char key_data[1];   // Beginning of key

        Slice key() const {
            // For cheaper lookups, we allow a temporary Handle object
            // to store a pointer to a key in "value".
            if (next == this) {
                return *(reinterpret_cast<Slice*>(value));
            } else {
                return Slice(key_data, key_length);
            }
        }
    };
    ```

### HandleTable ###

1. HandleTable是leveldb中hashtable的实现，使用链地址法解决hash冲突的问题。
2. HandleTable在操作LRUHandle时只会操作next_hash字段。
3. 插入的实现：
    ```c
    // 当h在HandleTable中时，会用新的h替代旧的old,然后返回旧的，否则会插入到HandleTable中
    LRUHandle* HandleTable::Insert(LRUHandle* h) {
        // 二级指针的妙用，一个指针相当于存储了两个值：
        //  1. 指向key为h->key()的entry的指针
        //  2. 通过上面的指针获取指向的entry
        LRUHandle** ptr = FindPointer(h->key(), h->hash);
        LRUHandle* old = *ptr;
        h->next_hash = (old == NULL ? NULL : old->next_hash);
        *ptr = h;
        if (old == NULL) {
            ++elems_;
            if (elems_ > length_) {
                // Since each cache entry is fairly large, we aim for a small
                // average linked list length (<= 1).
                Resize();
            }
        }
        return old;
    }
    ```

### LRUCache ###

1. LRUCache是leveldb的LRUCache的具体实现。具体的定义如下：
    ```c
    class LRUCache {
    public:
        LRUCache();
        ~LRUCache();

        // Separate from constructor so caller can easily make an array of LRUCache
        void SetCapacity(size_t capacity) { capacity_ = capacity; }

        // Like Cache methods, but with an extra "hash" parameter.
        Cache::Handle* Insert(const Slice& key, uint32_t hash,
            void* value, size_t charge,
            void (*deleter)(const Slice& key, void* value));
        Cache::Handle* Lookup(const Slice& key, uint32_t hash);
        void Release(Cache::Handle* handle);
        void Erase(const Slice& key, uint32_t hash);

    private:
        // 这三个方法在操作的时候只会操作LRUHandle的next和prev字段，分工明确
        void LRU_Remove(LRUHandle* e);
        void LRU_Append(LRUHandle* e);
        void Unref(LRUHandle* e);

        // Initialized before use.
        size_t capacity_;

        // 互斥锁
        port::Mutex mutex_;
        size_t usage_;

        // 双向循环链表的头，lru.prev指向链表的开始，lru.next指向最后的节点
        LRUHandle lru_;

        // HandleTable主要用于索引
        HandleTable table_;
    };
    ```

### SharedLRUCache ###

1. 本来LRUCache的实现到LRUCache就ok了，但是如果整个LRUCache使用一个Metux的话，锁的力度就会比较大，因此添加了SharedLRUCache，相当于给LRUCache建立了索引(二级索引)。
2. SharedLRUCache才是Cache的实现类。具体的定义如下：
    ```c
    class ShardedLRUCache : public Cache {
    private:
        // 默认分16个分区
        LRUCache shard_[kNumShards];
        port::Mutex id_mutex_;
        uint64_t last_id_;

        static inline uint32_t HashSlice(const Slice& s) {
            return Hash(s.data(), s.size(), 0);
        }

        // shared的算法：hash值的高4位
        static uint32_t Shard(uint32_t hash) {
            return hash >> (32 - kNumShardBits);
        }
        // .....
    };
    ```
