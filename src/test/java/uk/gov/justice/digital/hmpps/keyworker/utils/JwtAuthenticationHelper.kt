package uk.gov.justice.digital.hmpps.keyworker.utils

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import lombok.Builder
import lombok.Data
import org.apache.commons.codec.binary.Base64
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyStore
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.RSAPublicKeySpec
import java.time.Duration
import java.util.*

@Component
class JwtAuthenticationHelper(@Value("\${jwt.signing.key.pair}") privateKeyPair: String?,
                              @Value("\${jwt.keystore.password}") keystorePassword: String,
                              @Value("\${jwt.keystore.alias:keyworkerApi}") keystoreAlias: String) {
    private val keyPair: KeyPair
    fun createJwt(parameters: JwtParameters): String {
        val claims = HashMap<String, Any?>()
        claims["user_name"] = parameters.username
        claims["user_id"] = parameters.userId
        claims["client_id"] = "keyworkerApiClient"
        if (parameters.roles.isNotEmpty()) claims["authorities"] = parameters.roles
        if (parameters.scope.isNotEmpty()) claims["scope"] = parameters.scope
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(parameters.username)
                .addClaims(claims)
                .setExpiration(Date(System.currentTimeMillis() + parameters.expiryTime.toMillis()))
                .signWith(SignatureAlgorithm.RS256, keyPair.private)
                .compact()
    }

    data class JwtParameters(
            val username: String? = null,
            val userId: String? = null,
            val scope: List<String> = emptyList(),
            val roles: List<String> = emptyList(),
            val expiryTime: Duration = Duration.ZERO,
            val clientId: String = "keyworkerApiClient",
            val internalUser: Boolean = true
    )

    private fun getKeyPair(resource: Resource, alias: String, password: CharArray): KeyPair {
        try {
            resource.inputStream.use { inputStream ->
                val store = KeyStore.getInstance("jks")
                store.load(inputStream, password)
                val key = store.getKey(alias, password) as RSAPrivateCrtKey
                val spec = RSAPublicKeySpec(key.modulus, key.publicExponent)
                val publicKey = KeyFactory.getInstance("RSA").generatePublic(spec)
                return KeyPair(publicKey, key)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Cannot load keys from store: $resource", e)
        }
    }

    init {
        keyPair = getKeyPair(ByteArrayResource(Base64.decodeBase64(privateKeyPair)), keystoreAlias, keystorePassword.toCharArray())
    }
}