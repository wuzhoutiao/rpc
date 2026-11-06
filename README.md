# RPC Demo

## 项目结构

```text
rpc-api             共享接口、模型和 RPC 协议
netty-consumer      RPC 客户端、代理与服务发现
netty-server-demo   RPC 服务端与提供者示例
rpc-service         消费者示例应用
```

项目保持四个 Maven 模块，不再为少量协议类单独建立 common 模块。

## 启动 ZooKeeper

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

ZooKeeper 地址可以通过 JVM 参数覆盖：

```text
-Drpc.zookeeper.address=127.0.0.1:2181
```

## 停止 ZooKeeper

项目使用结束后执行：

```bash
cd /mnt/d/apache-zookeeper-3.8.6-bin
bin/zkServer.sh stop
```

检查是否停止：

```bash
bin/zkServer.sh status
```

## 启动示例

先运行 `com.rpc.server.SpringServer`，再运行
`com.rpc.service.ClientApplication`。

默认单元测试不依赖 ZooKeeper：

```bash
mvn clean test
```

需要真实 ZooKeeper 和服务端的集成测试：

```bash
mvn verify -Pintegration-tests
```
