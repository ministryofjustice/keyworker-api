package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriTemplate;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.exception.AgencyNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.repository.BulkOffenderKeyworkerImporter;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class KeyworkerMigrationService extends Elite2ApiSource {
    public static final String URI_KEY_WORKER_GET_ALLOCATION_HISTORY = "/key-worker/{agencyId}/allocationHistory";

    private static final ParameterizedTypeReference<List<OffenderKeyworkerDto>> PARAM_TYPE_REF_OFF_KEY_WORKER =
            new ParameterizedTypeReference<List<OffenderKeyworkerDto>>() {};

    @Value("${svc.kw.migration.page.limit:10}")
    private long migrationPageSize;

    @Value("${svc.kw.migration.agencies}")
    private Set<String> agenciesForMigration;

    private final OffenderKeyworkerRepository repository;
    private final BulkOffenderKeyworkerImporter importer;

    public KeyworkerMigrationService(OffenderKeyworkerRepository repository, BulkOffenderKeyworkerImporter importer) {
        this.repository = repository;
        this.importer = importer;
    }

    public void checkAndMigrateOffenderKeyWorker(String agencyId) {
        Validate.notBlank(agencyId, "Agency id is required.");

        // Check configuration to verify that agency is eligible for migration.
        if (!agenciesForMigration.contains(agencyId)) {
            throw AgencyNotSupportedException.withId(agencyId);
        }

        // Check repository to determine if agency already migrated - exit silently if it has.
        if (repository.existsByAgencyId(agencyId)) {
            return;
        }

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

    private List<OffenderKeyworkerDto> getOffenderKeyWorkerPage(String agencyId, long offset, long limit) {
        log.debug("Retrieving allocation history for agency [{}] using offset [{}] and limit [{}].", agencyId, offset, limit);

        URI uri = new UriTemplate(URI_KEY_WORKER_GET_ALLOCATION_HISTORY).expand(agencyId);
        PagingAndSortingDto pagingAndSorting = PagingAndSortingDto.builder().pageOffset(offset).pageLimit(limit).build();

        return getWithPaging(uri, pagingAndSorting, PARAM_TYPE_REF_OFF_KEY_WORKER).getBody();
    }
}
