package com.hanjisang.pis.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthenticationSessionFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "PIS_SESSION";

    private final AuthenticationSessionStore sessions;

    public AuthenticationSessionFilter(AuthenticationSessionStore sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = cookie(request, COOKIE_NAME);
        AuthenticatedUser user = sessions.find(token);
        if (user != null) AuthenticationContext.set(user);
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuthenticationContext.clear();
        }
    }

    private static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
