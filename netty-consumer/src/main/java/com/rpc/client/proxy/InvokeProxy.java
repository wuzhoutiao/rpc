package com.rpc.client.proxy;

import com.rpc.api.protocol.Request;
import com.rpc.api.protocol.RpcResponse;
import com.rpc.api.protocol.RpcMethodSignature;
import com.rpc.client.annotation.RemoteInvoke;
import com.rpc.client.core.NettyClient;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

@Component
public class InvokeProxy implements BeanPostProcessor {
    private static final boolean VERBOSE = Boolean.getBoolean("rpc.verbose");

    @Override
    public Object postProcessBeforeInitialization(
            Object bean,
            String beanName) throws BeansException {

        Field[] fields = bean.getClass().getDeclaredFields();

        for (Field field : fields) {

            if (!field.isAnnotationPresent(RemoteInvoke.class)) {
                continue;
            }

            Class<?> clazz = field.getType();

            // JDK 动态代理只能代理接口
            if (!clazz.isInterface()) {
                throw new IllegalStateException(
                        "@RemoteInvoke 只能标注接口类型：" + clazz.getName()
                );
            }

            System.out.println("发现远程服务接口：" + clazz.getName());

            Object proxy = Proxy.newProxyInstance(
                    clazz.getClassLoader(),
                    new Class<?>[]{clazz},
                    (proxyObject, method, args) -> {

                        // Object 自带的方法不应该发送 RPC 请求
                        if (method.getDeclaringClass() == Object.class) {
                            switch (method.getName()) {
                                case "toString":
                                    return "RpcProxy(" + clazz.getName() + ")";
                                case "hashCode":
                                    return System.identityHashCode(proxyObject);
                                case "equals":
                                    return proxyObject == args[0];
                                default:
                                    throw new UnsupportedOperationException(
                                            method.getName()
                                    );
                            }
                        }

                        Request request = new Request();
                        request.setArguments(args == null ? new Object[0] : args);
                        Class<?>[] parameterTypes = method.getParameterTypes();
                        String[] parameterTypeNames = new String[parameterTypes.length];
                        for (int i = 0; i < parameterTypes.length; i++) {
                            parameterTypeNames[i] = parameterTypes[i].getName();
                        }
                        request.setParameterTypeNames(parameterTypeNames);

                        String command = RpcMethodSignature.of(clazz, method);

                        request.setCommand(command);

                        if (VERBOSE) {
                            System.out.println("客户端发送 command = " + command);
                        }

                        RpcResponse response = NettyClient.send(request);
                        return RpcResultDecoder.decode(response, method);
                    }
            );

            try {
                field.setAccessible(true);
                field.set(bean, proxy);

                System.out.println(
                        "远程代理注入成功：" + field.getName()
                );

            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "远程代理注入失败：" + field.getName(),
                        e
                );
            }
        }

        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(
            Object bean,
            String beanName) throws BeansException {
        return bean;
    }
}
