package uk.gov.justice.digital.hmpps.keyworker.utils

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm.RS256
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.util.Date
import java.util.UUID

@Component
class JwtAuthHelper {
  private val keyPair: KeyPair

  init {
    val gen = KeyPairGenerator.getInstance("RSA")
    gen.initialize(2048)
    keyPair = gen.generateKeyPair()
  }

  @Bean
  fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withPublicKey(keyPair.public as RSAPublicKey).build()

  fun createJwt(
    subject: String? = null,
    userId: String? = "${subject}_ID",
    scope: List<String>? = listOf(),
    roles: List<String>? = listOf(),
    expiryTime: Duration = Duration.ofHours(1),
    clientId: String = "keyworker-api",
    jwtId: String = UUID.randomUUID().toString(),
  ): String {
    val claims = mutableMapOf<String, Any?>("user_name" to subject, "client_id" to clientId, "user_id" to userId)
    roles?.let { claims["authorities"] = roles }
    scope?.let { claims["scope"] = scope }
    return Jwts
      .builder()
      .setId(jwtId)
      .setSubject(subject)
      .addClaims(claims)
      .setExpiration(Date(System.currentTimeMillis() + expiryTime.toMillis()))
      .signWith(RS256, keyPair.private)
      .compact()
  }
}
