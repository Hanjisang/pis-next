package com.hanjisang.pis.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProductionAuthorizationConfigurationTest {

    @AfterEach
    void clearAuthentication() {
        AuthenticationContext.clear();
    }

    @Test
    void authenticatedProductionUserIsAllowedOnlyWhenProductionIsExplicitlyTrusted() {
        AuthenticationContext.set(user("P14-PERM-034"));
        P15AuthorizationService trustedProduction = service("production", "local,test,production");
        P15AuthorizationService untrustedPreview = service("preview", "local,test,production");

        assertThat(trustedProduction.decide("P14-PERM-034").allowed()).isTrue();
        assertThat(untrustedPreview.decide("P14-PERM-034").reason())
                .isEqualTo("P14-ENVIRONMENT-NOT-TRUSTED");
    }

    private static P15AuthorizationService service(String runtimeEnvironment, String trustedEnvironments) {
        return new P15AuthorizationService(runtimeEnvironment, "fallback", "P14-PERM-034",
                "P19-DIAGNOSIS-REPORT", "HUMAN_USER", trustedEnvironments, true,
                new DoctorIdentityResolver());
    }

    private static AuthenticatedUser user(String permission) {
        UUID userId = UUID.randomUUID();
        DoctorIdentity doctor = new DoctorIdentity(UUID.randomUUID(), userId, "DOC-PROD", "合成医生",
                "审核医师", "PATHOLOGY", UUID.randomUUID(), true);
        return new AuthenticatedUser(userId, "synthetic-production-doctor", "合成医生", "DOCTOR",
                "HOSPITAL_A", "PATHOLOGY", "P19-DIAGNOSIS-REPORT", Set.of(permission), doctor, null);
    }
}
