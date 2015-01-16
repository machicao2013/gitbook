Java Generic Programming
==============

泛型编程意味着编写的代码可以被很多不同类型的对象所重用。

** 简单泛型 **

泛型类的定义
```java
public class Pair<T> {
    private T first;
    private T second;

    public Pair() {
        first = second = null;
    }

    public T getFirst() {
        return first;
    }

    public void setFirst(T first) {
        this.first = first;
    }

    public T getSecond() {
        return second;
    }

    public void setSecond(T second) {
        this.second = second;
    }
}
```
泛型方法的定义
```java
class ArrayAlg
{
    public static <T> T getMiddle(T[] arrays) {
        return arrays[arrays.length/2];
    }
}
```

** 泛型代码与虚拟机 **

1. 虚拟机没有泛型类型对象--所有对象都属于普通类。
2. 无论何时定义一个泛型类型，都自动提供了一个相应的**原始类型(raw type)**。原始类型的名字就是删去类型参数后的泛型类型名。擦除类型变量，并替换为限定类型(无线定的变量用object)。
3. 例如上例中的Pair<T>的原始类型如下：
```java
public class Pair {
    private Object firsObject;
    private Object second;

    public Pair() {
        firsObject = second = null;
    }

    public Object geObjectFirsObject() {
        reObjecturn firsObject;
    }

    public void seObjectFirsObject(Object firsObject) {
        Objecthis.firsObject = firsObject;
    }

    public Object geObjectSecond() {
        reObjecturn second;
    }

    public void seObjectSecond(Object second) {
        Objecthis.second = second;
    }
}
```
4. **T是一个无限定的变量，所以直接用Object替换。就这点而言，java泛型与C++模版有很大的区别。C++中每个模版的实例化产生不同的类型，这一现象称为"模版代码膨胀"。Java不存在这个问题的困扰。**
5. 原始类型用第一个限定的类型变量来替换，如果没有给定限定就用Object替换。例如，类Pair<T>中的类型变量没有显式的限定，因此，原始类型用Object替换T。
```java
class Interval<T extends Comparable & Serializable> implements Serializable
{
    private static final long serialVersionUID = 7679772108855820645L;

    public Interval(T first, T second)
    {
        if (first.compareTo(second) <= 0) {
            lower = first;
            upper = second;
        } else {
            upper = first;
            lower = second;
        }
    }
    private T lower;
    private T upper;
}
```
6. 其对应的原始类型为：
```java
class Interval implements Serializable
{
    public Interval(Comparable first, Comparable second)
    ....
    private Comparable lower;
    private Comparable upper;
}
```
** 约束与局限性 **

1. 不能用基本类型实例化类型参数.没有Pair<double>，原因是类型擦除，擦除之后，Pair类含有Object类型的域，而Object不能存储double值。
2. 运行时类型查询只适用于原始类型。虚拟机中的对象总有一个特定的非泛型类型。因此，所有的类型查询只产生原始类型。因此，getClass总是返回原始类型，Pair<String> stringPair; Pair<Employee> employeePair; stringPair.getClass() == employeePair.getClass()总是成立。
3. 参数化类型的数组不合法
4. 不能实例化类型变量。不能使用像new T(..), new T[...]或T.class这样的表达式中的类型变量。可以使用下面的方法获取对象：
```java
public static<T> Pair<T> makePair(Class<T> clazz) {
    try {
        return new Pair<T>(clazz.newInstance(), clazz.newInstance());
    } catch(Exception ) {
        // ....
    }
}
5. 泛型类的静态上下文中类型变量无效。

** 通配符类型 **
1. Pair<? extends Employee>:表示任何泛型Pair类型，它的参数是Employee的子类。
2. 带有超类型的通配符可以向泛型对象写入，带有子类型限定的通配符可以从泛型对象读取。
3. 无限制通配类型Set<?>和原生态类型Set之间有什么区别呢？这个问号真正起到作用了吗？当然有作用，通配符是安全的，原生态类型则是不安全的。由于可以将任何元素放进使用原生态类型的集合中，因此很容易破坏该集合的类型约束条件；但**不能将任何元素(除了null之外)放到Collection<?>中**。
4. 使用原生态类型会在运行时导致异常，因此不要在新代码中使用。原生态类型只是为了引入泛型之前的遗留代码进行兼容和互用而提供的。
5. Set<Object>是个参数化类型，表示可以包含任何对象类型的一个集合；Set<?>则是一个通配符类型，**表示只能包含某种未知对象类型的一个集合**；Set则是个原生态类型，它脱离了泛型系统。前两种是安全的，最后一种不安全。
