Buffer Introduction
=========

**Buffer简介**

1. 一个buffer对象是固定数量的数据的容器。其作用是一个存储器，在这里数据可被存储并在之后用于检索。
2. Buffer与Channel紧密联系。Channel是I/O传输发生时通过的入口，而Buffer是这些数据传输的来源或目标。

**Buffer属性**

1. 容量(Capacity): Buffer能够容纳的数据元素的最大数量。这个容量在缓冲区创建时被设定，并且永远不能被改变。
2. 上界(Limit): Buffer的第一个不能被读或者写的元素。或者说是Buffer中现存元素的计数。上界属性指明了缓冲区有效内容的末端.(有效内容相对于读或者写).
3. 位置(Position): 下一个要被读或者写的元素的索引。位置会自动由相应的get()或者put()函数更新。
4. 标记(Mark): 一个备忘位置。调用mark()来设置mark = Position.调用reset()设定Position=mark.标记在设定前是未定义的。
5. 这四个属性之间的关系是：0 <= mark <= position <= limit <= capacity。

**常用函数**

1. flip()函数将一个能够继续添加数据元素的填充状态的缓冲区翻转成一个准备读出元素的释放状态.
2. Rewind()函数与flip()相似，但不影响上界属性。它只是将位置值设回0。您可以使用rewind()后退，重读已经被翻转的缓冲区中的数据.
3. equals()方法。比较两个Buffer相等的重要条件是：
    - 两个对象类型相同。包含不同数据类型的buffer永远不会相等，而且buffer绝不会等于非buffer对象。
    - 两个对象都剩余同样数量的元素。Buffer的容量不需要相同，而且缓冲区中剩余数据的索引也不必相同。但每个缓冲区中剩余元素的数目（从位置到上界）必须相同。
    - 在每个缓冲区中应被Get()函数返回的剩余数据元素序列必须一致。
4. duplicate()函数创建了一个与原始缓冲区相似的新缓冲区。两个缓冲区共享数据元素，拥有同样的容量，但每个缓冲区拥有各自的位置，上界和标记属性。对一个缓冲区内的数据元素所做的改变会反映在另外一个缓冲区上。这一副本缓冲区具有与原始缓冲区同样的数据视图。如果原始的缓冲区为只读，或者为直接缓冲区，新的缓冲区将继承这些属性。