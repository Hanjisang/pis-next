package com.hanjisang.pis.security.identity;

import org.springframework.stereotype.Component;

@Component
public class SsoIdentityProviderAdapter extends UnavailableIdentityProviderAdapter {
    @Override public String adapterCode() { return "SSO_IDENTITY"; }
    @Override public IdentityProviderType providerType() { return IdentityProviderType.SSO; }
}
