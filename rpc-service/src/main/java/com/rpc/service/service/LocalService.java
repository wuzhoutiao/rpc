package com.rpc.service.service;

import org.springframework.stereotype.Service;

@Service
public class LocalService {

    public String hello() {
        return "本地服务调用成功";
    }
}
