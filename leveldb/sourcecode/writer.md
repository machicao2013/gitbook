写日志的Writer实现
====================

1. Writer的实现文件位db/log_writer.h和db/log_writer.cc中。
2. leveldb中所有的写操作都必须先成功的append到操作日志，然后才能在更新memtable.这么做有两个好处：
    - 可以将随机的写IO变成append，极大的提高写磁盘速
    - 防止在节点down机导致内存数据丢失，造成数据丢失
3. log格式的详细描述见：doc/log_format.txt,简单的几个特征是：
    - log文件是由32KB的块组成，唯一的例外是在文件的末尾可能包含半块
    - 每一块都包含多条记录(records)
    - 每条record的格式如下：
        ```
        record :=
            checksum: uint32    // crc32c of type and data[] ; little-endian
            length: uint16      // little-endian
            type: uint8     // One of FULL, FIRST, MIDDLE, LAST
            data: uint8[length]
        ```
    - 每条记录不会起始于块的后6个字节,这少于7个的字节必须填充0
3. 记录的类型：
    - FULL: 一个record包含一条完整的用户数据
    - FIRST: 下面的三个回出现在record不能包含一条完整的用户数据，FIRST表示第一条
    - MIDDLE: FIRST代表中间的
    - LAST: LAST代表最后的数据
4. AddRecord接口的实现：
    ```c
    Status Writer::AddRecord(const Slice& slice) {
        const char* ptr = slice.data();
        size_t left = slice.size();

        // Fragment the record if necessary and emit it.  Note that if slice
        // is empty, we still want to iterate once to emit a single
        // zero-length record
        Status s;
        bool begin = true;
        do {
            const int leftover = kBlockSize - block_offset_;
            assert(leftover >= 0);
            if (leftover < kHeaderSize) {
                // Switch to a new block
                if (leftover > 0) {
                    // Fill the trailer (literal below relies on kHeaderSize being 7)
                    // 剩余空间小于7,则填充0
                    assert(kHeaderSize == 7);
                    dest_->Append(Slice("\x00\x00\x00\x00\x00\x00", leftover));
                }
                block_offset_ = 0;
            }

            // Invariant: we never leave < kHeaderSize bytes in a block.
            assert(kBlockSize - block_offset_ - kHeaderSize >= 0);

            const size_t avail = kBlockSize - block_offset_ - kHeaderSize;
            const size_t fragment_length = (left < avail) ? left : avail;

            // 判断记录的类型
            RecordType type;
            const bool end = (left == fragment_length);
            if (begin && end) {
                type = kFullType;
            } else if (begin) {
                type = kFirstType;
            } else if (end) {
                type = kLastType;
            } else {
                type = kMiddleType;
            }
            // 记录同步到文件中
            s = EmitPhysicalRecord(type, ptr, fragment_length);
            ptr += fragment_length;
            left -= fragment_length;
            begin = false;
        } while (s.ok() && left > 0);
        return s;
    }
    ```
5. 在Writer中有一个变量type_crc_[kMaxRecordType +1],这主要是以空间换取时间的一种方式，在每次读取到数据的时候就更新crc的值.
