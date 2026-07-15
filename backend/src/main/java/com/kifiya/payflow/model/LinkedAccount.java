package com.kifiya.payflow.model;
import java.time.Instant;
public record LinkedAccount(
    String id,
    String ownerId,
    String provider,
    AccountType accountType,
    String accountAlias,
    String maskedReference,
    double availableBalance,
    boolean verified,
    boolean smartPayEnabled,
    boolean smartSettlementEnabled,
    Instant createdAt
) {}
