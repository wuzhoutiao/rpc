package server;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@Configuration //告诉Spring从这个类开始读取配置
@ComponentScan(basePackages = {
        "annotation",
        "bean",
        "client",
        "constants",
        "factory",
        "future",
        "handler",
        "medium",
        "protocol",
        "remote",
        "server",
        "service",
        "util"
})
public class SpringServer {
    public static void main(String[] args) throws Exception {
        ApplicationContext context = new AnnotationConfigApplicationContext(SpringServer.class);
    }
}
//cd D:\apache-zookeeper-3.8.6-bin\bin
//.\zkCli.cmd
//ls /