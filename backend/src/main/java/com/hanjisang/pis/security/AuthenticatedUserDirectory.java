package com.hanjisang.pis.security;

import java.util.Optional;
import java.util.UUID;

public interface AuthenticatedUserDirectory {

    Optional<AuthenticatedUser> find(UUID userId);
}
