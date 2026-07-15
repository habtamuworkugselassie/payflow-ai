package com.kifiya.payflow.controller;

import com.kifiya.payflow.model.*;
import com.kifiya.payflow.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = "*")
public class UserController {
    private final UserService users;
    private final AccountService accounts;

    public UserController(UserService users, AccountService accounts) {
        this.users = users;
        this.accounts = accounts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User create(@Valid @RequestBody CreateUserRequest request) {
        return users.create(request);
    }

    @GetMapping
    public List<User> list() {
        return users.all();
    }

    @PostMapping("/{userId}/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkedAccount linkAccount(
        @PathVariable String userId,
        @Valid @RequestBody LinkAccountRequest request
    ) {
        return accounts.link(userId, request);
    }

    @GetMapping("/{userId}/accounts")
    public List<LinkedAccount> listAccounts(@PathVariable String userId) {
        return accounts.byOwner(userId);
    }
}
