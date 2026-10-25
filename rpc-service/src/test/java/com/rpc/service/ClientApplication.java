package com.rpc.service;

import com.rpc.service.config.ClientConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ClientApplication {

    public static void main(String[] args) throws Exception {

        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ClientConfig.class);

        System.out.println("RPC 客户端已启动");

        // 保持客户端进程运行，方便测试断线重连
        System.in.read();

        context.close();
    }
}