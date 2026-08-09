package com.hanjisang.pis.security.identity;

abstract class UnavailableIdentityProviderAdapter implements IdentityProviderAdapter {

    @Override
    public AuthenticationResult authenticate(ExternalAuthenticationRequest request) {
        return AuthenticationResult.rejected("IDENTITY_PROVIDER_NOT_CONFIGURED",
                providerType() + " 真实身份提供方尚未配置");
    }
}
