package remote;

import annotation.Remote;
import protocol.Response;
import bean.User;
import service.UserService;
import util.ResponseUtil;

import javax.annotation.Resource;
import java.util.List;

@Remote
public class UserRemoteImpl implements UserRemote {
    @Resource //依赖注入
    private UserService userService;

    @Override
    public Response saveUser(User user) {
        userService.saveUser(user);
        Response response = ResponseUtil.createSuccessResponse(user);
        return response;
    }

    @Override
    public Response saveUsers(List<User> userslist) {
        userService.saveUsers(userslist);
        Response response = ResponseUtil.createSuccessResponse(userslist);
        return response;
    }
}
