package uk.gov.justice.digital.hmpps.keyworker.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

internal class AuthAwareAuthenticationToken(jwt: Jwt, private val principal: Any?, authorities: Collection<GrantedAuthority>) : JwtAuthenticationToken(jwt, authorities) {

    private val name: String =  principal?.toString() ?: ""

    override fun getPrincipal(): Any? {
        return this.principal
    }

    override fun getName(): String {
        return this.name
    }

}