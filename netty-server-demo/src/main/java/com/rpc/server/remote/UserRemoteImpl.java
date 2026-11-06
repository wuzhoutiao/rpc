package com.rpc.server.remote;

import com.rpc.api.model.User;
import com.rpc.api.protocol.Response;
import com.rpc.api.service.UserRemote;
import com.rpc.server.annotation.Remote;
import com.rpc.server.service.UserService;

import javax.annotation.Resource;
import java.util.List;

@Remote
public class UserRemoteImpl implements UserRemote {
    @Resource //依赖注入
    private UserService userService;

    @Override
    public Response saveUser(User user) {
        userService.saveUser(user);
        Response response = createSuccessResponse(user);
        return response;
    }

    @Override
    public Response saveUsers(List<User> userslist) {
        userService.saveUsers(userslist);
        Response response = createSuccessResponse(userslist);
        return response;
    }

    private Response createSuccessResponse(Object result) {
        Response response = new Response();
        response.setResult(result);
        return response;
    }
}
