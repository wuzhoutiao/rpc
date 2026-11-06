package com.rpc.api.service;

import com.rpc.api.model.User;
import com.rpc.api.protocol.Response;

import java.util.List;

public interface UserRemote {

    Response saveUser(User user);

    Response saveUsers(List<User> users);
}
