package com.kifiya.payflow.service;

import com.kifiya.payflow.model.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProviderRegistry {
    private final Map<String, Provider> providers = new ConcurrentHashMap<>();
    private final Map<String, Provider> healthyBaselines = new ConcurrentHashMap<>();

    public ProviderRegistry() {
        seed(new Provider("ROUTE_A","Route A",0.92,0.98,0.82,0.91,500,ProviderMode.HEALTHY));
        seed(new Provider("ROUTE_B","Route B",0.86,0.95,0.96,0.73,900,ProviderMode.HEALTHY));
        seed(new Provider("ROUTE_C","Route C",0.89,0.96,0.75,0.85,700,ProviderMode.HEALTHY));
    }

    public List<Provider> all() {
        return providers.values().stream().sorted(Comparator.comparing(Provider::code)).toList();
    }

    public Provider get(String code) {
        Provider p = providers.get(code);
        if (p == null) throw new NoSuchElementException("Provider not found: " + code);
        return p;
    }

    public Provider updateMode(String code, ProviderMode mode) {
        Provider updated = get(code).withMode(mode, healthyBaseline(code));
        providers.put(code, updated);
        return updated;
    }

    private void seed(Provider provider) {
        providers.put(provider.code(), provider);
        healthyBaselines.put(provider.code(), provider);
    }

    private Provider healthyBaseline(String code) {
        Provider provider = healthyBaselines.get(code);
        if (provider == null) throw new NoSuchElementException("Provider not found: " + code);
        return provider;
    }
}
