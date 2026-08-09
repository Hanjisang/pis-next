package com.hanjisang.pis.security.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ExternalIdentityLinkStore {

    Optional<UUID> findUserId(String hospitalProfileCode, String providerCode, String externalSubject);

    void link(String hospitalProfileCode, String providerCode, String externalSubject, UUID userId,
            String linkedByRef, Instant linkedAt);
}
