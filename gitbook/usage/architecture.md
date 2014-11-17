gitbook的目录组织结构
=========

一本图书就是一个git reposity，至少应该包含两个文件: `README.md`和`SUMMARY.md`

README.md主要是图书的介绍，它可以自动被加载到最终的summary中。

SUMMARY.md定义了图书的结构，应该包含章节的列表，以及它们的链接。没有被SUMMARY.md包含的文件不会被gitbook处理。
