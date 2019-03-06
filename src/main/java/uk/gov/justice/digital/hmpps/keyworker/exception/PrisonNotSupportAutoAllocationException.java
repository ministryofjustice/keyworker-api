package uk.gov.justice.digital.hmpps.keyworker.exception;

import java.util.function.Supplier;

public class PrisonNotSupportAutoAllocationException extends RuntimeException implements Supplier<PrisonNotSupportAutoAllocationException> {
    private static final String DEFAULT_MESSAGE_FOR_ID_FORMAT = "Prison [%s] does not support auto allocation";

    public static PrisonNotSupportAutoAllocationException withId(final String id) {
        return new PrisonNotSupportAutoAllocationException(String.format(DEFAULT_MESSAGE_FOR_ID_FORMAT, id));
    }

    public static PrisonNotSupportAutoAllocationException withMessage(final String message) {
        return new PrisonNotSupportAutoAllocationException(message);
    }

    public static PrisonNotSupportAutoAllocationException withMessage(final String message, final Object... args) {
        return new PrisonNotSupportAutoAllocationException(String.format(message, args));
    }

    public PrisonNotSupportAutoAllocationException(final String message) {
        super(message);
    }

    @Override
    public PrisonNotSupportAutoAllocationException get() {
        return new PrisonNotSupportAutoAllocationException(getMessage());
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
