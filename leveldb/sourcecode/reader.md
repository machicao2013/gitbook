Reader的分析
=============

1. 读比写入要复杂的多，需要检查checksum,检查文件是否损坏的等情况。主要实现在db/log_reader.h和db/log_reader.cc.
2. Reader接口主要使用到了两个类，一个是读取log文件的SequentialFile,一个是汇报错误的Reporter.
3. SequentialFile有两个接口：
    - Status Read(size_t n, Slice* result, char* scratch);
    - Status Skip(uint64_t n);
4. 解释以下3中的Read接口问什么有result参数后还需要scratch参数：Slice是不负责底层的存储的，也就是说存储是有scratch指向的空间负责的。假设scratch = new char[1024],可能result.size()只等于256.
5. Reader提供的主要接口是bool ReadRecord(Slice* record, std::string* scratch);为什么有scratch见4的解释。
    ```c
    bool Reader::ReadRecord(Slice* record, std::string* scratch) {
        if (last_record_offset_ < initial_offset_) {
            if (!SkipToInitialBlock()) {
                return false;
            }
        }
        scratch->clear();
        record->clear();
        // 是否属于kFirstType
        bool in_fragmented_record = false;
        // Record offset of the logical record that we're reading
        // 0 is a dummy value to make compilers happy
        uint64_t prospective_record_offset = 0;

        Slice fragment;
        while (true) {
            // buffer_中存储着已经从磁盘中读取的记录
            uint64_t physical_record_offset = end_of_buffer_offset_ - buffer_.size();
            // 从磁盘读取一条记录，每次读取一块(32kb)，缓存到了backing_store_中，用buffer_指向剩下的记录
            // ReadPhysicalRecord函数还会做crc校验
            const unsigned int record_type = ReadPhysicalRecord(&fragment);
            switch (record_type) {
            case kFullType:
                if (in_fragmented_record) {
                    // Handle bug in earlier versions of log::Writer where
                    // it could emit an empty kFirstType record at the tail end
                    // of a block followed by a kFullType or kFirstType record
                    // at the beginning of the next block.
                    if (scratch->empty()) {
                        in_fragmented_record = false;
                    } else {
                        ReportCorruption(scratch->size(), "partial record without end(1)");
                    }
                }
                prospective_record_offset = physical_record_offset;
                scratch->clear();
                *record = fragment;
                last_record_offset_ = prospective_record_offset;
                return true;

            case kFirstType:
                if (in_fragmented_record) {
                    // Handle bug in earlier versions of log::Writer where
                    // it could emit an empty kFirstType record at the tail end
                    // of a block followed by a kFullType or kFirstType record
                    // at the beginning of the next block.
                    if (scratch->empty()) {
                        in_fragmented_record = false;
                    } else {
                        ReportCorruption(scratch->size(), "partial record without end(2)");
                    }
                }
                prospective_record_offset = physical_record_offset;
                scratch->assign(fragment.data(), fragment.size());
                in_fragmented_record = true;
                break;

            case kMiddleType:
                if (!in_fragmented_record) {
                    ReportCorruption(fragment.size(),
                        "missing start of fragmented record(1)");
                } else {
                    scratch->append(fragment.data(), fragment.size());
                }
                break;

            case kLastType:
                if (!in_fragmented_record) {
                    ReportCorruption(fragment.size(),
                        "missing start of fragmented record(2)");
                } else {
                    scratch->append(fragment.data(), fragment.size());
                    *record = Slice(*scratch);
                    last_record_offset_ = prospective_record_offset;
                    return true;
                }
                break;

            case kEof:
                if (in_fragmented_record) {
                    // This can be caused by the writer dying immediately after
                    // writing a physical record but before completing the next; don't
                    // treat it as a corruption, just ignore the entire logical record.
                    scratch->clear();
                }
                return false;

            case kBadRecord:
                if (in_fragmented_record) {
                    ReportCorruption(scratch->size(), "error in middle of record");
                    in_fragmented_record = false;
                    scratch->clear();
                }
                break;

            default: {
                char buf[40];
                snprintf(buf, sizeof(buf), "unknown record type %u", record_type);
                ReportCorruption(
                    (fragment.size() + (in_fragmented_record ? scratch->size() : 0)),
                    buf);
                in_fragmented_record = false;
                scratch->clear();
                break;
            }
            }
        }
        return false;
    }
    ```
