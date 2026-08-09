package com.hanjisang.pis.security.identity;

import org.springframework.stereotype.Component;

@Component
public class ActiveDirectoryIdentityProviderAdapter extends UnavailableIdentityProviderAdapter {
    @Override public String adapterCode() { return "AD_IDENTITY"; }
    @Override public IdentityProviderType providerType() { return IdentityProviderType.ACTIVE_DIRECTORY; }
}
