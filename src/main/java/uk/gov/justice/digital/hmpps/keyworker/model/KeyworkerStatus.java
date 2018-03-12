package uk.gov.justice.digital.hmpps.keyworker.model;

import java.util.HashMap;
import java.util.Map;

public enum KeyworkerStatus {
    ACTIVE("ACT"),
    INACTIVE("INA"),
    UNAVAILABLE_ANNUAL_LEAVE("UAL"),
    UNAVAILABLE_LONG_TERM_ABSENCE("ULT"),
    UNAVAILABLE_NO_PRISONER_CONTACT("UNP"),
    UNAVAILABLE_SUSPENDED("USU");

    private final String statusCode;

    KeyworkerStatus(String statusCode) {
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
        for (KeyworkerStatus status : KeyworkerStatus.values()) {
            lookup.put(status.statusCode, status);
        }
    }

    public static KeyworkerStatus get(String statusCode) {
        return lookup.get(statusCode);
    }
}
