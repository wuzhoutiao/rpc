package annotation;


import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Component //让Spring能扫描到这个类
@Target(value = {ElementType.TYPE,ElementType.METHOD}) //这个注解可以贴在类和方法上
@Documented
@Retention(RetentionPolicy.RUNTIME) //保留到运行时，让运行时能反射读取
public @interface Remote {
    String value() default ""; //给注解定义参数
}
