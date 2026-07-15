package com.kifiya.payflow.model;
import java.time.Instant;
public record PaymentAttempt(int attemptNumber, String routeProvider, String status, String errorCode, long latencyMs, Instant createdAt) {}
