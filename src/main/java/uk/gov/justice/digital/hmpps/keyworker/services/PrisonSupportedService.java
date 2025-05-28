package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotMigratedException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportAutoAllocationException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyPrisonConfiguration;
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyPrisonConfigurationRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PrisonSupportedService {

    @Value("${svc.kw.allocation.capacity.tiers:6,9}")
    private List<Integer> capacityTiers;

    @Value("${svc.kw.session.frequency.weeks:1}")
    private  int keyWorkerSessionDefaultFrequency;

    private final LegacyPrisonConfigurationRepository repository;

    @Autowired
    public PrisonSupportedService(final LegacyPrisonConfigurationRepository repository) {
        this.repository = repository;
    }

    private void verifyPrisonSupported(final String prisonId) {
        Validate.notBlank(prisonId, "Prison id is required.");

        // Check configuration to verify that prison is eligible for migration.
        if (isNotSupported(prisonId)) {
            throw PrisonNotSupportedException.withId(prisonId);
        }
    }

    void verifyPrisonMigrated(final String prisonId) {
        Validate.notBlank(prisonId, "Prison id is required.");
        verifyPrisonSupported(prisonId);

        // Check configuration to verify that prison has been migrated
        if (!isMigrated(prisonId)) {
            throw PrisonNotMigratedException.withId(prisonId);
        }
    }

    void verifyPrisonSupportsAutoAllocation(final String prisonId) {
        verifyPrisonSupported(prisonId);
        final var prison = getPrisonDetail(prisonId);

        if (!prison.isAutoAllocatedSupported()) {
            throw PrisonNotSupportAutoAllocationException.withId(prisonId);
        }
    }

    @PreAuthorize("hasRole('KW_MIGRATION')")
    @Transactional
    public void updateSupportedPrison(final String prisonId, final boolean autoAllocate) {
        updateSupportedPrison(prisonId, autoAllocate, capacityTiers.get(0), capacityTiers.get(1), keyWorkerSessionDefaultFrequency);
    }

    @PreAuthorize("hasRole('KW_MIGRATION')")
    @Transactional
    public void updateSupportedPrison(final String prisonId, final boolean autoAllocate, final Integer capacityTier1, final Integer capacityTier2, final Integer kwSessionFrequencyInWeeks) {

        repository.findByPrisonCode(prisonId)
                .ifPresentOrElse(prison -> {
                    prison.setAllowAutoAllocation(autoAllocate);
                    if (capacityTier1 != null) {
                        prison.setCapacity(capacityTier1);
                    }
                    if (capacityTier2 != null) {
                        prison.setMaximumCapacity(capacityTier2);
                    }
                    if (kwSessionFrequencyInWeeks != null) {
                        prison.setFrequencyInWeeks(kwSessionFrequencyInWeeks);
                    }
                }, () -> {
                    final var prisonSupported = LegacyPrisonConfiguration.builder()
                            .prisonCode(prisonId)
                            .allowAutoAllocation(autoAllocate)
                            .capacity(capacityTier1 == null ? capacityTiers.get(0) : capacityTier1)
                            .maximumCapacity(capacityTier2 == null ? capacityTiers.get(1) : capacityTier2)
                            .frequencyInWeeks(kwSessionFrequencyInWeeks == null ? keyWorkerSessionDefaultFrequency : kwSessionFrequencyInWeeks)
                            .build();

                    // create a new entry for a new supported prison
                    repository.save(prisonSupported);
                });
    }

    boolean isMigrated(final String prisonId) {
        // Check remote to determine if prison already migrated
        return getPrisonDetail(prisonId).isMigrated();
    }

    public List<Prison> getMigratedPrisons() {
        return repository.findAllByEnabledEquals(true).stream().map(this::buildPrison).collect(Collectors.toList());
    }

    public Prison getPrisonDetail(final String prisonId) {
        return repository.findByPrisonCode(prisonId).map(this::buildPrison)
                .orElseGet(() ->  Prison.builder()
                    .prisonId(prisonId)
                    .capacityTier1(capacityTiers.get(0))
                    .capacityTier2(capacityTiers.get(1))
                    .kwSessionFrequencyInWeeks(keyWorkerSessionDefaultFrequency)
                    .highComplexity(false)
                    .build()
                );

    }

    private Prison buildPrison(final LegacyPrisonConfiguration prison) {
        return Prison.builder()
                .prisonId(prison.getPrisonCode())
                .migrated(prison.isEnabled())
                .supported(true)
                .autoAllocatedSupported(prison.isAllowAutoAllocation())
                .capacityTier1(prison.getCapacity())
                .capacityTier2(prison.getMaximumCapacity())
                .kwSessionFrequencyInWeeks(prison.getFrequencyInWeeks())
                .highComplexity(prison.isHasPrisonersWithHighComplexityNeeds())
                .build();
    }

    private boolean isNotSupported(final String prisonId) {
        return !repository.existsByPrisonCode(prisonId);
    }

}
