package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.justice.digital.hmpps.keyworker.exception.AgencyNotSupportedException;

import java.util.Set;

@Component
public class AgencyValidation {

    private final Set<String> supportedAgencies;

    @Autowired
    public AgencyValidation(@Value("${svc.kw.supported.agencies}") Set<String> supportedAgencies) {
        this.supportedAgencies = supportedAgencies;
    }

    public void verifyAgencySupport(String agencyId) {
        Validate.notBlank(agencyId, "Agency id is required.");

        // Check configuration to verify that agency is eligible for migration.
        if (!supportedAgencies.contains(agencyId)) {
            throw AgencyNotSupportedException.withId(agencyId);
        }
    }
}