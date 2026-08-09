package com.hanjisang.pis.security.identity;

import org.springframework.stereotype.Service;

import com.hanjisang.pis.security.AuthenticatedUser;
import com.hanjisang.pis.security.AuthenticatedUserDirectory;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.security.identity.IdentityProviderAdapter.AuthenticationResult;

@Service
public class ExternalIdentityIntegrationService {

    private final IdentityProviderAdapterRegistry adapters;
    private final ExternalIdentityLinkStore links;
    private final AuthenticatedUserDirectory users;

    public ExternalIdentityIntegrationService(IdentityProviderAdapterRegistry adapters,
            ExternalIdentityLinkStore links, AuthenticatedUserDirectory users) {
        this.adapters = adapters;
        this.links = links;
        this.users = users;
    }

    public AuthenticatedUser authenticate(String adapterCode, ExternalAuthenticationRequest request) {
        IdentityProviderAdapter adapter = adapters.require(adapterCode);
        AuthenticationResult result = adapter.authenticate(request);
        if (!result.authenticated() || result.principal() == null) {
            throw new P15BusinessException(result.errorCode() == null ? "EXTERNAL_AUTH_REJECTED" : result.errorCode(),
                    result.errorMessage() == null ? "外部认证失败" : result.errorMessage(), 401);
        }
        ExternalIdentityPrincipal principal = result.principal();
        if (!request.providerCode().equals(principal.providerCode())
                || !request.hospitalProfileCode().equals(principal.hospitalProfileCode())) {
            throw new P15BusinessException("EXTERNAL_AUTH_SCOPE_MISMATCH", "外部认证组织范围不匹配", 403);
        }
        AuthenticatedUser user = links.findUserId(request.hospitalProfileCode(), request.providerCode(),
                principal.externalSubject()).flatMap(users::find)
                .orElseThrow(() -> new P15BusinessException("EXTERNAL_IDENTITY_UNMAPPED",
                        "外部身份尚未映射到 PIS 用户", 403));
        if (!request.hospitalProfileCode().equals(user.hospitalScope())) {
            throw new P15BusinessException("EXTERNAL_IDENTITY_USER_SCOPE_MISMATCH", "PIS 用户组织范围不匹配", 403);
        }
        return user;
    }
}
