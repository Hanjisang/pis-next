package com.hanjisang.pis.security.identity;

import java.time.Instant;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class MockIdentityProviderAdapter implements IdentityProviderAdapter {

    @Override
    public String adapterCode() {
        return "MOCK_IDENTITY";
    }

    @Override
    public IdentityProviderType providerType() {
        return IdentityProviderType.MOCK;
    }

    @Override
    public AuthenticationResult authenticate(ExternalAuthenticationRequest request) {
        String prefix = "mock://subject/";
        if (request.assertionReference() == null || !request.assertionReference().startsWith(prefix)) {
            return AuthenticationResult.rejected("MOCK_ASSERTION_INVALID", "合成身份断言无效");
        }
        String subject = request.assertionReference().substring(prefix.length()).trim();
        if (subject.isBlank()) return AuthenticationResult.rejected("MOCK_SUBJECT_MISSING", "合成主体为空");
        return AuthenticationResult.authenticated(new ExternalIdentityPrincipal(request.providerCode(), subject,
                subject, Set.of("SYNTHETIC_PIS_USER"), request.hospitalProfileCode(), "MAIN", "PATHOLOGY",
                Instant.now()));
    }
}
