package com.rpc.service;

import com.rpc.api.model.User;
import com.rpc.api.protocol.Response;
import com.rpc.service.config.ClientConfig;
import com.rpc.service.service.ClientService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ClientApplication {

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ClientConfig.class);
        context.registerShutdownHook();

        System.out.println("RPC 客户端已启动");

        User user = new User();
        user.setId(1000L);
        user.setName("张三");

        ClientService clientService = context.getBean(ClientService.class);
        Response response = clientService.callRemote(user);
        System.out.println(
                "远程调用完成，code=" + response.getCode()
                        + "，message=" + response.getMessage()
                        + "，result=" + response.getResult()
        );

        System.in.read();
        context.close();
    }
}
