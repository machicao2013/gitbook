java的枚举
=========

1. java的枚举类型背后的基本思想：它们就是通过共有的静态final域为每个枚举常量导出实例的类。其构造方法为private，枚举类型是真正的final。因为客户端既不能创建枚举类型的实例，也不能对它进行扩展，因此很有可能没有实例，而只有声明过的枚举常量。
2. 所有的枚举类型是java.lang.Enum<E>类型的子类，实现了Comparable<E>, Serializable接口。枚举天生是不可变的，因此所有的域都应该是final的，它们可以是公有的，但最好将它们做成是私有的，然后提供共有的访问方法。
3. 一个例子：
    ```java
    enum Operation {
        PLUS("+") {
            double apply(double x, double y) {
                return x + y;
            }
        },
        MINUS("-") {
            double apply(double x, double y) {
                return x - y;
            }
        },
        TIMES("*") {
            double apply(double x, double y) {
                return x * y;
            }
        },
        DIVIDE("/") {
            double apply(double x, double y) {
                return x / y;
            }
        };

        private final String symbol;

        // is private
        Operation(String symbol) {
            this.symbol = symbol;
        }

        public String toString() {
            return this.symbol;
        }

        abstract double apply(double x, double y);
    }
    ```
