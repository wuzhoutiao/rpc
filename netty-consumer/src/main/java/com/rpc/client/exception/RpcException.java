package com.rpc.client.exception;

/** RPC 传输、治理或远程业务调用失败。 */
public class RpcException extends RuntimeException {
    private final String code;
    private final long requestId;

    public RpcException(String code, String message, long requestId) {
        super(message == null || message.trim().isEmpty() ? "RPC 调用失败：" + code : message);
        this.code = code;
        this.requestId = requestId;
    }

    public String getCode() { return code; }
    public long getRequestId() { return requestId; }
}
