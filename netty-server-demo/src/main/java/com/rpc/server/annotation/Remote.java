package com.rpc.server.annotation;


import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 标记需要暴露的 RPC 服务，同时作为 Spring 组件注解。
 */
@Component
@Target(ElementType.TYPE)
@Documented
@Retention(RetentionPolicy.RUNTIME) //保留到运行时，让运行时能反射读取
public @interface Remote {
}
