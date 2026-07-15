package com.kifiya.payflow.service;

import com.kifiya.payflow.model.*;
import org.springframework.stereotype.Service;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProviderSimulator {
    public Result charge(Provider provider) {
        try { Thread.sleep(Math.min(provider.latencyMs(), 1200)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        if (provider.mode() == ProviderMode.UNAVAILABLE)
            return new Result(false, "PROVIDER_UNAVAILABLE", provider.latencyMs());

        boolean ok = ThreadLocalRandom.current().nextDouble() <= provider.successRate();
        return ok
            ? new Result(true, null, provider.latencyMs())
            : new Result(false, provider.mode() == ProviderMode.SLOW ? "TIMEOUT" : "PROVIDER_ERROR", provider.latencyMs());
    }

    public record Result(boolean success, String errorCode, long latencyMs) {}
}
