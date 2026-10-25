package client.core;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class RpcClientInitializer {

    /**
     * Spring 创建 Bean 后执行
     */
    @PostConstruct
    public void init() {

        System.out.println("Spring 开始初始化 RPC 客户端");

        NettyClient.initialize();
    }

    /**
     * Spring 销毁 Bean 前执行
     */
    @PreDestroy
    public void destroy() {

        System.out.println("Spring 开始销毁 RPC 客户端");

        NettyClient.shutdown();
    }
}
