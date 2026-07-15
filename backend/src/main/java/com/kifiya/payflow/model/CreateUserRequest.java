package com.kifiya.payflow.model;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record CreateUserRequest(@NotBlank String name, @NotBlank String phoneNumber, @NotNull UserRole role) {}
