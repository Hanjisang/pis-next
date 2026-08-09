package com.hanjisang.pis.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hanjisang.pis.security.identity.ExternalAuthenticationRequest;
import com.hanjisang.pis.security.identity.ExternalIdentityIntegrationService;
import com.hanjisang.pis.security.identity.ExternalIdentityLinkStore;
import com.hanjisang.pis.security.identity.IdentityProviderAdapterRegistry;
import com.hanjisang.pis.security.identity.LdapIdentityProviderAdapter;
import com.hanjisang.pis.security.identity.MockIdentityProviderAdapter;

class IdentityIntegrationTest {

    @Test
    void mappedExternalIdentityResolvesExistingUserDoctorAndOrganization() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        OrganizationContext organization = new OrganizationContext(UUID.randomUUID(), "HOSPITAL_A",
                UUID.randomUUID(), "MAIN", departmentId, "PATHOLOGY", "病理科");
        DoctorIdentity doctor = new DoctorIdentity(UUID.randomUUID(), userId, "DOC-A", "Doctor A", "主治医师",
                "PATHOLOGY", departmentId, true);
        AuthenticatedUser user = new AuthenticatedUser(userId, "doctor-a", "Doctor A", "DOCTOR", "HOSPITAL_A",
                "PATHOLOGY", "P19-DIAGNOSIS-REPORT", Set.of("P14-PERM-034"), doctor, organization);
        InMemoryIdentityStore store = new InMemoryIdentityStore(Map.of(userId, user));
        store.link("HOSPITAL_A", "MOCK-LOCAL", "doctor-a", userId, "ADMIN", Instant.now());
        ExternalIdentityIntegrationService service = new ExternalIdentityIntegrationService(
                new IdentityProviderAdapterRegistry(List.of(new MockIdentityProviderAdapter(),
                        new LdapIdentityProviderAdapter())), store, store);

        AuthenticatedUser resolved = service.authenticate("MOCK_IDENTITY",
                new ExternalAuthenticationRequest("HOSPITAL_A", "MOCK-LOCAL", "mock://subject/doctor-a",
                        "CORR-IDENTITY-001"));

        assertThat(resolved.userId()).isEqualTo(userId);
        assertThat(resolved.doctorIdentity().doctorCode()).isEqualTo("DOC-A");
        assertThat(resolved.organization().departmentId()).isEqualTo(departmentId);
        assertThat(new DoctorIdentityResolver().actorReference(resolved)).isEqualTo(doctor.id().toString());
    }

    @Test
    void unmappedOrUnconfiguredProviderCannotGrantPisResponsibility() {
        InMemoryIdentityStore store = new InMemoryIdentityStore(Map.of());
        ExternalIdentityIntegrationService service = new ExternalIdentityIntegrationService(
                new IdentityProviderAdapterRegistry(List.of(new MockIdentityProviderAdapter(),
                        new LdapIdentityProviderAdapter())), store, store);
        ExternalAuthenticationRequest request = new ExternalAuthenticationRequest("HOSPITAL_A", "MOCK-LOCAL",
                "mock://subject/unmapped", "CORR-IDENTITY-002");

        assertThatThrownBy(() -> service.authenticate("MOCK_IDENTITY", request))
                .isInstanceOf(P15BusinessException.class)
                .hasMessageContaining("尚未映射");
        assertThatThrownBy(() -> service.authenticate("LDAP_IDENTITY", request))
                .isInstanceOf(P15BusinessException.class)
                .hasMessageContaining("尚未配置");
    }

    private static final class InMemoryIdentityStore implements ExternalIdentityLinkStore,
            AuthenticatedUserDirectory {

        private final Map<String, UUID> links = new HashMap<>();
        private final Map<UUID, AuthenticatedUser> users;

        private InMemoryIdentityStore(Map<UUID, AuthenticatedUser> users) {
            this.users = users;
        }

        @Override
        public Optional<UUID> findUserId(String hospitalProfileCode, String providerCode, String externalSubject) {
            return Optional.ofNullable(links.get(key(hospitalProfileCode, providerCode, externalSubject)));
        }

        @Override
        public void link(String hospitalProfileCode, String providerCode, String externalSubject, UUID userId,
                String linkedByRef, Instant linkedAt) {
            links.put(key(hospitalProfileCode, providerCode, externalSubject), userId);
        }

        @Override
        public Optional<AuthenticatedUser> find(UUID userId) {
            return Optional.ofNullable(users.get(userId));
        }

        private static String key(String hospitalProfileCode, String providerCode, String externalSubject) {
            return hospitalProfileCode + "|" + providerCode + "|" + externalSubject;
        }
    }
}
