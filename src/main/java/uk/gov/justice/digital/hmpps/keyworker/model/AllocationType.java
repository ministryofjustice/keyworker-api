package uk.gov.justice.digital.hmpps.keyworker.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

public enum AllocationType {
    AUTO("A"),
    MANUAL("M"),
    PROVISIONAL("P");

    private final String typeCode;

    AllocationType(final String typeCode) {
        this.typeCode = typeCode;
    }

    public boolean isAuto() {
        return this == AUTO;
    }

    public boolean isManual() {
        return this == MANUAL;
    }

    @JsonValue
    public String getTypeCode() {
        return typeCode;
    }

    @Override
    public String toString() {
        return typeCode;
    }

    // Reverse lookup
    private static final Map<String, AllocationType> lookup = new HashMap<>();

    static {
        for (final var type : AllocationType.values()) {
            lookup.put(type.typeCode, type);
        }
    }

    @JsonCreator
    public static AllocationType get(final String typeCode) {
        return lookup.get(typeCode);
    }
}
