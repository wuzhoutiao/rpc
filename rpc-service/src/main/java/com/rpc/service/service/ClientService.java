package com.rpc.service.service;

import bean.User;
import client.annotation.RemoteInvoke;
import com.rpc.common.protocol.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import remote.UserRemote;

@Service
public class ClientService {

    // 本地服务
    @Autowired
    private LocalService localService;

    // 远程服务
    @RemoteInvoke
    private UserRemote userRemote;

    // 本地调用
    public String callLocal() {
        return localService.hello();
    }

    // 远程调用
    public Response callRemote(User user) {
        return userRemote.saveUser(user);
    }
}
