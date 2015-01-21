java调优参数
===============

1. 常见的和java内参调优相关的参数
    <table>
        <tbody>
            <tr>
                <td>argument</td>
                <td>explain</td>
                <td>example</td>
            </tr>
            <tr>
                <td>-Xms</td>
                <td>初始堆大小</td>
                <td>-Xms256m</td>
            </tr>
            <tr>
                <td>-Xmx</td>
                <td>最大堆大小</td>
                <td>-Xmx512m</td>
            </tr>
            <tr>
                <td>-Xmn</td>
                <td>新生代大小</td>
                <td>-Xmx128m</td>
            </tr>
            <tr>
                <td>-Xss</td>
                <td>线程栈大小</td>
                <td>-Xss1m</td>
            </tr>
            <tr>
                <td>-XX:NewRatio</td>
                <td>老年代/新生代</td>
                <td>-XX:NewRatio=2</td>
            </tr>
            <tr>
                <td>-XX:SurvivorRation</td>
                <td>Eden/Survivor</td>
                <td>-XX:SurvivorRation=8</td>
            </tr>
            <tr>
                <td>-XX:PermSize</td>
                <td>永久代初始大小</td>
                <td>-XX:PermSize=20m</td>
            </tr>
            <tr>
                <td>-XX:MaxPermSize</td>
                <td>永久代最大值</td>
                <td>-XX:MaxPermSize=50m</td>
            </tr>
            <tr>
                <td>-XX:PrintGcDetails</td>
                <td>打印gc信息</td>
                <td>-XX:PrintGcDetails</td>
            </tr>
            <tr>
                <td>-XX:+HeapDumpOnOutOfMemoryError</td>
                <td>内存溢出时dump快照</td>
                <td>-XX:+HeapDumpOnOutOfMemoryError</td>
            </tr>
            <tr>
                <td>-XX:HeapDumpPath</td>
                <td>dump文件路径</td>
                <td>-XX:HeapDumpPath=/tmp/dump</td>
            </tr>
        </tbody>
    </table>

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
