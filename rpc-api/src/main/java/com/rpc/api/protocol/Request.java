package com.rpc.api.protocol;

/**
 * 客户端与服务端共用的 RPC 请求协议。
 */
public class Request {

    private Long id;
    private Object content;
    private String command;

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
