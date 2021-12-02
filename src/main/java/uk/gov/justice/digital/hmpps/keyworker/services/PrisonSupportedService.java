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
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PrisonSupportedService {

    @Value("${svc.kw.allocation.capacity.tiers:6,9}")
    private List<Integer> capacityTiers;

    @Value("${svc.kw.session.frequency.weeks:1}")
    private  int keyWorkerSessionDefaultFrequency;

    private final PrisonSupportedRepository repository;

    private final ComplexityOfNeed complexityOfNeedService;

    @Autowired
    public PrisonSupportedService(final PrisonSupportedRepository repository, final ComplexityOfNeed complexityOfNeedService) {
        this.repository = repository;
        this.complexityOfNeedService = complexityOfNeedService;
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

        repository.findById(prisonId)
                .ifPresentOrElse(prison -> {
                    prison.setAutoAllocate(autoAllocate);
                    if (capacityTier1 != null) {
                        prison.setCapacityTier1(capacityTier1);
                    }
                    if (capacityTier2 != null) {
                        prison.setCapacityTier2(capacityTier2);
                    }
                    if (kwSessionFrequencyInWeeks != null) {
                        prison.setKwSessionFrequencyInWeeks(kwSessionFrequencyInWeeks);
                    }
                }, () -> {
                    final var prisonSupported = PrisonSupported.builder()
                            .prisonId(prisonId)
                            .autoAllocate(autoAllocate)
                            .capacityTier1(capacityTier1 == null ? capacityTiers.get(0) : capacityTier1)
                            .capacityTier2(capacityTier2 == null ? capacityTiers.get(1) : capacityTier2)
                            .kwSessionFrequencyInWeeks(kwSessionFrequencyInWeeks == null ? keyWorkerSessionDefaultFrequency : kwSessionFrequencyInWeeks)
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
        return repository.findAllByMigratedEquals(true).stream().map(this::buildPrison).collect(Collectors.toList());
    }

    public Prison getPrisonDetail(final String prisonId) {
        return repository.findById(prisonId).map(this::buildPrison)
                .orElseGet(() ->  Prison.builder()
                    .prisonId(prisonId)
                    .capacityTier1(capacityTiers.get(0))
                    .capacityTier2(capacityTiers.get(1))
                    .kwSessionFrequencyInWeeks(keyWorkerSessionDefaultFrequency)
                    .highComplexity(complexityOfNeedService.isComplexPrison(prisonId))
                    .build()
                );

    }

    private Prison buildPrison(final PrisonSupported prison) {
        return Prison.builder()
                .prisonId(prison.getPrisonId())
                .migrated(prison.isMigrated())
                .supported(true)
                .autoAllocatedSupported(prison.isAutoAllocate())
                .capacityTier1(prison.getCapacityTier1())
                .capacityTier2(prison.getCapacityTier2())
                .kwSessionFrequencyInWeeks(prison.getKwSessionFrequencyInWeeks())
                .migratedDateTime(prison.getMigratedDateTime())
                .highComplexity(complexityOfNeedService.isComplexPrison(prison.getPrisonId()))
                .build();
    }

    private boolean isNotSupported(final String prisonId) {
        return !repository.existsByPrisonId(prisonId);
    }

}
