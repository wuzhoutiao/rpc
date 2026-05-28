import bean.User;
import client.protocol.Response;

public interface TestRemote {
    public Response testUser(User user);
}
