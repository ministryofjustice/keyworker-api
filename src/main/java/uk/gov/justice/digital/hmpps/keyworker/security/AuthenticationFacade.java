package uk.gov.justice.digital.hmpps.keyworker.security;

import org.springframework.security.core.Authentication;

public interface AuthenticationFacade {
    Authentication getAuthentication();

    String getCurrentUsername();

    boolean isIdentifiedAuthentication();
}
