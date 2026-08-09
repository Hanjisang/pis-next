package com.hanjisang.pis.security;

import java.util.Optional;

import org.springframework.stereotype.Component;

/** Explicit boundary from the authenticated application user to pathology doctor identity. */
@Component
public class DoctorIdentityResolver {

    public Optional<DoctorIdentity> resolve(AuthenticatedUser user) {
        return user == null || user.doctorIdentity() == null
                ? Optional.empty()
                : Optional.of(user.doctorIdentity());
    }

    public String actorReference(AuthenticatedUser user) {
        return resolve(user).map(doctor -> doctor.id().toString()).orElse(user.userId().toString());
    }
}
