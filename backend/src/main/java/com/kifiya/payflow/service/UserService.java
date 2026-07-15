package com.kifiya.payflow.service;

import com.kifiya.payflow.model.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {
    private final Map<String, User> users = new ConcurrentHashMap<>();

    public User create(CreateUserRequest request) {
        String id = "usr_" + UUID.randomUUID().toString().replace("-","").substring(0,10);
        User user = new User(id, request.name(), request.phoneNumber(), request.role(), Instant.now());
        users.put(id, user);
        return user;
    }

    public List<User> all() {
        return users.values().stream().sorted(Comparator.comparing(User::createdAt)).toList();
    }

    public User get(String id) {
        User user = users.get(id);
        if (user == null) throw new NoSuchElementException("User not found: " + id);
        return user;
    }
}
