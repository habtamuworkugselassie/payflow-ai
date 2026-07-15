package com.kifiya.payflow.service;

import com.kifiya.payflow.model.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class CorridorRoutingService {
    private final AccountService accountService;
    private final ProviderRegistry providerRegistry;

    public CorridorRoutingService(AccountService accountService, ProviderRegistry providerRegistry) {
        this.accountService = accountService;
        this.providerRegistry = providerRegistry;
    }

    public List<CorridorCandidate> rank(SmartPaymentRequest request, Set<String> excludedRoutes) {
        List<LinkedAccount> sources = resolveSources(request);
        List<LinkedAccount> destinations = resolveDestinations(request);
        List<CorridorCandidate> result = new ArrayList<>();

        for (LinkedAccount source : sources) {
            if (!source.verified() || source.availableBalance() < request.amount().doubleValue()) continue;

            for (LinkedAccount destination : destinations) {
                if (!destination.verified()) continue;

                for (Provider route : providerRegistry.all()) {
                    if (excludedRoutes.contains(route.code()) || route.mode() == ProviderMode.UNAVAILABLE) continue;
                    result.add(score(source, route, destination));
                }
            }
        }

        result.sort(Comparator.comparingDouble(CorridorCandidate::finalScore).reversed());
        if (result.isEmpty()) return result;

        CorridorCandidate winner = result.getFirst();
        return result.stream().map(c -> new CorridorCandidate(
            c.sourceAccountId(), c.sourceProvider(), c.routeProvider(),
            c.destinationAccountId(), c.destinationProvider(),
            c.reliabilityScore(), c.successProbability(), c.costScore(),
            c.latencyScore(), c.finalScore(),
            c.sourceAccountId().equals(winner.sourceAccountId())
                && c.routeProvider().equals(winner.routeProvider())
                && c.destinationAccountId().equals(winner.destinationAccountId()),
            c.explanation()
        )).toList();
    }

    private List<LinkedAccount> resolveSources(SmartPaymentRequest request) {
        if (request.smartPay()) {
            return accountService.byOwner(request.senderId()).stream()
                .filter(LinkedAccount::smartPayEnabled).toList();
        }
        if (request.selectedSourceAccountId() == null)
            throw new IllegalArgumentException("selectedSourceAccountId is required");
        LinkedAccount a = accountService.get(request.selectedSourceAccountId());
        validateOwner(a, request.senderId(), "source");
        return List.of(a);
    }

    private List<LinkedAccount> resolveDestinations(SmartPaymentRequest request) {
        if (request.smartSettlement()) {
            return accountService.byOwner(request.receiverId()).stream()
                .filter(LinkedAccount::smartSettlementEnabled).toList();
        }
        if (request.selectedDestinationAccountId() == null)
            throw new IllegalArgumentException("selectedDestinationAccountId is required");
        LinkedAccount a = accountService.get(request.selectedDestinationAccountId());
        validateOwner(a, request.receiverId(), "destination");
        return List.of(a);
    }

    private void validateOwner(LinkedAccount account, String userId, String role) {
        if (!account.ownerId().equals(userId))
            throw new IllegalArgumentException("Selected " + role + " account does not belong to user");
    }

    private CorridorCandidate score(LinkedAccount source, Provider route, LinkedAccount destination) {
        double affinity = source.provider().equals(destination.provider()) ? 0.98 : 0.86;
        double reliability =
            0.45 * route.successRate() +
            0.25 * route.healthScore() +
            0.15 * route.latencyScore() +
            0.15 * affinity;

        double successProbability =
            0.55 * route.successRate() +
            0.25 * affinity +
            0.20 * route.healthScore();

        double finalScore =
            0.45 * reliability +
            0.25 * successProbability +
            0.15 * route.costScore() +
            0.10 * route.latencyScore() +
            0.05 * affinity;

        String explanation = String.format(
            "%s -> %s -> %s scored %.1f/100",
            source.provider(), route.code(), destination.provider(), finalScore * 100
        );

        return new CorridorCandidate(
            source.id(), source.provider(), route.code(),
            destination.id(), destination.provider(),
            reliability, successProbability, route.costScore(),
            route.latencyScore(), finalScore, false, explanation
        );
    }
}
