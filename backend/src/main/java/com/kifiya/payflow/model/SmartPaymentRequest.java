package com.kifiya.payflow.model;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record SmartPaymentRequest(
    @NotBlank String merchantReference,
    @NotNull @DecimalMin("1.00") BigDecimal amount,
    @NotBlank String currency,
    @NotBlank String senderId,
    @NotBlank String receiverId,
    String selectedSourceAccountId,
    String selectedDestinationAccountId,
    boolean smartPay,
    boolean smartSettlement
) {}
