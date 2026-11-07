package com.rpc.server.remote;

import com.rpc.api.model.User;
import com.rpc.api.service.UserRemote;
import com.rpc.server.annotation.Remote;
import com.rpc.server.service.UserService;
import com.rpc.server.testing.FaultInjector;

import javax.annotation.Resource;
import java.util.List;

@Remote
public class UserRemoteImpl implements UserRemote {
    @Resource //依赖注入
    private UserService userService;

    @Override
    public User saveUser(User user) {
        userService.saveUser(user);
        return user;
    }

    @Override
    public List<User> saveUsers(List<User> userslist) {
        userService.saveUsers(userslist);
        return userslist;
    }

    @Override
    public String findUser(long id) {
        FaultInjector.beforeQuery();
        return "user:" + id;
    }

    @Override
    public String findUser(long id, String name) {
        FaultInjector.beforeQuery();
        return "user:" + id + ":" + name;
    }
}
