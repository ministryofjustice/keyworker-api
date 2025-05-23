package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotMigratedException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportAutoAllocationException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrisonSupportedServiceTest {

    private static final String TEST_AGENCY = "LEI";

    private PrisonSupportedService prisonSupportedService;

    @Mock
    private PrisonSupportedRepository repository;

    @Mock
    private ComplexityOfNeedService complexityOfNeedService;

    @BeforeEach
    void setUp() {
        prisonSupportedService = new PrisonSupportedService(repository);
        ReflectionTestUtils.setField(prisonSupportedService, "capacityTiers", List.of(6,9));
    }

    @Test
    void testVerifyPrisonSupportForUnsupportedPrison() {
        when(repository.existsByPrisonId("XXX")).thenReturn(false);
        assertThatThrownBy(() -> prisonSupportedService.verifyPrisonMigrated("XXX")).isInstanceOf(PrisonNotSupportedException.class);
    }

    @Test
    void testVerifyPrisonSupportForSupportedPrison() {
        when(repository.existsByPrisonId(TEST_AGENCY)).thenReturn(true);
        when(repository.findById(TEST_AGENCY)).thenReturn(Optional.of(PrisonSupported.builder().prisonId(TEST_AGENCY).migrated(false).build()));
        assertThatThrownBy(() -> prisonSupportedService.verifyPrisonMigrated(TEST_AGENCY)).isInstanceOf(PrisonNotMigratedException.class);
    }

    @Test
    void testVerifyPrisonMigratedForSupportedPrison() {
        when(repository.existsByPrisonId(TEST_AGENCY)).thenReturn(true);
        when(repository.findById(TEST_AGENCY)).thenReturn(Optional.of(PrisonSupported.builder().prisonId(TEST_AGENCY).migrated(true).build()));
        prisonSupportedService.verifyPrisonMigrated(TEST_AGENCY);
    }

    @Test
    void testIsMigratedSupportedPrison() {
        when(repository.findById(TEST_AGENCY)).thenReturn(Optional.of(PrisonSupported.builder().prisonId(TEST_AGENCY).migrated(true).build()));
        final var migrated = prisonSupportedService.isMigrated(TEST_AGENCY);

        assertThat(migrated).isTrue();
    }

    @Test
    void testIsNotMigratedSupportedPrison() {
        when(repository.findById(TEST_AGENCY)).thenReturn(Optional.of(PrisonSupported.builder().prisonId(TEST_AGENCY).migrated(false).build()));
        final var migrated = prisonSupportedService.isMigrated(TEST_AGENCY);

        assertThat(migrated).isFalse();
    }

    @Test
    void testIsNotExistsSupportedPrison() {
        when(repository.findById(TEST_AGENCY)).thenReturn(Optional.empty());
        final var migrated = prisonSupportedService.isMigrated(TEST_AGENCY);

        assertThat(migrated).isFalse();
    }

    @Test
    void testAutoAllocationSupportedForPrison() {
        when(repository.existsByPrisonId(TEST_AGENCY)).thenReturn(true);
        when(repository.findById(TEST_AGENCY)).thenReturn(Optional.of(PrisonSupported.builder().prisonId(TEST_AGENCY).autoAllocate(true).build()));
        prisonSupportedService.verifyPrisonSupportsAutoAllocation(TEST_AGENCY);
    }

    @Test
    void testAutoAllocationNotSupportedForPrison() {
        when(repository.existsByPrisonId(TEST_AGENCY)).thenReturn(true);
        when(repository.findById(TEST_AGENCY)).thenReturn(Optional.of(PrisonSupported.builder().prisonId(TEST_AGENCY).autoAllocate(false).build()));
        assertThatThrownBy(() -> prisonSupportedService.verifyPrisonSupportsAutoAllocation(TEST_AGENCY)).isInstanceOf(PrisonNotSupportAutoAllocationException.class);
    }

    @Test
    void testUpdateSupportedPrisonAutoAllocateUpdate() {
        final var prison = PrisonSupported.builder().prisonId(TEST_AGENCY).build();
        when(repository.findById(TEST_AGENCY)).thenReturn(Optional.of(prison));

        prisonSupportedService.updateSupportedPrison(TEST_AGENCY, true, 5, 7, 1);

        verify(repository, never()).save(any(PrisonSupported.class));
        assertThat(prison.getCapacityTier1()).isEqualTo(5);
        assertThat(prison.getCapacityTier2()).isEqualTo(7);
    }

    @Test
    void testGetMigratedPrisons() {
        final var agencyWithHighComplexity = "MDI";
        final var agencyWithoutHighComplexity = "LEI";
        when(repository.findAllByMigratedEquals(true)).thenReturn(
            List.of(
                PrisonSupported.builder().prisonId(agencyWithHighComplexity).autoAllocate(true).migrated(true)
                    .capacityTier1(5).capacityTier2(7).kwSessionFrequencyInWeeks(1)
                    .hasPrisonersWithHighComplexityNeeds(true).build(),
                PrisonSupported.builder().prisonId(agencyWithoutHighComplexity).autoAllocate(true).migrated(true)
                    .capacityTier1(2).capacityTier2(5).kwSessionFrequencyInWeeks(4)
                    .hasPrisonersWithHighComplexityNeeds(false).build()
            )
        );

        final var migratedPrisons = prisonSupportedService.getMigratedPrisons();
        assertThat(migratedPrisons).hasSize(2);
        assertThat(migratedPrisons.get(0).isMigrated()).isTrue();
        assertThat(migratedPrisons.get(0).getPrisonId()).isEqualTo(agencyWithHighComplexity);
        assertThat(migratedPrisons.get(0).getCapacityTier1()).isEqualTo(5);
        assertThat(migratedPrisons.get(0).getCapacityTier2()).isEqualTo(7);
        assertThat(migratedPrisons.get(0).getKwSessionFrequencyInWeeks()).isEqualTo(1);
        assertThat(migratedPrisons.get(0).isHighComplexity()).isTrue();

        assertThat(migratedPrisons).hasSize(2);
        assertThat(migratedPrisons.get(1).isMigrated()).isTrue();
        assertThat(migratedPrisons.get(1).getPrisonId()).isEqualTo(agencyWithoutHighComplexity);
        assertThat(migratedPrisons.get(1).getCapacityTier1()).isEqualTo(2);
        assertThat(migratedPrisons.get(1).getCapacityTier2()).isEqualTo(5);
        assertThat(migratedPrisons.get(1).getKwSessionFrequencyInWeeks()).isEqualTo(4);
        assertThat(migratedPrisons.get(1).isHighComplexity()).isFalse();
    }

    @Test
    void getPrisonDetail() {
        final var agencyWithHighComplexity = "MDI";
        when(repository.findById(agencyWithHighComplexity))
            .thenReturn(Optional.of(
                PrisonSupported.builder().prisonId(agencyWithHighComplexity)
                    .autoAllocate(true)
                    .migrated(true)
                    .capacityTier1(5)
                    .capacityTier2(7)
                    .kwSessionFrequencyInWeeks(1)
                    .hasPrisonersWithHighComplexityNeeds(true)
                    .build()
            ));

        final var prison = prisonSupportedService.getPrisonDetail(agencyWithHighComplexity);
        assertThat(prison.isMigrated()).isTrue();
        assertThat(prison.getPrisonId()).isEqualTo(agencyWithHighComplexity);
        assertThat(prison.getCapacityTier1()).isEqualTo(5);
        assertThat(prison.getCapacityTier2()).isEqualTo(7);
        assertThat(prison.getKwSessionFrequencyInWeeks()).isEqualTo(1);
        assertThat(prison.isHighComplexity()).isTrue();
    }

    @Test
    void testUpdateSupportedPrisonAutoAllocateNew() {
        when(repository.findById(TEST_AGENCY)).thenReturn(Optional.empty());

        prisonSupportedService.updateSupportedPrison(TEST_AGENCY, true);

        final var kwaArg = ArgumentCaptor.forClass(PrisonSupported.class);
        verify(repository).save(kwaArg.capture());
        final var prison = kwaArg.getValue();
        assertThat(prison.getCapacityTier1()).isEqualTo(6);
        assertThat(prison.getCapacityTier2()).isEqualTo(9);
    }
}
