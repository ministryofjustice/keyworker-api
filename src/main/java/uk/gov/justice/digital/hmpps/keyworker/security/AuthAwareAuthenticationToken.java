package uk.gov.justice.digital.hmpps.keyworker.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

@Getter
public class AuthAwareAuthenticationToken extends JwtAuthenticationToken {
    private final Object principal;
    private final String name;

    AuthAwareAuthenticationToken(final Jwt jwt, final Object principal, final Collection<? extends GrantedAuthority> authorities) {
        super(jwt, authorities);
        this.principal = principal;
        this.name = principal != null ? principal.toString() : "";
    }
}
