package uk.gov.justice.digital.hmpps.keyworker.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;


@Component
@Slf4j
public class ApiGatewayTokenGenerator {

    private final String apiGatewayToken;
    private final String apiGatewayPrivateKey;

    public ApiGatewayTokenGenerator(@Value("${api.gateway.token}") final String apiGatewayToken,
                                    @Value("${api.gateway.private.key}") final String apiGatewayPrivateKey) {
        this.apiGatewayToken = apiGatewayToken;
        this.apiGatewayPrivateKey = apiGatewayPrivateKey;
    }

    public String createGatewayToken() throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchProviderException {

        Long milliseconds = System.currentTimeMillis();

        final String payload = String.format("{ \"iat\": %d, \"token\": \"%s\" }", milliseconds, apiGatewayToken);
        return Jwts.builder()
                .setPayload(payload)
                .signWith(SignatureAlgorithm.ES256, Utils.loadPrivateKey(apiGatewayPrivateKey))
                .compact();
    }




}
