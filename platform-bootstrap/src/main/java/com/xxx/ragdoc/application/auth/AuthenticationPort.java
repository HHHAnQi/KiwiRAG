package com.xxx.ragdoc.application.auth;

import com.xxx.ragdoc.domain.auth.Principal;
import java.util.Optional;

/**
 * Inbound authentication port. Infrastructure adapters resolve credentials into domain principals.
 */
public interface AuthenticationPort {

    Optional<Principal> authenticate(String token);
}
