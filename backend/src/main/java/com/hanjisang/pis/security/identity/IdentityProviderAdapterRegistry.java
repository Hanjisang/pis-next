package com.hanjisang.pis.security.identity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class IdentityProviderAdapterRegistry {

    private final Map<String, IdentityProviderAdapter> adapters;

    public IdentityProviderAdapterRegistry(List<IdentityProviderAdapter> adapters) {
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(IdentityProviderAdapter::adapterCode,
                Function.identity()));
    }

    public IdentityProviderAdapter require(String adapterCode) {
        IdentityProviderAdapter adapter = adapters.get(adapterCode);
        if (adapter == null) throw new IllegalArgumentException("身份适配器未配置：" + adapterCode);
        return adapter;
    }
}
