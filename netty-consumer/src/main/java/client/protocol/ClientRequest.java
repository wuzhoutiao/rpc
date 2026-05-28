package client.protocol;

import java.util.concurrent.atomic.AtomicLong;

public class ClientRequest {

    private Long id ; //真正属于当前请求的编号
    private Object content;//方法参数
    private static AtomicLong realID = new AtomicLong(0); //全局请求ID生成器,确保线程安全
    private String command; //media.map里的key

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
