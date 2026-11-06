package com.rpc.server;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@Configuration //告诉Spring从这个类开始读取配置
@ComponentScan("com.rpc.server")
public class SpringServer {
    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(SpringServer.class);
        context.registerShutdownHook();
    }
}
