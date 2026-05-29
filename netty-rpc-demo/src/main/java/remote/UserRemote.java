package remote;

import protocol.Response;
import bean.User;

import java.util.List;

public interface UserRemote {
    public Response saveUser(User user);
    public Response saveUsers(List<User> userslist);
}
