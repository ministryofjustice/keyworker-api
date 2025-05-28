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
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyPrisonConfiguration;
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyPrisonConfigurationRepository;

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
    private LegacyPrisonConfigurationRepository repository;

    @BeforeEach
    void setUp() {
        prisonSupportedService = new PrisonSupportedService(repository);
        ReflectionTestUtils.setField(prisonSupportedService, "capacityTiers", List.of(6,9));
    }

    @Test
    void testVerifyPrisonSupportForUnsupportedPrison() {
        when(repository.existsByPrisonCode("XXX")).thenReturn(false);
        assertThatThrownBy(() -> prisonSupportedService.verifyPrisonMigrated("XXX")).isInstanceOf(PrisonNotSupportedException.class);
    }

    @Test
    void testVerifyPrisonSupportForSupportedPrison() {
        when(repository.existsByPrisonCode(TEST_AGENCY)).thenReturn(true);
        when(repository.findByPrisonCode(TEST_AGENCY)).thenReturn(Optional.of(LegacyPrisonConfiguration.builder().prisonCode(TEST_AGENCY).enabled(false).build()));
        assertThatThrownBy(() -> prisonSupportedService.verifyPrisonMigrated(TEST_AGENCY)).isInstanceOf(PrisonNotMigratedException.class);
    }

    @Test
    void testVerifyPrisonMigratedForSupportedPrison() {
        when(repository.existsByPrisonCode(TEST_AGENCY)).thenReturn(true);
        when(repository.findByPrisonCode(TEST_AGENCY)).thenReturn(Optional.of(LegacyPrisonConfiguration.builder().prisonCode(TEST_AGENCY).enabled(true).build()));
        prisonSupportedService.verifyPrisonMigrated(TEST_AGENCY);
    }

    @Test
    void testIsMigratedSupportedPrison() {
        when(repository.findByPrisonCode(TEST_AGENCY)).thenReturn(Optional.of(LegacyPrisonConfiguration.builder().prisonCode(TEST_AGENCY).enabled(true).build()));
        final var migrated = prisonSupportedService.isMigrated(TEST_AGENCY);

        assertThat(migrated).isTrue();
    }

    @Test
    void testIsNotMigratedSupportedPrison() {
        when(repository.findByPrisonCode(TEST_AGENCY)).thenReturn(Optional.of(LegacyPrisonConfiguration.builder().prisonCode(TEST_AGENCY).enabled(false).build()));
        final var migrated = prisonSupportedService.isMigrated(TEST_AGENCY);

        assertThat(migrated).isFalse();
    }

    @Test
    void testIsNotExistsSupportedPrison() {
        when(repository.findByPrisonCode(TEST_AGENCY)).thenReturn(Optional.empty());
        final var migrated = prisonSupportedService.isMigrated(TEST_AGENCY);

        assertThat(migrated).isFalse();
    }

    @Test
    void testAutoAllocationSupportedForPrison() {
        when(repository.existsByPrisonCode(TEST_AGENCY)).thenReturn(true);
        when(repository.findByPrisonCode(TEST_AGENCY)).thenReturn(Optional.of(LegacyPrisonConfiguration.builder().prisonCode(TEST_AGENCY).allowAutoAllocation(true).build()));
        prisonSupportedService.verifyPrisonSupportsAutoAllocation(TEST_AGENCY);
    }

    @Test
    void testAutoAllocationNotSupportedForPrison() {
        when(repository.existsByPrisonCode(TEST_AGENCY)).thenReturn(true);
        when(repository.findByPrisonCode(TEST_AGENCY)).thenReturn(Optional.of(LegacyPrisonConfiguration.builder().prisonCode(TEST_AGENCY).allowAutoAllocation(false).build()));
        assertThatThrownBy(() -> prisonSupportedService.verifyPrisonSupportsAutoAllocation(TEST_AGENCY)).isInstanceOf(PrisonNotSupportAutoAllocationException.class);
    }

    @Test
    void testUpdateSupportedPrisonAutoAllocateUpdate() {
        final var prison = LegacyPrisonConfiguration.builder().prisonCode(TEST_AGENCY).build();
        when(repository.findByPrisonCode(TEST_AGENCY)).thenReturn(Optional.of(prison));

        prisonSupportedService.updateSupportedPrison(TEST_AGENCY, true, 5, 7, 1);

        verify(repository, never()).save(any(LegacyPrisonConfiguration.class));
        assertThat(prison.getCapacity()).isEqualTo(5);
        assertThat(prison.getMaximumCapacity()).isEqualTo(7);
    }

    @Test
    void testGetMigratedPrisons() {
        final var agencyWithHighComplexity = "MDI";
        final var agencyWithoutHighComplexity = "LEI";
        when(repository.findAllByEnabledEquals(true)).thenReturn(
            List.of(
                LegacyPrisonConfiguration.builder().prisonCode(agencyWithHighComplexity).allowAutoAllocation(true).enabled(true)
                    .capacity(5).maximumCapacity(7).frequencyInWeeks(1)
                    .hasPrisonersWithHighComplexityNeeds(true).build(),
                LegacyPrisonConfiguration.builder().prisonCode(agencyWithoutHighComplexity).allowAutoAllocation(true).enabled(true)
                    .capacity(2).maximumCapacity(5).frequencyInWeeks(4)
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
        when(repository.findByPrisonCode(agencyWithHighComplexity))
            .thenReturn(Optional.of(
                LegacyPrisonConfiguration.builder().prisonCode(agencyWithHighComplexity)
                    .allowAutoAllocation(true)
                    .enabled(true)
                    .capacity(5)
                    .maximumCapacity(7)
                    .frequencyInWeeks(1)
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
        when(repository.findByPrisonCode(TEST_AGENCY)).thenReturn(Optional.empty());

        prisonSupportedService.updateSupportedPrison(TEST_AGENCY, true);

        final var kwaArg = ArgumentCaptor.forClass(LegacyPrisonConfiguration.class);
        verify(repository).save(kwaArg.capture());
        final var prison = kwaArg.getValue();
        assertThat(prison.getCapacity()).isEqualTo(6);
        assertThat(prison.getMaximumCapacity()).isEqualTo(9);
    }
}
