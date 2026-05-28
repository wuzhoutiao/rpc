package client.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Component
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Remote {
    String value() default "";
}
