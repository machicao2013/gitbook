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

** 数组与列表 **

1. 数组与泛型相比，有两个重要的不同：
  1. 数组是协变的(convariant)。就是表示如果Sub为Super的子类型，那么数组类型Sub[]就是Super[]的子类型。相反，泛型则是不可变的(invariant):对于任意两个不同的类型Type1和Type2，List<Type1>即不是List<Type2>的子类型，也不是List<Type2>的超类型。
      ```java
     Object[] objs = new Object[10];
     String[] strs1 = new String[2];
     String[] strs2 = new String[10];
     objs = strs1;
     objs = strs2;
     System.out.println(strs1 instanceof Object[]); // true
     System.out.println(strs2 instanceof Object[]); // true

     List<Object> ol = new ArrayList<Long>();  // compile error
      ```
  2. 数组是具体化的(reified)。因此数组会在运行时才知道并检查它们的元素类型约束。相比之下，泛型则是通过擦除来实现的。因此泛型只在编译时强化它们的类型信息，并在运行时丢弃它们的元素类型信息。
