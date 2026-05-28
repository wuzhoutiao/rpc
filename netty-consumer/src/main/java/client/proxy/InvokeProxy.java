package client.proxy;

import client.annotation.RemoteInvoke;
import client.core.NettyClient;
import client.protocol.ClientRequest;
import client.protocol.Response;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

/*扫描 Spring Bean 里带 @RemoteInvoke 的字段，给这个字段生成一个代理对象。
以后调用这个字段的方法时，不会真的执行本地方法，而是封装成 ClientRequest，通过 Netty 发给服务端。*/
@Component
public class InvokeProxy implements BeanPostProcessor {

    private void putMethodClass(HashMap<Method, Class> methodmap, Field field) {
        Method[] methods = field.getType().getDeclaredMethods(); //获取这个属性的类型和该类型中的所有方法
        for(Method method : methods){
            methodmap.put(method, field.getType()); //将方法和属性的类建立映射
        }

    }

    public Object postProcessBeforeInitialization(Object bean, String arg1) throws BeansException {
        Field[] fields = bean.getClass().getDeclaredFields();
        for(Field field : fields){
            if(field.isAnnotationPresent(RemoteInvoke.class)){
                field.setAccessible(true); //允许通过反射访问 private 字段
                final Class<?> clazz = field.getType();
                Enhancer enhancer = new Enhancer();
                enhancer.setInterfaces(new Class[]{field.getType()}); //设置代理对象要实现的接口
                enhancer.setCallback(new MethodInterceptor() { //设置拦截器，只要调用了代理对象的方法，就会进入 intercept()
                    public Object intercept(Object instance, Method method, Object[] args, MethodProxy proxy) throws Throwable {
                        ClientRequest clientRequest = new ClientRequest();
                        clientRequest.setContent(args[0]); //把方法参数放进请求里
                        String command =clazz.getSimpleName() + "." + method.getName();
                        clientRequest.setCommand(command);
                        Response response = NettyClient.send(clientRequest);
                        return response;
                    }
                });
                try {
                    field.set(bean, enhancer.create()); //把生成的代理对象塞回原来的字段里
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return bean;
    }

    public Object postProcessAfterInitialization(Object bean, String beanName){
        return bean;
    }
}
