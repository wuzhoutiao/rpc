package client.zk;

import client.core.ChannelManager;
import client.core.NettyClient;
import io.netty.channel.ChannelFuture;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.WatchedEvent;

import java.util.List;

public class ServerWatcher implements CuratorWatcher {
    //ZooKeeper 事件发生时自动调用
    public void  process(WatchedEvent event) throws Exception{
        System.out.println("ZooKeeper事件发生，Watcher被触发了----------------");
        CuratorFramework client = ZooKeeperFactory.getClient();
        String path = event.getPath(); //获取变化路径
        client.getChildren().usingWatcher(this).forPath(path); //监听默认只触发一次，必须重新注册watcher继续监听该变化路径
        List<String> newServerPaths = client.getChildren().forPath(path); //获取该变化路径下所有子节点
        System.out.println("变化路径：" + path + "，当前所有子节点为：");
        System.out.println(newServerPaths);
        ChannelManager.realServerPath.clear(); //清空旧服务器集合
        for(String p:newServerPaths){
            String[] str = p.split("#");
            ChannelManager.realServerPath.add(str[0]+"#"+str[1]);
        }
        ChannelManager.clearChannelFutures(); //清空旧连接
        for(String r:ChannelManager.realServerPath){
            String[] str = r.split("#");
            ChannelFuture channelFuture = NettyClient.b.connect(str[0], Integer.valueOf(str[1])); //重新建立Netty连接
            ChannelManager.addChannelFuture(channelFuture); //放进连接池
        }
    }
}
