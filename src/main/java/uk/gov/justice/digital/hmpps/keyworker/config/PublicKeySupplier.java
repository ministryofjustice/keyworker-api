package uk.gov.justice.digital.hmpps.keyworker.config;

import java.security.PublicKey;

public interface PublicKeySupplier {

    PublicKey getPublicKeyForKeyId(String keyId);
}
