package uk.gov.justice.digital.hmpps.keyworker.utils;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MdcUtility {

    public static final String USER_ID_HEADER = "userId";
    public static final String REQUEST_ID = "requestId";
    public static final String REQUEST_DURATION = "duration";
    public static final String RESPONSE_STATUS = "status";

    public String generateUUID() {
        return UUID.randomUUID().toString();
    }

}
