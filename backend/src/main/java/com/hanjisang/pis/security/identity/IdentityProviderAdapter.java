package com.hanjisang.pis.security.identity;

public interface IdentityProviderAdapter {

    String adapterCode();

    IdentityProviderType providerType();

    AuthenticationResult authenticate(ExternalAuthenticationRequest request);

    record AuthenticationResult(boolean authenticated, ExternalIdentityPrincipal principal, String errorCode,
            String errorMessage) {
        public static AuthenticationResult authenticated(ExternalIdentityPrincipal principal) {
            return new AuthenticationResult(true, principal, null, null);
        }

        public static AuthenticationResult rejected(String errorCode, String errorMessage) {
            return new AuthenticationResult(false, null, errorCode, errorMessage);
        }
    }
}
