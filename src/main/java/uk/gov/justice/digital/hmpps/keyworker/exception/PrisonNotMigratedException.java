package uk.gov.justice.digital.hmpps.keyworker.exception;

import java.util.function.Supplier;

public class PrisonNotMigratedException extends RuntimeException implements Supplier<PrisonNotMigratedException> {
    private static final String DEFAULT_MESSAGE_FOR_ID_FORMAT = "Prison [%s] is not migrated by this service.";

    public static PrisonNotMigratedException withId(final String id) {
        return new PrisonNotMigratedException(String.format(DEFAULT_MESSAGE_FOR_ID_FORMAT, id));
    }

    public static PrisonNotMigratedException withMessage(final String message) {
        return new PrisonNotMigratedException(message);
    }

    public static PrisonNotMigratedException withMessage(final String message, final Object... args) {
        return new PrisonNotMigratedException(String.format(message, args));
    }

    public PrisonNotMigratedException(final String message) {
        super(message);
    }

    @Override
    public PrisonNotMigratedException get() {
        return new PrisonNotMigratedException(getMessage());
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
