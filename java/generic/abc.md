java基础知识
=========

** equals方法 **
1. Java语言规范要求equals方法具有下面的特性：
    1. 自反性：对任何非空引用x，x.equals(x)应该返回true。
    2. 对称性：对任何引用x和y，当且仅当y.equals(x)返回true，x.equals(y)也应该返回true。
    3. 传递性：对于任何引用x、y和z，如果x.equals(y)返回true，y.equals(z)返回true，则x.equals(z)也应该返回true
    4. 一致性：如果x和y引用的对象没有发生变化，反复调用x.equals(y)应该返回同样的结果。
2. 一个标准的equals的实现：
  ```java
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        EqualMethodTest other = (EqualMethodTest) obj;
        if (age != other.age)
            return false;
        if (Double.doubleToLongBits(height) != Double.doubleToLongBits(other.height))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }
  ```

  ** hashCode方法 **
  1. equals方法与hashCode方法的定义必须一致：如果x.equals(y)返回true，那么x.hashCode()就必须与y.hashCode()具有相同的值。
  2. 如果重新定义equals方法，就必须从新定义hashCode方法，以便用户可以将对象插入到散列表中。
