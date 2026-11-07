package com.rpc.client.proxy;

import com.alibaba.fastjson.JSONObject;
import com.rpc.api.protocol.RpcResponse;
import com.rpc.client.constants.Constants;
import com.rpc.client.exception.RpcException;

import java.lang.reflect.Method;

/** 将协议响应信封转换为接口方法声明的业务返回类型。 */
final class RpcResultDecoder {

    private RpcResultDecoder() {
    }

    static Object decode(RpcResponse response, Method method) {
        if (!Constants.SUCCESS.equals(response.getCode())) {
            throw new RpcException(response.getCode(), response.getMessage(), response.getId());
        }
        if (method.getReturnType() == void.class) {
            return null;
        }
        Object result = response.getResult();
        if (result == null) {
            if (method.getReturnType().isPrimitive()) {
                throw new RpcException("RPC_NULL_PRIMITIVE_RESULT",
                        "RPC 返回 null，无法转换为基本类型：" + method.getReturnType().getName(),
                        response.getId());
            }
            return null;
        }
        return JSONObject.parseObject(
                JSONObject.toJSONString(result), method.getGenericReturnType());
    }
}
