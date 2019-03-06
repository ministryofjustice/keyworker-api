package uk.gov.justice.digital.hmpps.keyworker.model;

import java.util.HashMap;
import java.util.Map;

public enum KeyworkerStatus {
    ACTIVE("ACT"),
    UNAVAILABLE_ANNUAL_LEAVE("UAL"),
    UNAVAILABLE_LONG_TERM_ABSENCE("ULT"),
    UNAVAILABLE_NO_PRISONER_CONTACT("UNP"),
    INACTIVE("INA");

    private final String statusCode;

    KeyworkerStatus(final String statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusCode() {
        return statusCode;
    }

    @Override
    public String toString() {
        return statusCode;
    }

    // Reverse lookup
    private static final Map<String, KeyworkerStatus> lookup = new HashMap<>();

    static {
        for (final var status : KeyworkerStatus.values()) {
            lookup.put(status.statusCode, status);
        }
    }

    public static KeyworkerStatus get(final String statusCode) {
        return lookup.get(statusCode);
    }
}
