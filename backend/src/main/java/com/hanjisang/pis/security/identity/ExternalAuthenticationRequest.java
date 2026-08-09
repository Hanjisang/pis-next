package com.hanjisang.pis.security.identity;

public record ExternalAuthenticationRequest(String hospitalProfileCode, String providerCode,
        String assertionReference, String correlationId) {
}
