package uk.gov.justice.digital.hmpps.keyworker.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter

class AuthAwareTokenConverter : Converter<Jwt, AbstractAuthenticationToken> {

  private val jwtGrantedAuthoritiesConverter: Converter<Jwt, Collection<GrantedAuthority>> = JwtGrantedAuthoritiesConverter()

  override fun convert(jwt: Jwt): AbstractAuthenticationToken {
    val claims = jwt.claims
    val principal = findPrincipal(claims)
    val authorities = extractAuthorities(jwt)
    return AuthAwareAuthenticationToken(jwt, principal, authorities)
  }

  private fun findPrincipal(claims: Map<String, Any>): Any? {
    return if (claims.containsKey("user_name")) {
      claims["user_name"]
    } else {
      claims["client_id"]
    }
  }

  private fun extractAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
    val authorities = HashSet(jwtGrantedAuthoritiesConverter.convert(jwt))
    if (jwt.claims.containsKey("authorities")) {
      authorities.addAll(
        (jwt.claims["authorities"] as Collection<String>)
          .map { role: String? -> SimpleGrantedAuthority(role) }
      )
    }
    return authorities.toSet()
  }
}
