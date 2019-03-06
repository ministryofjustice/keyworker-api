package uk.gov.justice.digital.hmpps.keyworker.utils;

import org.springframework.stereotype.Component;


@Component
public class UserContext {
    private static final ThreadLocal<String> authToken = new ThreadLocal<>();

    public static String getAuthToken() { return authToken.get(); }

    public static void setAuthToken(final String aToken) {
        authToken.set(aToken);
    }
}
