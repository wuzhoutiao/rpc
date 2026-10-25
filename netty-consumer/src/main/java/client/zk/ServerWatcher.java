package client.zk;

import client.core.ChannelManager;
import client.core.NettyClient;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.WatchedEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ServerWatcher implements CuratorWatcher {
    //ZooKeeper 事件发生时自动调用
    public void  process(WatchedEvent event) throws Exception{
        System.out.println("ZooKeeper Watcher被触发了----------------");
        String path = event.getPath(); //获取变化路径
        if(path==null) return;

        CuratorFramework client = ZooKeeperFactory.getClient();
        //重新注册watcher，同时获取最新子节点列表
        List<String> newServerPaths = client.getChildren().usingWatcher(this).forPath(path); //获取该变化路径下所有子节点

        Set<String> newServers = new HashSet<>();

        for(String p:newServerPaths){
            String[] str = p.split("#");
            String ip = str[0];
            String port = str[1];
            newServers.add(ip+":"+port);
        }

        System.out.println("最新服务器列表："+newServers);
        ChannelManager.updateChannels(newServers,NettyClient.b);
    }
}
