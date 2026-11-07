package com.rpc.api.service;

import com.rpc.api.model.User;

import java.util.List;

public interface UserRemote {

    User saveUser(User user);

    List<User> saveUsers(List<User> users);

    String findUser(long id);

    String findUser(long id, String name);
}
