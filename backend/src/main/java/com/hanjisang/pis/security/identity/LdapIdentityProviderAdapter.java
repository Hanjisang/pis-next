package com.hanjisang.pis.security.identity;

import org.springframework.stereotype.Component;

@Component
public class LdapIdentityProviderAdapter extends UnavailableIdentityProviderAdapter {
    @Override public String adapterCode() { return "LDAP_IDENTITY"; }
    @Override public IdentityProviderType providerType() { return IdentityProviderType.LDAP; }
}
