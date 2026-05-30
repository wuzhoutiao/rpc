import bean.User;
import client.annotation.RemoteInvoke;
import client.protocol.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import remote.UserRemote;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = RemoteInvokeTest.class)
@ComponentScan(basePackages = {"client"})
public class RemoteInvokeTest {

    @RemoteInvoke
    private UserRemote userremote;

    @Test
    public void testSaveUser() {
        User user = new User();
        user.setId(1000);
        user.setName("张三");

        Response response = userremote.saveUser(user);

        System.out.println(response);
    }
}