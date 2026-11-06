package com.rpc.service.service;

import com.rpc.api.model.User;
import com.rpc.api.protocol.Response;
import com.rpc.api.service.UserRemote;
import com.rpc.client.annotation.RemoteInvoke;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    public Response findRemote(long id, String name) {
        return userRemote.findUser(id, name);
    }
}
