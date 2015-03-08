memtable分析
===============

memtable是leveldb中非常重要的一块，使用skiplist存储kv节点。与memtable相关的实现文件有memtable.h,memtable.cc,dbformat.h,dbformat.cc.

## 与memtable相关的key ##

### InternalKey ###

1. InternalKey是由user_key(Slice),sequence number(7 bytes)和value type(1 byte)组合而成的一个字符串。在向memtable中添加数据的时候，会拼接一个InternalKey，然后将VarintLength(InternalKey) + InternalKey + VarintLength(value) + value拼接在一起，作为key存储到skiplist中。

### ParsedInternalKey ###

1. ParsedInternalKey是InternalKey的一个内部结构的展示，具体的定义如下：
    ```c
    struct ParsedInternalKey {
        Slice user_key;
        SequenceNumber sequence;
        ValueType type;

        ParsedInternalKey() { }  // Intentionally left uninitialized (for speed)
        ParsedInternalKey(const Slice& u, const SequenceNumber& seq, ValueType t)
            : user_key(u), sequence(seq), type(t) { }
        std::string DebugString() const;
    };
    ```
2. InternalKey和ParsedInternalKey之间转换的两个函数如下：
    ```c
    // InternalKey to ParsedInternalKey
    inline bool ParseInternalKey(const Slice& internal_key, ParsedInternalKey* result) {
        const size_t n = internal_key.size();
        if (n < 8) return false;
        uint64_t num = DecodeFixed64(internal_key.data() + n - 8);
        unsigned char c = num & 0xff;
        result->sequence = num >> 8;
        result->type = static_cast<ValueType>(c);
        result->user_key = Slice(internal_key.data(), n - 8);
        return (c <= static_cast<unsigned char>(kTypeValue));
    }
    // ParsedInternalKey to InternalKey
    void AppendInternalKey(std::string* result, const ParsedInternalKey& key) {
        result->append(key.user_key.data(), key.user_key.size());
        PutFixed64(result, PackSequenceAndType(key.sequence, key.type));
    }
    ```

### LookupKey ###

1. LookupKey是memtable的查询接口所使用的key.
2. LookupKey的定义如下：
    ```c
    class LookupKey {
    public:
        // Initialize *this for looking up user_key at a snapshot with
        // the specified sequence number.
        LookupKey(const Slice& user_key, SequenceNumber sequence);

        ~LookupKey();

        // Return a key suitable for lookup in a MemTable.
        Slice memtable_key() const { return Slice(start_, end_ - start_); }

        // Return an internal key (suitable for passing to an internal iterator)
        Slice internal_key() const { return Slice(kstart_, end_ - kstart_); }

        // Return the user key
        Slice user_key() const { return Slice(kstart_, end_ - kstart_ - 8); }

    private:
        // We construct a char array of the form:
        //    klength  varint32               <-- start_
        //    userkey  char[klength]          <-- kstart_
        //    tag      uint64
        //                                    <-- end_
        // The array is a suitable MemTable key.
        // The suffix starting with "userkey" can be used as an InternalKey.
        const char* start_;
        const char* kstart_;
        const char* end_;
        char space_[200];      //存储格式：VarintLength() + (user_key，sequence,kValueTypeForSeek)

        // No copying allowed
        LookupKey(const LookupKey&);
        void operator=(const LookupKey&);
    };
    ```
3. 有LookupKey可以快速的转换成InternalKey，MemTableKey.

## MemTable ##

1. 在Leveldb中，所有内存中的KV数据都存储在Memtable中，物理disk则存储在SSTable中。在系统运行过程中，如果Memtable中的数据占用内存到达指定值(Options.write_buffer_size)，则Leveldb就自动将Memtable转换为Immutable Memtable，并自动生成新的Memtable，也就是Copy-On-Write机制了。
2. Immutable Memtable则被新的线程Dump到磁盘中，Dump结束则该Immutable Memtable就可以释放了。因名知意，Immutable Memtable是只读的。 所以可见，最新的数据都是存储在Memtable中的，Immutable Memtable和物理SSTable则是某个时点的数据。
3. Memtable提供了写入KV记录，删除以及读取KV记录的接口，但是事实上Memtable并不执行真正的删除操作,删除某个Key的Value在Memtable内是作为插入一条记录实施的，但是会打上一个Key的删除标记，真正的删除操作在后面的 Compaction过程中，lazy delete。
4. MemTable的定义如下：
    ```c
    class MemTable {
    public:
        // MemTables are reference counted.  The initial reference count
        // is zero and the caller must call Ref() at least once.
        // 引用计数
        explicit MemTable(const InternalKeyComparator& comparator);

        // Increase reference count.
        void Ref() { ++refs_; }

        // Drop reference count.  Delete if no more references exist.
        void Unref() {
            --refs_;
            assert(refs_ >= 0);
            if (refs_ <= 0) {
                delete this;
            }
        }
        //  .....
    private:
        ~MemTable();  // Private since only Unref() should be used to delete it

        struct KeyComparator {
            const InternalKeyComparator comparator;
            explicit KeyComparator(const InternalKeyComparator& c) : comparator(c) { }
            int operator()(const char* a, const char* b) const;
        };

        typedef SkipList<const char*, KeyComparator> Table;

        KeyComparator comparator_;
        int refs_;
        Arena arena_;
        Table table_;   // 数据实际存储在SkipList中
    }

    ```
2. MemTable的Add接口实现：
    ```c
    void MemTable::Add(SequenceNumber s, ValueType type,
                       const Slice& key,
                       const Slice& value) {
      size_t key_size = key.size();
      size_t val_size = value.size();
      size_t internal_key_size = key_size + 8;
      // internal_key: | User key (string) | sequence number (7 bytes) | value type (1 byte) |
      const size_t encoded_len =
          VarintLength(internal_key_size) + internal_key_size +
          VarintLength(val_size) + val_size;
      char* buf = arena_.Allocate(encoded_len);
      char* p = EncodeVarint32(buf, internal_key_size);
      memcpy(p, key.data(), key_size);
      p += key_size;
      EncodeFixed64(p, (s << 8) | type);
      p += 8;
      p = EncodeVarint32(p, val_size);
      memcpy(p, value.data(), val_size);
      assert((p + val_size) - buf == encoded_len);
      // 将key和value打包后放到skiplist中
      table_.Insert(buf);
    }
    ```
3. MemTable的Get接口：
    ```c
    // 根据LookupKey查找数据
    bool MemTable::Get(const LookupKey& key, std::string* value, Status* s) {
        Slice memkey = key.memtable_key(); //根据LookupKey获取MemTableKey
        Table::Iterator iter(&table_);
        iter.Seek(memkey.data()); //根据MemTableKey在SkipList中超找
        if (iter.Valid()) {
            const char* entry = iter.key();
            uint32_t key_length;
            const char* key_ptr = GetVarint32Ptr(entry, entry+5, &key_length);
            if (comparator_.comparator.user_comparator()->Compare(
                    Slice(key_ptr, key_length - 8),
                    key.user_key()) == 0) {
                // Correct user key
                const uint64_t tag = DecodeFixed64(key_ptr + key_length - 8);
                switch (static_cast<ValueType>(tag & 0xff)) {
                case kTypeValue: {
                    //从SkipList的Node值中获取value
                    Slice v = GetLengthPrefixedSlice(key_ptr + key_length);
                    value->assign(v.data(), v.size());
                    return true;
                }
                case kTypeDeletion:
                    *s = Status::NotFound(Slice());
                    return true;
                }
            }
        }
        return false;
    }
    ```
