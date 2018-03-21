package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.Before;
import org.junit.Test;
import uk.gov.justice.digital.hmpps.keyworker.exception.AgencyNotSupportedException;

import java.util.Collections;

public class AgencyValidationTest {

    private static final String TEST_AGENCY = "LEI";

    private AgencyValidation agencyValidation;

    @Before
    public void setUp() {
        agencyValidation = new AgencyValidation(Collections.singleton(TEST_AGENCY));
    }

    @Test
    public void testVerifyAgencySupportForSupportedAgency() {
        agencyValidation.verifyAgencySupport(TEST_AGENCY);
    }

    @Test(expected = AgencyNotSupportedException.class)
    public void testVerifyAgencySupportForUnsupportedAgency() {
        agencyValidation.verifyAgencySupport("XXX");
    }

}
