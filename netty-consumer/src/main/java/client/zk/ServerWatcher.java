package client.zk;

import client.constants.Constants;
import client.core.ChannelManager;
import client.core.NettyClient;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.KeeperException;

import java.util.*;

public class ServerWatcher implements CuratorWatcher {

    // 接口名称 -> 提供该接口的服务器地址集合
    private volatile Map<String, Set<String>> serviceServers =
            Collections.emptyMap();

    /**
     * ZooKeeper Watcher 触发时执行
     */
    @Override
    public void process(WatchedEvent event) throws Exception {

        System.out.println(
                "ZooKeeper Watcher 被触发：" + event.getPath()
        );

        // 客户端关闭期间不再处理服务发现事件
        if (NettyClient.isShuttingDown()) {
            return;
        }

        // ZooKeeper 连接状态事件不一定包含节点路径
        if (event.getPath() == null) {
            return;
        }

        refreshServices();
    }

    /**
     * 重新读取所有 RPC 服务，并注册 Watcher
     *
     * 客户端首次启动时也需要主动调用此方法
     */
    public synchronized void refreshServices() throws Exception {

        if (NettyClient.isShuttingDown()) {
            return;
        }

        CuratorFramework client = ZooKeeperFactory.getClient();

        // 1. 获取所有 RPC 接口名称
        // 同时监听 /netty 下接口节点的新增和删除
        List<String> serviceNames;

        try {
            serviceNames = client.getChildren()
                    .usingWatcher(this)
                    .forPath(Constants.SERVER_PATH);

        } catch (KeeperException.NoNodeException e) {

            // 注册根节点尚不存在
            System.out.println("RPC 服务注册根节点不存在");

            return;
        }

        Map<String, Set<String>> newServiceServers =
                new HashMap<>();

        Set<String> allServers = new HashSet<>();

        // 2. 遍历每个 RPC 接口
        for (String serviceName : serviceNames) {

            String servicePath =
                    Constants.SERVER_PATH + "/" + serviceName;

            List<String> serverNodes;

            try {

                // 获取该接口下的服务器节点
                // 同时监听服务器节点的新增和删除
                serverNodes = client.getChildren()
                        .usingWatcher(this)
                        .forPath(servicePath);

            } catch (KeeperException.NoNodeException e) {

                // 接口节点在读取期间被删除
                continue;
            }

            Set<String> servers = new HashSet<>();

            // 3. 将 ZooKeeper 节点转换成 IP:端口
            for (String serverNode : serverNodes) {

                // 例如：
                // 192.168.1.10#8081#0000000001
                String[] parts = serverNode.split("#");

                if (parts.length < 2) {
                    System.err.println(
                            "无效的服务器节点：" + serverNode
                    );
                    continue;
                }

                String address = parts[0] + ":" + parts[1];

                servers.add(address);
                allServers.add(address);
            }

            newServiceServers.put(
                    serviceName,
                    Collections.unmodifiableSet(servers)
            );
        }

        if (NettyClient.isShuttingDown()) {
            return;
        }

        // 4. 更新服务映射
        serviceServers = Collections.unmodifiableMap(
                newServiceServers
        );

        System.out.println(
                "最新 RPC 服务映射：" + serviceServers
        );

        // 5. 更新 Netty 连接池
        ChannelManager.updateChannels(
                allServers,
                NettyClient.b
        );
    }

    /**
     * 获取指定 RPC 接口对应的服务器列表
     */
    public Set<String> getServers(String serviceName) {

        return serviceServers.getOrDefault(
                serviceName,
                Collections.emptySet()
        );
    }
}