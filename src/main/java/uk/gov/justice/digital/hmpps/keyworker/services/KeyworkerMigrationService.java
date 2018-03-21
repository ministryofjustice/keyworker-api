package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriTemplate;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.repository.BulkOffenderKeyworkerImporter;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.net.URI;
import java.util.List;

@Service
@Transactional
@Slf4j
public class KeyworkerMigrationService extends Elite2ApiSource {
    static final String URI_KEY_WORKER_GET_ALLOCATION_HISTORY = "/key-worker/{agencyId}/allocationHistory";

    private static final ParameterizedTypeReference<List<OffenderKeyworkerDto>> PARAM_TYPE_REF_OFF_KEY_WORKER =
            new ParameterizedTypeReference<List<OffenderKeyworkerDto>>() {};

    @Value("${svc.kw.migration.page.limit:10}")
    private long migrationPageSize;

    private final OffenderKeyworkerRepository repository;
    private final BulkOffenderKeyworkerImporter importer;
    private final AgencyValidation agencyValidation;

    public KeyworkerMigrationService(OffenderKeyworkerRepository repository, BulkOffenderKeyworkerImporter importer, AgencyValidation agencyValidation) {
        this.repository = repository;
        this.importer = importer;
        this.agencyValidation = agencyValidation;
    }

    public void checkAndMigrateOffenderKeyWorker(String agencyId) {
        if (isMigrated(agencyId)) return;

        // If we get here, agency is eligible for migration and has not yet been migrated.

        // Do the migration...
        List<OffenderKeyworkerDto> allocations;

        long offset = 0;

        do {
            allocations = getOffenderKeyWorkerPage(agencyId, offset, migrationPageSize);

            log.debug("[{}] allocations retrieved for agency [{}]", allocations.size(), agencyId);

            importer.translateAndStore(allocations);

            offset += migrationPageSize;
        } while (allocations.size() == migrationPageSize);

        // Finally, persist all allocations
        importer.importAll();
    }

    private boolean isMigrated(String agencyId) {
        agencyValidation.verifyAgencySupport(agencyId);

        // Check repository to determine if agency already migrated
        return repository.existsByAgencyId(agencyId);
    }

    private List<OffenderKeyworkerDto> getOffenderKeyWorkerPage(String agencyId, long offset, long limit) {
        log.debug("Retrieving allocation history for agency [{}] using offset [{}] and limit [{}].", agencyId, offset, limit);

        URI uri = new UriTemplate(URI_KEY_WORKER_GET_ALLOCATION_HISTORY).expand(agencyId);
        PagingAndSortingDto pagingAndSorting = PagingAndSortingDto.builder().pageOffset(offset).pageLimit(limit).build();

        return getWithPaging(uri, pagingAndSorting, PARAM_TYPE_REF_OFF_KEY_WORKER).getBody();
    }
}
