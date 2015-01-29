Channel Introduction
===============

** Channel Introduction **

1. Channel用于在ByteBuffer和位于通道另一侧的实体(通常是一个文件或套接字)之间有效地传递数据。
2. Channel是访问I/O服务的导管。I/O可以分为广义的两大类：File I/O和Stream I/O。那么相应地有两种类型的通道，它们是文件(file)通道和套接字(socket)通道。在java.nio.channel下面有FileChannel和三个socket通道类：SocketChannel, ServerSocketChannel和DatagramChannel。

** Channel的创建 **

1. Socket通道有可以直接创建新socket通道的工厂方法。但是一个FileChannel对象却只能通过在一个打开的RandomAccessFile,FileInputStream或FileOutputStream对象上调用getChannel()方法来获取。
    ```java
    SocketChannel sc = SocketChannel.open( );
    sc.connect (new InetSocketAddress ("somehost", someport));

    ServerSocketChannel ssc = ServerSocketChannel.open( );
    ssc.socket( ).bind (new InetSocketAddress (somelocalport));

    DatagramChannel dc = DatagramChannel.open( );

    RandomAccessFile raf = new RandomAccessFile ("somefile", "r");
    FileChannel fc = raf.getChannel( );
    ```
2. 通道可以以阻塞（blocking）或非阻塞（nonblocking）模式运行。非阻塞模式的通道永远不会让调用的线程休眠。请求的操作要么立即完成，要么返回一个结果表明未进行任何操作。**只有面向流的（stream-oriented）的通道，如sockets和pipes才能使用非阻塞模式。**
3. 。Scatter/Gather是一个简单却强大的概念，它是指在多个缓冲区上实现一个简单的I/O操作。对于一个write操作而言，数据是从几个缓冲区按顺序抽取（称为gather）并沿着通道发送的。。对于read操作而言，从通道读取的数据会按顺序被散布（称为scatter）到多个缓冲区，将每个缓冲区填满直至通道中的数据或者缓冲区的最大空间被消耗完。

** 文件Channel **

1. 文件通道总是阻塞式的，因此不能被置于非阻塞模式。
2. 现代操作系统都有复杂的缓存和预取机制，使得本地磁盘I/O操作延迟很少。网络文件系统一般而言延迟会多些，不过却也因该优化而受益。面向流的I/O的非阻塞范例对于面向文件的操作并无多大意义，这是由文件I/O本质上的不同性质造成的。
3. 对于文件I/O，最强大之处在于异步I/O（asynchronous I/O），它允许一个进程可以从操作系统请求一个或多个I/O操作而不必等待这些操作的完成。发起请求的进程之后会收到它请求的I/O操作已完成的通知。
4. FileChannel对象是线程安全（thread-safe）的。多个进程可以在同一个实例上并发调用方法而不会引起任何问题.

** 文件锁 **

1. 有关FileChannel实现的文件锁定模型的一个重要注意项是：**锁的对象是文件而不是通道或线程**，这意味着文件锁不适用于判优同一台Java虚拟机上的多个线程发起的访问。
2. 如果一个线程在某个文件上获得了一个独占锁，然后第二个线程利用一个单独打开的通道来请求该文件的独占锁，那么第二个线程的请求会被批准。但如果这两个线程运行在不同的Java虚拟机上，那么第二个线程会阻塞，因为锁最终是由操作系统或文件系统来判优的并且几乎总是在进程级而非线程级上判优。锁都是与一个文件关联的，而不是与单个的文件句柄或通道关联。
3. 锁与文件关联，而不是与通道关联。我们使用锁来判优外部进程，而不是判优同一个Java虚拟机上的线程。
4. 一个FileLock对象创建之后即有效，直到它的release( )方法被调用或它所关联的通道被关闭或Java虚拟机关闭时才会失效。
5. 尽管一个FileLock对象是与某个特定的FileChannel实例关联的，它所代表的锁却是与一个底层文件关联的，而不是与通道关联。因此，如果您在使用完一个锁后而不释放它的话，可能会导致冲突或者死锁。请小心管理文件锁以避免出现此问题。一旦您成功地获取了一个文件锁，如果随后在通道上出现错误的话，请务必释放这个锁。

** 内存映射 **

1. FileChannel类提供了一个名为map( )的方法，该方法可以在一个打开的文件和一个特殊类型的ByteBuffer之间建立一个虚拟内存映射.
2. 在FileChannel上调用map( )方法会创建一个由磁盘文件支持的虚拟内存映射（virtual memory mapping）并在那块虚拟内存空间外部封装一个MappedByteBuffer对象.
3. 通过内存映射机制来访问一个文件会比使用常规方法读写高效得多，甚至比使用通道的效率都高。因为不需要做明确的系统调用，那会很消耗时间。更重要的是，操作系统的虚拟内存可以自动缓存内存页（memory page）。这些页是用系统内存来缓存的，所以不会消耗Java虚拟机内存堆（memory heap）。
4. 文件映射可以是可写的或只读的。前两种映射模式MapMode.READ_ONLY和MapMode.READ_WRITE意义是很明显的，它们表示您希望获取的映射只读还是允许修改映射的文件。请求的映射模式将受被调用map( )方法的FileChannel对象的访问权限所限制。如果通道是以只读的权限打开的而您却请求MapMode.READ_WRITE模式，那么map( )方法会抛出一个NonWritableChannelException异常；如果您在一个没有读权限的通道上请求MapMode.READ_ONLY映射模式，那么将产生NonReadableChannelException异常.
5. MapMode.PRIVATE表示您想要一个写时拷贝（copy-on-write）的映射。这意味着您通过put( )方法所做的任何修改都会导致产生一个私有的数据拷贝并且该拷贝中的数据只有MappedByteBuffer实例可以看到。该过程不会对底层文件做任何修改，而且一旦缓冲区被施以垃圾收集动作（garbage collected），那些修改都会丢失。尽管写时拷贝的映射可以防止底层文件被修改，您也必须以read/write权限来打开文件以建立MapMode.PRIVATE映射。只有这样，返回的MappedByteBuffer对象才能允许使用put( )方法。
6. 所有的MappedByteBuffer对象都是直接的，这意味着它们占用的内存空间位于Java虚拟机内存堆之外（并且可能不会算作Java虚拟机的内存占用，不过这取决于操作系统的虚拟内存模型）。
7. 当我们为一个文件建立虚拟内存映射之后，文件数据通常不会因此被从磁盘读取到内存（这取决于操作系统）。
8. 对于映射缓冲区，虚拟内存系统将根据您的需要来把文件中相应区块的数据读进来。这个页验证或防错过程需要一定的时间，因为将文件数据读取到内存需要一次或多次的磁盘访问。某些场景下，您可能想先把所有的页都读进内存以实现最小的缓冲区访问延迟。如果文件的所有页都是常驻内存的，那么它的访问速度就和访问一个基于内存的缓冲区一样了。
9. MapMode.PRIVATE表示您想要一个写时拷贝（copy-on-write）的映射。这意味着您通过put( )方法所做的任何修改都会导致产生一个私有的数据拷贝并且该拷贝中的数据只有MappedByteBuffer实例可以看到。该过程不会对底层文件做任何修改，而且一旦缓冲区被施以垃圾收集动作（garbage collected），那些修改都会丢失。尽管写时拷贝的映射可以防止底层文件被修改，您也必须以read/write权限来打开文件以建立MapMode.PRIVATE映射。只有这样，返回的MappedByteBuffer对象才能允许使用put( )方法。
10. 如果映射是以MapMode.READ_ONLY或MAP_MODE.PRIVATE模式建立的，那么调用force( )方法将不起任何作用，因为永远不会有更改需要应用到磁盘上（但是这样做也是没有害处的）。

** Socket通道 **

1. socket通道类可以运行非阻塞模式并且是可选择的.
2. 请注意DatagramChannel和SocketChannel实现定义读和写功能的接口而ServerSocketChannel不实现。ServerSocketChannel负责监听传入的连接和创建新的SocketChannel对象，它本身从不传输数据。
3. 全部socket通道类（DatagramChannel、SocketChannel和ServerSocketChannel）在被实例化时都会创建一个对等socket对象。这些是我们所熟悉的来自java.net的类（Socket、ServerSocket和DatagramSocket），它们已经被更新以识别通道。对等socket可以通过调用socket( )方法从一个通道上获取。此外，这三个java.net类现在都有getChannel( )方法。
4. 虽然每个socket通道（在java.nio.channels包中）都有一个关联的java.net socket对象，却并非所有的socket都有一个关联的通道。如果您用传统方式（直接实例化）创建了一个Socket对象，它就不会有关联的SocketChannel并且它的getChannel( )方法将总是返回null。
5. 如果您选择在ServerSocket上调用accept( )方法，那么它会同任何其他的ServerSocket表现一样的行为：总是阻塞并返回一个java.net.Socket对象。如果您选择在ServerSocketChannel上调用accept( )方法则会返回SocketChannel类型的对象，返回的对象能够在非阻塞模式下运行
6. 虽然每个SocketChannel对象都会创建一个对等的Socket对象，反过来却不成立。直接创建的Socket对象不会关联SocketChannel对象，它们的getChannel( )方法只返回null。

** Pipe **

1. 管道可以被用来仅在同一个Java虚拟机内部传输数据。
