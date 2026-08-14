### 1. 日常启动 ZooKeeper

打开 WSL Ubuntu，执行：

```bash
cd /mnt/d/apache-zookeeper-3.8.6-bin
bin/zkServer.sh start
bin/zkServer.sh status
```

若出现：

```text
Mode: standalone
```

说明 ZooKeeper 已正常启动。

---

### 2. 停止 ZooKeeper

项目使用结束后执行：

```bash
cd /mnt/d/apache-zookeeper-3.8.6-bin
bin/zkServer.sh stop
```

检查是否停止：

```bash
bin/zkServer.sh status
```
