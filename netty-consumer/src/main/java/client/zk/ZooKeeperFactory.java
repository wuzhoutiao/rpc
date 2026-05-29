package client.zk;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

public class ZooKeeperFactory {
    public static CuratorFramework client; //CuratorFramework是 Curator 提供的 ZooKeeper 客户端对象

    public static CuratorFramework getClient(){
        if(client==null){
            RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3); //自动重试机制：初始等待1000ms，最多重试三次，并且每次失败等待时间会指数增长
            client = CuratorFrameworkFactory.newClient("127.0.0.1:2181", retryPolicy); //使用刚才定义的重试机制，创建一个 ZooKeeper 客户端，并连接到这台 ZooKeeper 服务(127.0.0.1:2181只是表示Zookeeper服务运行在哪儿，不是节点路径)
            client.start();
        }
        return client; //真正启动客户端
    }

}

