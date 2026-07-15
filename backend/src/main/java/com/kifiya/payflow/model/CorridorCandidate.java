package com.kifiya.payflow.model;
public record CorridorCandidate(
    String sourceAccountId,
    String sourceProvider,
    String routeProvider,
    String destinationAccountId,
    String destinationProvider,
    double reliabilityScore,
    double successProbability,
    double costScore,
    double latencyScore,
    double finalScore,
    boolean selected,
    String explanation
) {}
