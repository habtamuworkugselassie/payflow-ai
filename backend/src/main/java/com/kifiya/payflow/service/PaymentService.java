package com.kifiya.payflow.service;

import com.kifiya.payflow.model.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentService {
    private final CorridorRoutingService routing;
    private final ProviderRegistry providers;
    private final ProviderSimulator simulator;
    private final AccountService accountService;
    private final Map<String, SmartPayment> payments = new ConcurrentHashMap<>();
    private final Map<String, String> idempotency = new ConcurrentHashMap<>();

    public PaymentService(CorridorRoutingService routing, ProviderRegistry providers, ProviderSimulator simulator, AccountService accountService) {
        this.routing = routing;
        this.providers = providers;
        this.simulator = simulator;
        this.accountService = accountService;
    }

    public SmartPayment create(SmartPaymentRequest request, String key) {
        String paymentId = idempotency.computeIfAbsent(key, ignored -> createPayment(request, key).id());
        return payments.get(paymentId);
    }

    private SmartPayment createPayment(SmartPaymentRequest request, String key) {
        String id = "pay_" + UUID.randomUUID().toString().replace("-","").substring(0,10);
        Set<String> excluded = new HashSet<>();
        List<PaymentAttempt> attempts = new ArrayList<>();
        List<CorridorCandidate> candidates = List.of();
        CorridorCandidate selected = null;
        PaymentStatus status = PaymentStatus.FAILED;

        for (int i = 1; i <= 3; i++) {
            candidates = routing.rank(request, excluded);
            if (candidates.isEmpty()) break;

            selected = candidates.getFirst();
            ProviderSimulator.Result result = simulator.charge(providers.get(selected.routeProvider()));

            attempts.add(new PaymentAttempt(
                i, selected.routeProvider(),
                result.success() ? "SUCCEEDED" : "FAILED",
                result.errorCode(), result.latencyMs(), Instant.now()
            ));

            if (result.success()) {
                status = PaymentStatus.SUCCEEDED;
                accountService.debit(selected.sourceAccountId(), request.amount().doubleValue());
                break;
            }
            excluded.add(selected.routeProvider());
        }

        SmartPayment payment = new SmartPayment(
            id, request.merchantReference(), request.amount(), request.currency(),
            request.senderId(), request.receiverId(),
            selected == null ? null : selected.sourceAccountId(),
            selected == null ? null : selected.destinationAccountId(),
            selected == null ? null : selected.routeProvider(),
            status, key, List.copyOf(attempts), List.copyOf(candidates), Instant.now()
        );

        payments.put(id, payment);
        return payment;
    }

    public List<SmartPayment> all() {
        return payments.values().stream().sorted(Comparator.comparing(SmartPayment::createdAt).reversed()).toList();
    }
}
