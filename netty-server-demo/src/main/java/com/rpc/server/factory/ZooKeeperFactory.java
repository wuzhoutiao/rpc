package com.rpc.server.factory;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

public class ZooKeeperFactory {
    private static final String DEFAULT_ADDRESS =
            "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
    private static CuratorFramework client;

    public static synchronized CuratorFramework getClient() {
        if (client == null) {
            RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
            String address = System.getProperty("rpc.zookeeper.address", DEFAULT_ADDRESS);
            client = CuratorFrameworkFactory.newClient(address, retryPolicy);
            client.start();
        }
        return client;
    }

    public static synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
