package uk.gov.justice.digital.hmpps.keyworker.model;

import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription;

import java.util.HashMap;
import java.util.Map;

public enum KeyworkerStatus {
    ACTIVE("ACT", "Active"),
    UNAVAILABLE_ANNUAL_LEAVE("UAL", "Unavailable - annual leave"),
    UNAVAILABLE_LONG_TERM_ABSENCE("ULT", "Unavailable - long term absence"),
    UNAVAILABLE_NO_PRISONER_CONTACT("UNP", "Unavailable - no prisoner contact"),
    INACTIVE("INA", "Inactive");

    private final String statusCode;
    private final String description;

    KeyworkerStatus(final String statusCode, final String description) {
        this.statusCode = statusCode;
        this.description = description;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getDescription() { return description; }

    @Override
    public String toString() {
        return statusCode;
    }

    public CodedDescription codedDescription() {
        return new CodedDescription(statusCode, description);
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
