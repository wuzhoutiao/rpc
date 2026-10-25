package client.proxy;

import client.annotation.RemoteInvoke;
import client.core.NettyClient;
import client.protocol.ClientRequest;
import com.rpc.common.protocol.Response;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

@Component
public class InvokeProxy implements BeanPostProcessor {

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

                        ClientRequest request = new ClientRequest();

                        // 当前 RPC 协议暂时只支持单参数
                        if (args == null || args.length != 1) {
                            throw new UnsupportedOperationException(
                                    "当前 RPC 框架只支持单参数方法：" + method.getName()
                            );
                        }

                        request.setContent(args[0]);

                        String command =
                                clazz.getName() + "." + method.getName();

                        request.setCommand(command);

                        System.out.println(
                                "客户端发送 command = " + command
                        );

                        return NettyClient.send(request);
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