package remote;

import bean.User;
import client.protocol.Response;

import java.util.List;

public interface UserRemote {
    Response saveUser(User user);
    Response saveUsers(List<User> userlist);
}
