package com.rpc.api.protocol;

/**
 * 客户端与服务端共用的 RPC 请求协议。
 */
public class Request {

    private Long id;
    /** @deprecated 使用 arguments。保留该字段以兼容旧客户端。 */
    @Deprecated
    private Object content;
    private Object[] arguments;
    private String[] parameterTypeNames;
    private String command;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Deprecated
    public Object getContent() {
        return content;
    }

    @Deprecated
    public void setContent(Object content) {
        this.content = content;
    }

    public Object[] getArguments() {
        if (arguments != null) {
            return arguments;
        }
        return content == null ? new Object[0] : new Object[]{content};
    }

    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }

    public String[] getParameterTypeNames() {
        return parameterTypeNames;
    }

    public void setParameterTypeNames(String[] parameterTypeNames) {
        this.parameterTypeNames = parameterTypeNames;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
