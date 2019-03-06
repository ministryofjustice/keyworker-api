package uk.gov.justice.digital.hmpps.keyworker.exception;

import java.util.function.Supplier;

public class PrisonNotSupportedException extends RuntimeException implements Supplier<PrisonNotSupportedException> {
    private static final String DEFAULT_MESSAGE_FOR_ID_FORMAT = "Prison [%s] is not supported by this service.";

    public static PrisonNotSupportedException withId(final String id) {
        return new PrisonNotSupportedException(String.format(DEFAULT_MESSAGE_FOR_ID_FORMAT, id));
    }

    public static PrisonNotSupportedException withMessage(final String message) {
        return new PrisonNotSupportedException(message);
    }

    public static PrisonNotSupportedException withMessage(final String message, final Object... args) {
        return new PrisonNotSupportedException(String.format(message, args));
    }

    public PrisonNotSupportedException(final String message) {
        super(message);
    }

    @Override
    public PrisonNotSupportedException get() {
        return new PrisonNotSupportedException(getMessage());
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
