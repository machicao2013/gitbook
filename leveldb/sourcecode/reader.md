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

    ```
