package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.justice.digital.hmpps.keyworker.exception.AgencyNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.security.AuthenticationFacade;

import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * Test class for {@link KeyworkerService}.
 */
@RunWith(MockitoJUnitRunner.class)
public class KeyworkerServiceTest {
    private static final String TEST_AGENCY = "LEI";
    private static final String TEST_USER = "VANILLA";

    @Mock
    private AuthenticationFacade authenticationFacade;

    @Mock
    private OffenderKeyworkerRepository repository;

    @InjectMocks
    private KeyworkerService service;

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(service, "supportedAgencies", Collections.singleton(TEST_AGENCY));
    }

    @Test
    public void testVerifyAgencySupportForSupportedAgency() {
        service.verifyAgencySupport(TEST_AGENCY);
    }

    @Test(expected = AgencyNotSupportedException.class)
    public void testVerifyAgencySupportForUnsupportedAgency() {
        service.verifyAgencySupport("XXX");
    }

    @Test
    public void testAllocateOffenderKeyworker() {
        final String offenderNo = "A1111AA";
        final long staffId = 5L;

        OffenderKeyworker testAlloc = getTestOffenderKeyworker(TEST_AGENCY, offenderNo, staffId);

        // Mock authenticated user
        when(authenticationFacade.getCurrentUsername()).thenReturn(TEST_USER);

        service.allocate(testAlloc);

        ArgumentCaptor<OffenderKeyworker> argCap = ArgumentCaptor.forClass(OffenderKeyworker.class);

        verify(repository, times(1)).save(argCap.capture());

        KeyworkerTestHelper.verifyNewAllocation(argCap.getValue(), TEST_AGENCY, offenderNo, staffId);
    }

    private OffenderKeyworker getTestOffenderKeyworker(String agencyId, String offenderNo, long staffId) {
        return OffenderKeyworker.builder()
                .agencyId(agencyId)
                .offenderNo(offenderNo)
                .staffId(staffId)
                .allocationType(AllocationType.AUTO)
                .allocationReason(AllocationReason.AUTO)
                .build();
    }
}
