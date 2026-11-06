package com.rpc.service;

import com.rpc.api.model.User;
import com.rpc.service.config.ClientConfig;
import com.rpc.service.service.ClientService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * 依赖 ZooKeeper 和真实 RPC 服务端的集成测试。
 * 使用 mvn verify -Pintegration-tests 运行。
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = ClientConfig.class)
public class ClientServiceIT {

    @Autowired
    private ClientService clientService;

    @Test
    public void testLocal() {
        String result = clientService.callLocal();
        System.out.println(result);
    }

    @Test
    public void testRemote() {
        User user = new User();
        user.setId(1000L);
        user.setName("张三");
        System.out.println(clientService.callRemote(user));
    }
}
