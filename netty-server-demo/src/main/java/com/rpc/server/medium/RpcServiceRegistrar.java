package com.rpc.server.medium;

import com.rpc.server.annotation.Remote;
import com.rpc.api.protocol.RpcMethodSignature;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class RpcServiceRegistrar implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean.getClass().isAnnotationPresent(Remote.class)) { //如果这个bean对应的类被Remote注解了
            Class<?>[] interfaces = bean.getClass().getInterfaces();
            if (interfaces.length == 0) {
                throw new IllegalStateException("@Remote 服务必须实现接口：" + bean.getClass().getName());
            }
            for (Class<?> serviceInterface : interfaces) {
                for (Method interfaceMethod : serviceInterface.getMethods()) {
                    try {
                        Method implementationMethod = bean.getClass().getMethod(
                                interfaceMethod.getName(), interfaceMethod.getParameterTypes());
                        String key = RpcMethodSignature.of(serviceInterface, interfaceMethod);
                        RpcMethod rpcMethod = new RpcMethod();
                        rpcMethod.setBean(bean);
                        rpcMethod.setMethod(implementationMethod);
                        RpcRequestDispatcher.register(key, rpcMethod);
                        System.out.println("服务端扫描发现一个对外暴露的方法 " + key);
                    } catch (NoSuchMethodException e) {
                        throw new IllegalStateException("找不到接口方法的实现：" + interfaceMethod, e);
                    }
                }
            }
        }
        return bean;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }
}
