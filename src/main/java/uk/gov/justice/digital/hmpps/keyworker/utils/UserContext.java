package uk.gov.justice.digital.hmpps.keyworker.utils;

import org.springframework.stereotype.Component;


@Component
public class UserContext {
    public static final String CORRELATION_ID = "correlation-id";
    public static final String AUTH_TOKEN     = "Authorization";

    private static final ThreadLocal<String> correlationId= new ThreadLocal<>();
    private static final ThreadLocal<String> authToken= new ThreadLocal<>();


    public static String getCorrelationId() { return correlationId.get(); }
    public static void setCorrelationId(String cid) {correlationId.set(cid);}

    public static String getAuthToken() { return authToken.get(); }
    public static void setAuthToken(String aToken) {authToken.set(aToken);}
}