package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworkerAllocation;
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyKeyworkerAllocationRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyPrisonConfigurationRepository;
import uk.gov.justice.digital.hmpps.keyworker.utils.ConversionHelper;

import java.util.List;
import java.util.Set;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class KeyworkerMigrationService {

    private final NomisService nomisService;
    private final LegacyPrisonConfigurationRepository repository;
    private final PrisonSupportedService prisonSupportedService;
    private final LegacyKeyworkerAllocationRepository offenderKeyworkerRepository;
    private final ReferenceDataRepository referenceDataRepository;

    @PreAuthorize("hasRole('KW_MIGRATION')")
    public void migrateKeyworkerByPrison(final String prisonId) {
        if (prisonSupportedService.isMigrated(prisonId)) return;

        // If we get here, agency is eligible for migration and has not yet been migrated.
        final var allocations = nomisService.getOffenderKeyWorkerPage(prisonId, 0, Integer.MAX_VALUE);
        log.debug("[{}] allocations retrieved for agency [{}]", allocations.size(), prisonId);
        // persist all allocations
        offenderKeyworkerRepository.saveAll(translate(allocations));

        offenderKeyworkerRepository.flush();

        // Mark prison as migrated
        repository.findByPrisonCode(prisonId).ifPresent(prison -> prison.setEnabled(true));
    }

    private Set<LegacyKeyworkerAllocation> translate(final List<OffenderKeyworkerDto> dtos) {
        Validate.notNull(dtos);

        final var okwList = ConversionHelper.INSTANCE.convertOffenderKeyworkerDto2Model(dtos);

        final var reason = referenceDataRepository.findByKey(
            new ReferenceDataKey(ReferenceDataDomain.ALLOCATION_REASON, AllocationReason.MANUAL.getReasonCode())
        );

        final var dReason = referenceDataRepository.findByKey(
            new ReferenceDataKey(ReferenceDataDomain.DEALLOCATION_REASON, DeallocationReason.MANUAL.getReasonCode())
        );
        okwList.forEach(item -> {
            item.setAllocationType(AllocationType.MANUAL);
            item.setAllocationReason(reason);
            if (!item.isActive()) {
                item.deallocate(item.getDeallocatedAt(), dReason);
            }
        });

        return okwList;
    }

}
