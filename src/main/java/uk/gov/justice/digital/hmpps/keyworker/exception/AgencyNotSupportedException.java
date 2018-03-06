package uk.gov.justice.digital.hmpps.keyworker.exception;

import java.util.function.Supplier;

public class AgencyNotSupportedException extends RuntimeException implements Supplier<AgencyNotSupportedException> {
    private static final String DEFAULT_MESSAGE_FOR_ID_FORMAT = "Agency [%s] is not supported by this service.";

    public static AgencyNotSupportedException withId(String id) {
        return new AgencyNotSupportedException(String.format(DEFAULT_MESSAGE_FOR_ID_FORMAT, id));
    }

    public static AgencyNotSupportedException withMessage(String message) {
        return new AgencyNotSupportedException(message);
    }

    public static AgencyNotSupportedException withMessage(String message, Object... args) {
        return new AgencyNotSupportedException(String.format(message, args));
    }

    public AgencyNotSupportedException(String message) {
        super(message);
    }

    @Override
    public AgencyNotSupportedException get() {
        return new AgencyNotSupportedException(getMessage());
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
