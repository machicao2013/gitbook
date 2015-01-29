import java.lang.System;
import java.lang.Thread;

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
public class GcArgument {
    public static void main(String[] args) {
        new GcArgument().doGcArgument();
    }

    public void doGcArgument(){
        Integer M = new Integer(1024 * 1024 * 1);  //单位, 兆(M)
        byte[] bytes = new byte[1 * M]; //申请 1M 大小的内存空间
        bytes = null;  //断开引用链
        System.gc();   //通知 GC 收集垃圾
        System.out.println();
        bytes = new byte[1 * M];  //重新申请 1M 大小的内存空间
        bytes = new byte[1 * M];  //再次申请 1M 大小的内存空间
        System.gc();
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            System.out.println("============");
        }
        System.out.println();
    }
}
