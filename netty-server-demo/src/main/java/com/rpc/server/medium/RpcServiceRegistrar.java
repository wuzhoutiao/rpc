package com.rpc.server.medium;

import com.rpc.server.annotation.Remote;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class RpcServiceRegistrar implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean.getClass().isAnnotationPresent(Remote.class)) { //如果这个bean对应的类被Remote注解了
            Method[] methods = bean.getClass().getDeclaredMethods(); //获取这个类自己声明的所有方法
            for (Method method : methods) {
                String key =bean.getClass().getInterfaces()[0].getName() + "." + method.getName();
                RpcMethod rpcMethod = new RpcMethod();
                rpcMethod.setBean(bean);
                rpcMethod.setMethod(method);
                RpcRequestDispatcher.register(key, rpcMethod);
                System.out.println("服务端扫描发现一个对外暴露的方法"+ key);
            }
        }
        return bean;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }
}
