package com.kifiya.payflow.service;

import com.kifiya.payflow.model.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProviderRegistry {
    private final Map<String, Provider> providers = new ConcurrentHashMap<>();

    public ProviderRegistry() {
        providers.put("ROUTE_A", new Provider("ROUTE_A","Route A",0.92,0.98,0.82,0.91,500,ProviderMode.HEALTHY));
        providers.put("ROUTE_B", new Provider("ROUTE_B","Route B",0.86,0.95,0.96,0.73,900,ProviderMode.HEALTHY));
        providers.put("ROUTE_C", new Provider("ROUTE_C","Route C",0.89,0.96,0.75,0.85,700,ProviderMode.HEALTHY));
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
        Provider updated = get(code).withMode(mode);
        providers.put(code, updated);
        return updated;
    }
}
