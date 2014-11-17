gitbook的输出
======

gitbook支持的输出格式有：

- html
- pdf
- epub
- mobi

具体的使用方法见gitbook -h,可能会缺少某些依赖。

**将自己的gitbook托管到github**

下面的操作是假设你已经有github帐号

1. 在github上创建一个gitbook的project。假设连接为: git@github.com:machicao2013/gitbook.git
2. 在本地clone一份代码：`git clone git@github.com:machicao2013/gitbook.git`
3. 执行以下操作：
```bash
git checkout -b master
git push -u origin master
git checkout -b gh-pages
git push -u origin gh-pages
git checkout master
echo "_book" >> .gitignore
echo "book_end" >> .gitignore
git clone -b gh-pages git@github.com:machicao2013/gitbook.git book_end
```
4. 拷贝你的book到git工作空间，将book的源文件加到master分支，并提交。
5. 使用`gitbook build .`命令，会生成_book目录。
6. 执行`cp -r _book/* book_end`。
7. 执行`git checkout gh-pages`,add并commit。

