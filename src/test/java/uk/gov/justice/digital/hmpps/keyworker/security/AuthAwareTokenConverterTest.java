package uk.gov.justice.digital.hmpps.keyworker.security;

import org.junit.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthAwareTokenConverterTest {

    private final AuthAwareTokenConverter converter = new AuthAwareTokenConverter();

    @Test
    public void convert_withAuthorities_addedToToken() {
        final var jwt = Jwt.withTokenValue("any")
                .claim("authorities", List.of("some_authority", "some_other_authority"))
                .header("any", "any")
                .build();

        final var token = converter.convert(jwt);

        assertThat(token.getAuthorities())
                .contains(new SimpleGrantedAuthority("some_authority"), new SimpleGrantedAuthority("some_other_authority"));
    }

    @Test
    public void convert_withUserName_principalIsUserName() {
        final var jwt = Jwt.withTokenValue("any")
                .claim("user_name", "some user name")
                .claim("client_id", "some client id")
                .header("any", "any")
                .build();

        final var token = converter.convert(jwt);

        assertThat(token.getPrincipal()).isEqualTo("some user name");
    }

    @Test
    public void convert_withoutUserName_principalIsClientId() {
        final var jwt = Jwt.withTokenValue("any")
                .claim("client_id", "some client id")
                .header("any", "any")
                .build();

        final var token = converter.convert(jwt);

        assertThat(token.getPrincipal()).isEqualTo("some client id");
    }
}
