skiplist实现分析
============

## skiplist简介 ##

1. Skip list(跳跃表）是一种可以代替平衡树的数据结构。Skip lists应用概率保证平衡，平衡树采用严格的旋转（比如平衡二叉树有左旋右旋）来保证平衡，因此Skip list比较容易实现，而且相比平衡树有着较高的运行效率。
2. skip list在空间上也比较节省。一个节点平均只需要1.333个指针（甚至更少），并且不需要存储保持平衡的变量。
3. 在Leveldb中，skip list是实现memtable的核心数据结构，memtable的KV数据都存储在skip list中。redis的sorted set也使用了skiplist实现。

## skiplist源码分析 ##

skiplist的源代码在db/skiplist.h

### 线程安全性 ###

1. 多个线程写入需要额外的同步，例如使用mutext.读需要确保在读的过程中skiplist不会被destroyed.
2. 读线程不需要任何额外的同步和锁.
3. 创建的节点(Node)在skiplist被destroy时会被销毁(arena内存管理),skiplist没有提供delete的接口。
4. 在Node被添加到skiplist后，Node里面的字段除了next指针可变(mutable),其它字段都是不可变的(immutable)

### Node结构体 ###

1. Node结构体的定义如下：
    ```c
    // Implementation details follow
    template<typename Key, class Comparator>
    struct SkipList<Key,Comparator>::Node {
    explicit Node(const Key& k) : key(k) { }

    Key const key;

    // Accessors/mutators for links.  Wrapped in methods so we can
    // add the appropriate barriers as necessary.
    Node* Next(int n) {
        assert(n >= 0);
        // Use an 'acquire load' so that we observe a fully initialized
        // version of the returned Node.
        return reinterpret_cast<Node*>(next_[n].Acquire_Load());
    }
    void SetNext(int n, Node* x) {
        assert(n >= 0);
        // Use a 'release store' so that anybody who reads through this
        // pointer observes a fully initialized version of the inserted node.
        next_[n].Release_Store(x);
    }

    // No-barrier variants that can be safely used in a few locations.
    Node* NoBarrier_Next(int n) {
        assert(n >= 0);
        return reinterpret_cast<Node*>(next_[n].NoBarrier_Load());
    }
    void NoBarrier_SetNext(int n, Node* x) {
        assert(n >= 0);
        next_[n].NoBarrier_Store(x);
    }

    private:
    // Array of length equal to the node height.  next_[0] is lowest level link.
    port::AtomicPointer next_[1];
    };
    ```
2. 从上面的结构体定义可以看出，Node含有两个field,key和next_[1].next_[1]主要是为了分配一段连续的内存空间。创建一个Node节点的代码如下(使用placement new分配了一段连续的空间)：
    ```c
    template<typename Key, class Comparator>
    typename SkipList<Key,Comparator>::Node*
    SkipList<Key,Comparator>::NewNode(const Key& key, int height) {
        char* mem = arena_->AllocateAligned(
            sizeof(Node) + sizeof(port::AtomicPointer) * (height - 1));
        return new (mem) Node(key);
    }
    ```
3. SkipList的定义：
    ```c
    template<typename Key, class Comparator>
    class SkipList {
    private:
        struct Node;

    public:
        // Create a new SkipList object that will use "cmp" for comparing keys,
        // and will allocate memory using "*arena".  Objects allocated in the arena
        // must remain allocated for the lifetime of the skiplist object.
        explicit SkipList(Comparator cmp, Arena* arena);

        // Insert key into the list.
        // REQUIRES: nothing that compares equal to key is currently in the list.
        void Insert(const Key& key);

        // Returns true iff an entry that compares equal to key is in the list.
        bool Contains(const Key& key) const;

        // Iteration over the contents of a skip list
        class Iterator {
            //.....
        };

    private:
        enum { kMaxHeight = 12 };

        // Immutable after construction
        Comparator const compare_;
        Arena* const arena_;    // Arena used for allocations of nodes

        Node* const head_;

        // Modified only by Insert().  Read racily by readers, but stale
        // values are ok.
        port::AtomicPointer max_height_;   // Height of the entire list

        inline int GetMaxHeight() const {
            return static_cast<int>(
                reinterpret_cast<intptr_t>(max_height_.NoBarrier_Load()));
        }

        // Read/written only by Insert().
        Random rnd_;

        Node* NewNode(const Key& key, int height);
        int RandomHeight();
        bool Equal(const Key& a, const Key& b) const { return (compare_(a, b) == 0); }

        // Return true if key is greater than the data stored in "n"
        bool KeyIsAfterNode(const Key& key, Node* n) const;

        // Return the earliest node that comes at or after key.
        // Return NULL if there is no such node.
        //
        // If prev is non-NULL, fills prev[level] with pointer to previous
        // node at "level" for every level in [0..max_height_-1].
        Node* FindGreaterOrEqual(const Key& key, Node** prev) const;

        // Return the latest node with a key < key.
        // Return head_ if there is no such node.
        Node* FindLessThan(const Key& key) const;

        // Return the last node in the list.
        // Return head_ if list is empty.
        Node* FindLast() const;

        // No copying allowed
        SkipList(const SkipList&);
        void operator=(const SkipList&);
    };
    ```
4. 创建SkipList的方法(创建了一个head头节点)：
    ```c
    template<typename Key, class Comparator>
    SkipList<Key,Comparator>::SkipList(Comparator cmp, Arena* arena)
        : compare_(cmp),
        arena_(arena),
        head_(NewNode(0 /* any key will do */, kMaxHeight)),
        max_height_(reinterpret_cast<void*>(1)),
        rnd_(0xdeadbeef) {
            for (int i = 0; i < kMaxHeight; i++) {
                head_->SetNext(i, NULL);
            }
    }
    ```
5. 值得学习的代码片段：
    ```c
    template<typename Key, class Comparator>
    typename SkipList<Key,Comparator>::Node* SkipList<Key,Comparator>::FindGreaterOrEqual(const Key& key, Node** prev)
        const {
            Node* x = head_;
            int level = GetMaxHeight() - 1;
            while (true) {
                Node* next = x->Next(level);
                if (KeyIsAfterNode(key, next)) {
                    // Keep searching in this list
                    x = next;
                } else {
                    if (prev != NULL) prev[level] = x;
                    if (level == 0) {
                        return next;
                    } else {
                        // Switch to next list
                        level--;
                    }
                }
            }
        }

    template<typename Key, class Comparator>
    typename SkipList<Key,Comparator>::Node*
    SkipList<Key,Comparator>::FindLessThan(const Key& key) const {
        Node* x = head_;
        int level = GetMaxHeight() - 1;
        while (true) {
            assert(x == head_ || compare_(x->key, key) < 0);
            Node* next = x->Next(level);
            if (next == NULL || compare_(next->key, key) >= 0) {
                if (level == 0) {
                    return x;
                } else {
                    // Switch to next list
                    level--;
                }
            } else {
                x = next;
            }
        }
    }
    
    template<typename Key, class Comparator>
    typename SkipList<Key,Comparator>::Node* SkipList<Key,Comparator>::FindLast()
        const {
            Node* x = head_;
            int level = GetMaxHeight() - 1;
            while (true) {
                Node* next = x->Next(level);
                if (next == NULL) {
                    if (level == 0) {
                        return x;
                    } else {
                        // Switch to next list
                        level--;
                    }
                } else {
                    x = next;
                }
            }
        }
    ```
