package uk.gov.justice.digital.hmpps.keyworker.model;

import java.util.HashMap;
import java.util.Map;

public enum DeallocationReason {
    OVERRIDE("OVERRIDE"),
    RELEASED("RELEASED"),
    KEYWORKER_STATUS_CHANGE("KEYWORKER_STATUS_CHANGE"),
    TRANSFER("TRANSFER");

    private final String reasonCode;

    DeallocationReason(String reasonCode) {
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
        for (DeallocationReason reason : DeallocationReason.values()) {
            lookup.put(reason.reasonCode, reason);
        }
    }

    public static DeallocationReason get(String reasonCode) {
        return lookup.get(reasonCode);
    }
}
