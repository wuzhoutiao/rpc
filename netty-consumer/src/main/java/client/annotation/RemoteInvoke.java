package client.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Component
@Target(ElementType.FIELD) //定义这个注解只能加在成员变量上
@Retention(RetentionPolicy.RUNTIME)
@Documented //这个注解会出现在JavaDoc文档中
public @interface RemoteInvoke {
  String value() default "";
}

