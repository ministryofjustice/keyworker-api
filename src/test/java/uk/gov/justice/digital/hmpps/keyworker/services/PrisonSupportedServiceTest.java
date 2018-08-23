package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotMigratedException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportAutoAllocationException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository;

import java.util.Arrays;

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
        ReflectionTestUtils.setField(prisonSupportedService, "capacityTiers", Arrays.asList(6,9));
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
        PrisonSupported prison = PrisonSupported.builder().prisonId(TEST_AGENCY).build();
        when(repository.findOne(TEST_AGENCY)).thenReturn(prison);

        prisonSupportedService.updateSupportedPrison(TEST_AGENCY, true, 5, 7, 1);

        verify(repository, never()).save(any(PrisonSupported.class));
        assertThat(prison.getCapacityTier1()).isEqualTo(5);
        assertThat(prison.getCapacityTier2()).isEqualTo(7);
    }

    @Test
    public void testUpdateSupportedPrisonAutoAllocateNew() {
        when(repository.findOne(TEST_AGENCY)).thenReturn(null);

        prisonSupportedService.updateSupportedPrison(TEST_AGENCY, true);

        ArgumentCaptor<PrisonSupported> kwaArg = ArgumentCaptor.forClass(PrisonSupported.class);
        verify(repository).save(kwaArg.capture());//any(PrisonSupported.class));
        final PrisonSupported prison = kwaArg.getValue();
        assertThat(prison.getCapacityTier1()).isEqualTo(6);
        assertThat(prison.getCapacityTier2()).isEqualTo(9);
    }
}
