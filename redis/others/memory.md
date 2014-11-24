redis内存简介
==============

redis的内存分配工作有其自己接管，主要是在zmalloc.h和zmalloc.c两个文件中实现。

redis可以使用jemalloc，tcmalloc或者利用glibc的内存分配器。为这三种分配器提供了统一的接口，加入了统计功能。

redis在分配内存的时候，除了分配请求大小的内存，还在该内存头部保存了该内存的大小。

##内存分配##
zmalloc的定义：
```c
void *zmalloc(size_t size) {
    void *ptr = malloc(size+PREFIX_SIZE);

    if (!ptr) zmalloc_oom_handler(size);
#ifdef HAVE_MALLOC_SIZE
    update_zmalloc_stat_alloc(zmalloc_size(ptr));
    return ptr;
#else
    *((size_t*)ptr) = size;
    update_zmalloc_stat_alloc(size+PREFIX_SIZE);
    return (char*)ptr+PREFIX_SIZE;
#endif
}
```

##内存释放##
在释放的时候，可以通过当前指针减去PREFIX_SIZE的大小找到内存块的起始地址
```c
void zfree(void *ptr) {
#ifndef HAVE_MALLOC_SIZE
    void *realptr;
    size_t oldsize;
#endif

    if (ptr == NULL) return;
#ifdef HAVE_MALLOC_SIZE
    update_zmalloc_stat_free(zmalloc_size(ptr));
    free(ptr);
#else
    realptr = (char*)ptr-PREFIX_SIZE;
    oldsize = *((size_t*)realptr);
    update_zmalloc_stat_free(oldsize+PREFIX_SIZE);
    free(realptr);
#endif
}
```

##获取常驻集(RSS: resident set size)##

RSS: number of pages the process has in real memory. This is just the pages which count towards text, data, or stack space. This does not include pages which have not been  demand-loaded  in, or which are swapped out.

计算方法：从/proc/%d/stat读取数据，得到第24个字段，假设值为rss，则最终rss = rss*PAGESIZE

```c
size_t zmalloc_get_rss(void) {
    int page = sysconf(_SC_PAGESIZE);
    size_t rss;
    char buf[4096];
    char filename[256];
    int fd, count;
    char *p, *x;

    snprintf(filename,256,"/proc/%d/stat",getpid());
    if ((fd = open(filename,O_RDONLY)) == -1) return 0;
    if (read(fd,buf,4096) <= 0) {
        close(fd);
        return 0;
    }
    close(fd);

    p = buf;
    count = 23; /* RSS is the 24th field in /proc/<pid>/stat */
    while(p && count--) {
        p = strchr(p,' ');
        if (p) p++;
    }
    if (!p) return 0;
    x = strchr(p,' ');
    if (!x) return 0;
    *x = '\0';

    rss = strtoll(p,NULL,10);
    rss *= page;
    return rss;
}
```

##获取子进程占用内存的大小##

该信息主要是从/proc/self/smaps中的Private_Dirty字段读取，Private_Dirty和Private_Clean,进程fork之后，开始内存是共享的，即从父进程那里继承的内存空间都是Private_Clean,运行一段时间之后,子进程对继承的内存空间做了修改，这部分内存就不能与父进程共享了，需要多占用，这部分就是Private_Dirty。

获取private dirty是在zmalloc_get_private_dirty中定义的
```c
size_t zmalloc_get_private_dirty(void) {
    char line[1024];
    size_t pd = 0;
    FILE *fp = fopen("/proc/self/smaps","r");

    if (!fp) return 0;
    while(fgets(line,sizeof(line),fp) != NULL) {
        if (strncmp(line,"Private_Dirty:",14) == 0) {
            char *p = strchr(line,'k');
            if (p) {
                *p = '\0';
                pd += strtol(line+14,NULL,10) * 1024;
            }
        }
    }
    fclose(fp);
    return pd;
}
```
##统计碎片率##

redis中对碎片率的定义是：Fragmentation = rss/allocates-bytes

是在函数zmalloc_get_fragmentation_ratio中完成
```c
float zmalloc_get_fragmentation_ratio(void) {
    return (float)zmalloc_get_rss()/zmalloc_used_memory();
}
```

