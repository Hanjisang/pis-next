package com.hanjisang.pis.security.identity;

import org.springframework.stereotype.Component;

@Component
public class OAuthIdentityProviderAdapter extends UnavailableIdentityProviderAdapter {
    @Override public String adapterCode() { return "OAUTH_IDENTITY"; }
    @Override public IdentityProviderType providerType() { return IdentityProviderType.OAUTH; }
}
