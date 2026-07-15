package com.kifiya.payflow.service;

import com.kifiya.payflow.model.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {
    private final Map<String, LinkedAccount> accounts = new ConcurrentHashMap<>();
    private final UserService userService;

    public AccountService(UserService userService) {
        this.userService = userService;
    }

    public LinkedAccount link(String userId, LinkAccountRequest request) {
        userService.get(userId);

        String id = "acc_" + UUID.randomUUID().toString().replace("-","").substring(0,10);
        LinkedAccount account = new LinkedAccount(
            id,
            userId,
            request.provider().trim().toUpperCase(),
            request.accountType(),
            request.accountAlias(),
            mask(request.accountReference()),
            request.availableBalance(),
            true,
            request.smartPayEnabled(),
            request.smartSettlementEnabled(),
            Instant.now()
        );
        accounts.put(id, account);
        return account;
    }

    public List<LinkedAccount> byOwner(String ownerId) {
        userService.get(ownerId);
        return accounts.values().stream()
            .filter(a -> a.ownerId().equals(ownerId))
            .sorted(Comparator.comparing(LinkedAccount::createdAt))
            .toList();
    }

    public LinkedAccount get(String id) {
        LinkedAccount account = accounts.get(id);
        if (account == null) throw new NoSuchElementException("Account not found: " + id);
        return account;
    }

    private String mask(String reference) {
        String clean = reference.trim();
        if (clean.length() <= 4) return "****";
        return "*".repeat(Math.max(4, clean.length() - 4)) + clean.substring(clean.length() - 4);
    }
}
