package com.xxx.ragdoc.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xxx.ragdoc.infrastructure.persistence.jpa.entity.PrincipalEntity;
import com.xxx.ragdoc.infrastructure.persistence.jpa.repository.PrincipalJpaRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JpaAuthenticationAdapterTest {

    private final PrincipalJpaRepository repository = mock(PrincipalJpaRepository.class);
    private final JpaAuthenticationAdapter adapter = new JpaAuthenticationAdapter(repository);

    @Test
    void mapsPersistenceEntityToDomainPrincipalAndNormalizesRoles() {
        PrincipalEntity entity = new PrincipalEntity();
        entity.setTenantId("tenant-a");
        entity.setUserId("user-a");
        entity.setToken("token-a");
        entity.setRoles("role:user, role:admin, role:user,  ");
        when(repository.findByToken("token-a")).thenReturn(Optional.of(entity));

        var principal = adapter.authenticate("token-a").orElseThrow();

        assertThat(principal.tenantId()).isEqualTo("tenant-a");
        assertThat(principal.userId()).isEqualTo("user-a");
        assertThat(principal.roles()).containsExactly("role:default", "role:user", "role:admin");
    }

    @Test
    void returnsEmptyForUnknownToken() {
        when(repository.findByToken("unknown")).thenReturn(Optional.empty());

        assertThat(adapter.authenticate("unknown")).isEmpty();
    }
}
