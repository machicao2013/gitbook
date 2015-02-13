ss命令常用参数
=========

1. 显示所有打开网络连接的端口：ss -l
2. 显示连接和进程的信息(需要root权限)：ss -lp
3. 显示所有的tcp信息： ss -ta
4. 显示所有的udp信息： ss -ua
5. 显示所有状态位established的连接： ss -o state established
6. 显示所有状态为established的SMTP连接: ss -o state established '( dport = :smtp or sport = :smtp )'
7. 显示所有状态为Established的HTTP连接:
    - ss -o state established '( dport = :http or sport = :http )'
    - ss -o state established '( dport = :80 or sport = :80 )'
8. 列出所有状态为FIN-WAIT-1的Tcp Sockets: ss -o state final_wait_1 '( sport = :http or sport = :https )' dst 202.54.1/24
9. 显示所有连接到远程服务器192.168.1.5的端口: ss dst 192.168.1.5
10. 显示所有连接到远程服务器192.168.1.5的80端口: ss dst 192.168.1.5:http
11. 将本地或者远程端口和一个数比较:
    - ss  sport = :http
    - ss  dport = :http
    - ss  dport > :1024
    - ss  sport > :1024
    - ss sport < :32000
    - ss  sport eq :22
    - ss  dport != :22
    - ss  state connected sport = :http
    - ss '( sport = :http or sport = :https )'
    - ss -o state fin-wait-1 '( sport = :http or sport = :https )' dst 192.168.1/24

