package com.kifiya.payflow.model;
public record Provider(
    String code,
    String name,
    double successRate,
    double healthScore,
    double costScore,
    double latencyScore,
    long latencyMs,
    ProviderMode mode
) {
    public Provider withMode(ProviderMode newMode) {
        return switch (newMode) {
            case HEALTHY -> new Provider(code,name,0.91,0.98,costScore,0.90,500,newMode);
            case SLOW -> new Provider(code,name,0.82,0.70,costScore,0.25,4000,newMode);
            case UNAVAILABLE -> new Provider(code,name,0,0,costScore,0,50,newMode);
            case UNSTABLE -> new Provider(code,name,0.45,0.45,costScore,0.40,1500,newMode);
        };
    }
}
