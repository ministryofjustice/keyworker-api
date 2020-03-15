package uk.gov.justice.digital.hmpps.keyworker.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// TODO DT-611 Unit tests
public class AuthAwareTokenConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    public AbstractAuthenticationToken convert(final Jwt jwt) {
        final var claims = jwt.getClaims();

        final var principal = findPrincipal(claims);
        final var authorities = extractAuthorities(jwt);
        return new AuthAwareAuthenticationToken(jwt, principal, authorities);
    }

    private String findPrincipal(final Map<String, Object> claims) {
        if (claims.containsKey("user_name")) {
            return (String)claims.get("user_name");
        } else {
            return (String)claims.get("client_id");
        }
    }

    @SuppressWarnings("ConstantConditions")
    private Collection<GrantedAuthority> extractAuthorities(final Jwt jwt) {
        final var authorities = new HashSet<>(this.jwtGrantedAuthoritiesConverter.convert(jwt));
        if (jwt.getClaims().containsKey("authorities")) {
            authorities.addAll(
                    ((Collection<String>) jwt.getClaims().get("authorities"))
                            .stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toSet()));
        }
        return Set.copyOf(authorities);
    }
}
