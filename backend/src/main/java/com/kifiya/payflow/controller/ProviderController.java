package com.kifiya.payflow.controller;

import com.kifiya.payflow.model.*;
import com.kifiya.payflow.service.ProviderRegistry;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/providers")
@CrossOrigin(origins = "*")
public class ProviderController {
    private final ProviderRegistry providers;
    public ProviderController(ProviderRegistry providers) { this.providers = providers; }

    @GetMapping
    public List<Provider> list() { return providers.all(); }

    @PostMapping("/{code}/mode")
    public Provider mode(@PathVariable String code, @Valid @RequestBody ProviderModeRequest request) {
        return providers.updateMode(code, request.mode());
    }
}
