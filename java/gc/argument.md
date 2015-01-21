java调优参数
===============

1. 常见的和java内参调优相关的参数

   | argument                        | explain           |  example                        |
   | ------------------------------- |:-----------------:|:-------------------------------:|
   | -Xms                            |初始堆大小         | -Xms256m                        |
   | -Xmx                            |最大堆大小         | -Xmx512m                        |
   | -Xmn                            |新生代大小         | -Xmx128m                        |
   | -Xss                            |线程栈大小         | -Xss1m                          |
   | -XX:NewRatio                    |老年代/新生代      | -XX:NewRatio=2                  |
   | -XX:SurvivorRation              |Eden/Survivor      | -XX:SurvivorRation=8            |
   | -XX:PermSize                    |永久代初始大小     | -XX:PermSize=20m                |
   | -XX:MaxPermSize                 |永久代最大值       | -XX:MaxPermSize=50m             |
   | -XX:PrintGcDetails              |打印gc信息         | -XX:PrintGcDetails              |
   | -XX:+HeapDumpOnOutOfMemoryError |内存溢出时dump快照 | -XX:+HeapDumpOnOutOfMemoryError |
   | -XX:HeapDumpPath                |dump文件路径       | -XX:HeapDumpPath=/tmp/dump      |

2. 一个例子
    ```java
    import java.lang.System;

    /**
     *  -Xms60m
     *  -Xmx60m
     *  -Xmn20m
     *  -XX:NewRatio=2 ( 若 Xms = Xmx, 并且设定了 Xmn, 那么该项配置就不需要配置了 )
     *  -XX:SurvivorRatio=8
     *  -XX:PermSize=30m
     *  -XX:MaxPermSize=30m
     *  -XX:+PrintGCDetails
     **/
    public class Test {
        public static void main(String[] args) {
            new Test().doTest();
        }

        public void doTest(){
            Integer M = new Integer(1024 * 1024 * 1);  //单位, 兆(M)
            byte[] bytes = new byte[1 * M]; //申请 1M 大小的内存空间
            bytes = null;  //断开引用链
            System.gc();   //通知 GC 收集垃圾
            System.out.println();
            bytes = new byte[1 * M];  //重新申请 1M 大小的内存空间
            bytes = new byte[1 * M];  //再次申请 1M 大小的内存空间
            System.gc();
            System.out.println();
        }
    }
    ```
    ```shell
    #!/bin/bash
    javac Test.java
    java -Xms60m -Xmx60m -Xmn20m -XX:NewRatio=2 -XX:SurvivorRatio=8 -XX:PermSize=30m -XX:MaxPermSize=30m -XX:+PrintGCDetails Test
    rm Test.class
    ```
