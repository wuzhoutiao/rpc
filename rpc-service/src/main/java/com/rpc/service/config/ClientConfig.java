package com.rpc.service.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {
        "com.rpc.client",
        "com.rpc.service"
})
public class ClientConfig {
}
