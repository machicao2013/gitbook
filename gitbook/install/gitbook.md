gitbook安装
=====

gitbook安装脚本

```bash
if [[ ${NODEJS_HOME}/bin/npm != `which npm` ]]; then
    echo "setting nodejs path error!"
    exit 1
fi

npm install gitbook -g
```

安装完成后，可以通过如下方法检测是否安装成功

```bash
$ gitbook -V
1.3.3
```
