package uk.gov.justice.digital.hmpps.keyworker.model;

import java.util.HashMap;
import java.util.Map;

public enum DeallocationReason {
    OVERRIDE("OVERRIDE"),
    RELEASED("RELEASED"),
    STAFF_STATUS_CHANGE("STAFF_STATUS_CHANGE"),
    TRANSFER("TRANSFER"),
    MERGED("MERGED"),
    MISSING("MISSING"),
    DUP("DUPLICATE"),
    MANUAL("MANUAL"),
    CHANGE_IN_COMPLEXITY_OF_NEED("CHANGE_IN_COMPLEXITY_OF_NEED"),
    NO_LONGER_IN_PRISON("NO_LONGER_IN_PRISON"),
    PRISON_USES_KEY_WORK("PRISON_USES_KEY_WORK"),
    MIGRATION("MIGRATION"),
    ;

    private final String reasonCode;

    DeallocationReason(final String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    @Override
    public String toString() {
        return reasonCode;
    }

    // Reverse lookup
    private static final Map<String, DeallocationReason> lookup = new HashMap<>();

    static {
        for (final var reason : DeallocationReason.values()) {
            lookup.put(reason.reasonCode, reason);
        }
    }

    public static DeallocationReason get(final String reasonCode) {
        return lookup.get(reasonCode);
    }
}
