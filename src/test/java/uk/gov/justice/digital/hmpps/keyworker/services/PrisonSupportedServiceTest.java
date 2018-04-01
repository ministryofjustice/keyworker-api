package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PrisonSupportedServiceTest {

    private static final String TEST_AGENCY = "LEI";

    private PrisonSupportedService prisonSupportedService;

    @Mock
    private PrisonSupportedRepository repository;

    @Before
    public void setUp() {
        prisonSupportedService = new PrisonSupportedService(repository);
    }

    @Test
    public void testVerifyAgencySupportForSupportedAgency() {
        when(repository.existsByPrisonId(TEST_AGENCY)).thenReturn(true);
        prisonSupportedService.verifyPrisonSupported(TEST_AGENCY);
    }

    @Test(expected = PrisonNotSupportedException.class)
    public void testVerifyAgencySupportForUnsupportedAgency() {
        when(repository.existsByPrisonId("XXX")).thenReturn(false);
        prisonSupportedService.verifyPrisonSupported("XXX");
    }

}
