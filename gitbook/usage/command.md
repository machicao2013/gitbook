gitbook支持的命令
======

gitbook是一个命令行工具，使用方法比较简单

**查看帮助**
```bash
Commands:

    build [options] [source_dir] Build a gitbook from a directory
    serve [options] [source_dir] Build then serve a gitbook from a directory
    pdf [options] [source_dir] Build a gitbook as a PDF
    epub [options] [source_dir] Build a gitbook as a ePub book
    mobi [options] [source_dir] Build a gitbook as a Mobi book
    init [source_dir]      Create files and folders based on contents of SUMMARY.md
    publish [source_dir]   Publish content to the associated gitbook.io book
    git:remote [source_dir] [book_id] Adds a git remote to a book repository

Options:

    -h, --help     output usage information
    -V, --version  output the version number

```

**基于SUMMARY.md创建目录和文件**
```bash
$ gitbook init .
```

但是该命令在遇到多级目录的时候是不会创建多级目录下面的文件，因此我自己写了一个脚本来完成此功能，脚本如下:
```bash
#!/bin/sh

test -f SUMMARY.md || (echo "You must have a file named SUMMARY.md" && exit 1)

for file in `awk -F "[()]" '{print $2}' SUMMARY.md`; do
     path=${file%/*}
     test -d ${path} || mkdir -p $path
     test -f ${file} || touch $file
done
```

**输出一个静态网站**
```bash
$ gitbook build .
```

**输出一个静态网站并启动一个web服务**
```bash
$ gitbook serve .
```
