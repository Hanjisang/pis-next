package com.hanjisang.pis.security;

import java.util.Optional;

public final class AuthenticationContext {

    private static final ThreadLocal<AuthenticatedUser> CURRENT = new ThreadLocal<>();

    private AuthenticationContext() { }

    public static void set(AuthenticatedUser user) { CURRENT.set(user); }

    public static Optional<AuthenticatedUser> current() { return Optional.ofNullable(CURRENT.get()); }

    public static void clear() { CURRENT.remove(); }
}
