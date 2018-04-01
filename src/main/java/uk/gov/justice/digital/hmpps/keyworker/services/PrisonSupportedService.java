package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotMigratedException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportAutoAllocationException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository;

@Service
@Transactional(readOnly = true)
public class PrisonSupportedService {

    private final PrisonSupportedRepository repository;

    @Autowired
    public PrisonSupportedService(PrisonSupportedRepository repository) {
        this.repository = repository;
    }

    public void verifyPrisonSupported(String prisonId) {
        Validate.notBlank(prisonId, "Prison id is required.");

        // Check configuration to verify that prison is eligible for migration.
        if (isNotSupported(prisonId)) {
            throw PrisonNotSupportedException.withId(prisonId);
        }
    }

    public void verifyPrisonMigrated(String prisonId) {
        Validate.notBlank(prisonId, "Prison id is required.");

        // Check configuration to verify that prison has been migrated
        if (!isMigrated(prisonId)) {
            throw PrisonNotMigratedException.withId(prisonId);
        }
    }

    public void verifyPrisonSupportsAutoAllocation(String prisonId) {
        verifyPrisonSupported(prisonId);
        Prison prison = getPrisonDetail(prisonId);

        if (!prison.isAutoAllocatedSupported()) {
            throw PrisonNotSupportAutoAllocationException.withId(prisonId);
        }
    }

    @PreAuthorize("hasRole('ROLE_KW_MIGRATION')")
    @Transactional
    public void updateSupportedPrison(String prisonId, boolean autoAllocate) {
        PrisonSupported prison = repository.findOne(prisonId);

        if (prison != null) {
            // update entry
            prison.setAutoAllocate(autoAllocate);
        } else {
            // create a new entry for a new supported prison
            repository.save(PrisonSupported.builder()
                    .prisonId(prisonId)
                    .autoAllocate(autoAllocate)
                    .build());
        }
    }

    public boolean isMigrated(String prisonId) {
        // Check repository to determine if prison already migrated
        Prison prison = getPrisonDetail(prisonId);
        return prison != null && prison.isMigrated();
    }

    public Prison getPrisonDetail(String prisonId) {
        PrisonSupported prison = repository.findOne(prisonId);

        if (prison != null) {
            return Prison.builder()
                    .prisonId(prisonId)
                    .migrated(prison.isMigrated())
                    .supported(true)
                    .autoAllocatedSupported(prison.isAutoAllocate())
                    .build();
        }
        return null;
    }

    private boolean isNotSupported(String prisonId) {
        return !repository.existsByPrisonId(prisonId);
    }

}