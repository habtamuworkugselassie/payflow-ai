package com.kifiya.payflow.controller;

import com.kifiya.payflow.model.*;
import com.kifiya.payflow.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@CrossOrigin(origins = "*")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SmartPayment create(
        @RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody SmartPaymentRequest request
    ) {
        return payments.create(request, key);
    }

    @GetMapping
    public List<SmartPayment> list() {
        return payments.all();
    }
}
