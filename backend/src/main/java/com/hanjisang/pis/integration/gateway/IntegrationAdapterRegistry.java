package com.hanjisang.pis.integration.gateway;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class IntegrationAdapterRegistry {

    private final Map<String, IntegrationAdapter> adapters;

    public IntegrationAdapterRegistry(List<IntegrationAdapter> adapters) {
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(IntegrationAdapter::adapterCode,
                Function.identity()));
    }

    public IntegrationAdapter require(String adapterCode, IntegrationCapability capability) {
        IntegrationAdapter adapter = adapters.get(adapterCode);
        if (adapter == null) throw new IllegalArgumentException("未配置集成适配器：" + adapterCode);
        if (!adapter.supports(capability)) {
            throw new IllegalArgumentException("适配器不支持接口能力：" + adapterCode + "/" + capability);
        }
        return adapter;
    }
}
