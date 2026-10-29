package com.xxx.ragdoc.infrastructure.auth;

import com.xxx.ragdoc.application.auth.AuthenticationPort;
import com.xxx.ragdoc.domain.auth.Principal;
import com.xxx.ragdoc.infrastructure.persistence.jpa.repository.PrincipalJpaRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** JPA-backed authentication adapter. Keeps persistence entities outside the web layer. */
@Component
@RequiredArgsConstructor
public class JpaAuthenticationAdapter implements AuthenticationPort {

    private final PrincipalJpaRepository repository;

    @Override
    public java.util.Optional<Principal> authenticate(String token) {
        return repository
                .findByToken(token)
                .map(
                        entity ->
                                new Principal(
                                        entity.getTenantId(),
                                        entity.getUserId(),
                                        parseRoles(entity.getRoles()),
                                        entity.getToken()));
    }

    private static Set<String> parseRoles(String csv) {
        Set<String> roles = new LinkedHashSet<>();
        roles.add("role:default");
        if (org.springframework.util.StringUtils.hasText(csv)) {
            for (String role : csv.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) roles.add(trimmed);
            }
        }
        return roles;
    }
}
