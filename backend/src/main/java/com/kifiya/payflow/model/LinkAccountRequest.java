package com.kifiya.payflow.model;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
public record LinkAccountRequest(
    @NotBlank String provider,
    @NotNull AccountType accountType,
    @NotBlank String accountAlias,
    @NotBlank String accountReference,
    @PositiveOrZero double availableBalance,
    boolean smartPayEnabled,
    boolean smartSettlementEnabled
) {}
