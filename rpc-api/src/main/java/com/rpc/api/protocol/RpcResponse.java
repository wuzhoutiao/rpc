package com.rpc.api.protocol;

/**
 * RPC 传输层响应信封。业务方法的真实返回值保存在 result 中，
 * 客户端代理会按接口方法声明的返回类型完成反序列化。
 */
public class RpcResponse {

    private long id;
    private Object result;
    private String code = "00000";
    private String message;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
