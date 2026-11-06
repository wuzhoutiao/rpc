package com.rpc.api.protocol;

import java.lang.reflect.Method;
import java.util.StringJoiner;

/** 生成跨客户端、服务端一致的方法签名，确保重载方法不会互相覆盖。 */
public final class RpcMethodSignature {

    private static final char SERVICE_SEPARATOR = '#';

    private RpcMethodSignature() {
    }

    public static String of(Class<?> serviceInterface, Method method) {
        return of(serviceInterface.getName(), method.getName(), method.getParameterTypes());
    }

    public static String of(String serviceName, String methodName, Class<?>[] parameterTypes) {
        StringJoiner joiner = new StringJoiner(",");
        for (Class<?> parameterType : parameterTypes) {
            joiner.add(parameterType.getName());
        }
        return serviceName + SERVICE_SEPARATOR + methodName + "(" + joiner + ")";
    }

    public static String serviceName(String command) {
        if (command == null) {
            return null;
        }
        int separator = command.indexOf(SERVICE_SEPARATOR);
        if (separator > 0) {
            return command.substring(0, separator);
        }

        // 兼容旧协议：com.example.Service.method
        int methodSeparator = command.lastIndexOf('.');
        return methodSeparator > 0 ? command.substring(0, methodSeparator) : null;
    }
}
