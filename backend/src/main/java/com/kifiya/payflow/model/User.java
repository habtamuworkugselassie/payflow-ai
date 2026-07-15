package com.kifiya.payflow.model;
import java.time.Instant;
public record User(String id, String name, String phoneNumber, UserRole role, Instant createdAt) {}
