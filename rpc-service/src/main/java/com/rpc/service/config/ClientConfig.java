package com.rpc.service.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {
        "client",
        "com/rpc/service"
})
public class ClientConfig {
}