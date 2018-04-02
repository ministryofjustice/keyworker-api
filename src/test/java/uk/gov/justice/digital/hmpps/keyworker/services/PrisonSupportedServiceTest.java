package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotMigratedException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportAutoAllocationException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

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

    @Test(expected = PrisonNotSupportedException.class)
    public void testVerifyPrisonSupportForUnsupportedPrison() {
        when(repository.existsByPrisonId("XXX")).thenReturn(false);
        prisonSupportedService.verifyPrisonMigrated("XXX");
    }

    @Test(expected = PrisonNotMigratedException.class)
    public void testVerifyPrisonSupportForSupportedPrison() {
        when(repository.existsByPrisonId(TEST_AGENCY)).thenReturn(true);
        when(repository.findOne(TEST_AGENCY)).thenReturn(PrisonSupported.builder().prisonId(TEST_AGENCY).migrated(false).build());
        prisonSupportedService.verifyPrisonMigrated(TEST_AGENCY);
    }

    @Test
    public void testVerifyPrisonMigratedForSupportedPrison() {
        when(repository.existsByPrisonId(TEST_AGENCY)).thenReturn(true);
        when(repository.findOne(TEST_AGENCY)).thenReturn(PrisonSupported.builder().prisonId(TEST_AGENCY).migrated(true).build());
        prisonSupportedService.verifyPrisonMigrated(TEST_AGENCY);
    }

    @Test
    public void testIsMigratedSupportedPrison() {
        when(repository.findOne(TEST_AGENCY)).thenReturn(PrisonSupported.builder().prisonId(TEST_AGENCY).migrated(true).build());
        boolean migrated = prisonSupportedService.isMigrated(TEST_AGENCY);

        assertThat(migrated).isTrue();
    }

    @Test
    public void testIsNotMigratedSupportedPrison() {
        when(repository.findOne(TEST_AGENCY)).thenReturn(PrisonSupported.builder().prisonId(TEST_AGENCY).migrated(false).build());
        boolean migrated = prisonSupportedService.isMigrated(TEST_AGENCY);

        assertThat(migrated).isFalse();
    }

    @Test
    public void testIsNotExistsSupportedPrison() {
        when(repository.findOne(TEST_AGENCY)).thenReturn(null);
        boolean migrated = prisonSupportedService.isMigrated(TEST_AGENCY);

        assertThat(migrated).isFalse();
    }

    @Test
    public void testAutoAllocationSupportedForPrison() {
        when(repository.existsByPrisonId(TEST_AGENCY)).thenReturn(true);
        when(repository.findOne(TEST_AGENCY)).thenReturn(PrisonSupported.builder().prisonId(TEST_AGENCY).autoAllocate(true).build());
        prisonSupportedService.verifyPrisonSupportsAutoAllocation(TEST_AGENCY);
    }

    @Test(expected = PrisonNotSupportAutoAllocationException.class)
    public void testAutoAllocationNotSupportedForPrison() {
        when(repository.existsByPrisonId(TEST_AGENCY)).thenReturn(true);
        when(repository.findOne(TEST_AGENCY)).thenReturn(PrisonSupported.builder().prisonId(TEST_AGENCY).autoAllocate(false).build());
        prisonSupportedService.verifyPrisonSupportsAutoAllocation(TEST_AGENCY);
    }

    @Test
    public void testUpdateSupportedPrisonAutoAllocateUpdate() {
        when(repository.findOne(TEST_AGENCY)).thenReturn(PrisonSupported.builder().prisonId(TEST_AGENCY).build());

        prisonSupportedService.updateSupportedPrison(TEST_AGENCY, true);

        verify(repository, never()).save(any(PrisonSupported.class));
    }

    @Test
    public void testUpdateSupportedPrisonAutoAllocateNew() {
        when(repository.findOne(TEST_AGENCY)).thenReturn(null);

        prisonSupportedService.updateSupportedPrison(TEST_AGENCY, true);

        verify(repository).save(any(PrisonSupported.class));
    }
}
