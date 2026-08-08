package com.hanjisang.pis.v2.report.api;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class V2ReportExtensionRegistry {

    private final List<V2ReportDataSource> dataSources;

    public V2ReportExtensionRegistry(List<V2ReportDataSource> dataSources) {
        this.dataSources = List.copyOf(dataSources);
    }

    public List<String> registeredCodes() { return dataSources.stream().map(V2ReportDataSource::code).sorted().toList(); }
}
