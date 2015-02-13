nc命令
=======

1. 监听一个端口：nc -l 1234
2. 连接监听端口：nc 192.168.56.101 1234
3. 监听一个端口，并将结果保存到文件:
    - nc -l 1234 > filename.out
    - nc -l 1234 | dd of=filename.out
4. 将一个文件作为内容发送:
    - nc 192.168.56.101 1234 < filename.in
    - dd if=filename.in | nc 192.168.56.101 1234
5. 获取一个web page: echo -ne "HEAD / HTTP/1.0\r\nHost: www.baidu.com\r\nConnection: close\r\n\r\n" | nc www.baidu.com 80
6. 使用本地端口31337连接baidu.com:8080,并设置超时时间5s:  nc -p 31337 -w 5 baidu.com 8080
7. 打开一个udp连接： nc -u baidu.com 80
8. 使用10.1.2.3这个Ip连接远程服务器： nc -s 10.1.2.3 baidu.com 80
9. http代理服务器10.2.3.4:8080连接：nc -x10.2.3.4:8080 -Xconnect baidu.com
10. 扫描端口：nc -z host.example.com 20-30
11. A机器压缩文件传到B机器:
    - tar cvf - /redis_client | nc -l 1234
    - nc A_ip 1234 | tar xvf -
12. dd if=/dev/zero bs=1MB count=1000 | nc 192.168.1.120 5001;  nc -l 5001 > /dev/null
