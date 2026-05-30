package remote;

import bean.User;
import client.protocol.Response;

import java.util.List;

public interface UserRemote {
    public Response saveUser(User user);
    public Response saveUsers(List<User> userlist);
}
