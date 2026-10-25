package client.core;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class RpcClientInitializer {
    @PostConstruct
    public void init() {
        System.out.println("Spring 开始初始化 RPC 客户端");

        NettyClient.initialize();
    }
}
