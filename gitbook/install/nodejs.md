nodejs的安装
========

> Node.js 是一个基于Chrome JavaScript 运行时建立的一个平台， 用来方便地搭建快速的 易于扩展的网络应用· Node.js 借助事件驱动， 非阻塞I/O 模型变得轻量和高效， 非常适合 运行在分布式设备 的 数据密集型 的实时应用。[reference](http://baike.baidu.com/view/3974030.htm?fr=aladdin)

nodejs安装脚本
```bash
#!/bin/bash

NODEJS_HOME=~/opt/node-v0.10.33

test -f node-v0.10.33.tar.gz || wget http://nodejs.org/dist/v0.10.33/node-v0.10.33.tar.gz

tar zxvf node-v0.10.33.tar.gz

cd node-v0.10.33

test -d ${NODEJS_HOME} || mkdir -p ${NODEJS_HOME}

./configure --prefix=${NODEJS_HOME}

make && make install

echo "export PATH=${NODEJS_HOME}:${PATH}" >> ~/.bashrc

source ~/.bashrc
```
