package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriTemplate;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported;
import uk.gov.justice.digital.hmpps.keyworker.repository.BulkOffenderKeyworkerImporter;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository;

import java.net.URI;
import java.time.LocalDateTime;
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

    private final PrisonSupportedRepository repository;
    private final BulkOffenderKeyworkerImporter importer;
    private final PrisonSupportedService prisonSupportedService;

    public KeyworkerMigrationService(PrisonSupportedRepository repository,
                                     BulkOffenderKeyworkerImporter importer,
                                     PrisonSupportedService prisonSupportedService) {
        this.repository = repository;
        this.importer = importer;
        this.prisonSupportedService = prisonSupportedService;
    }

    @PreAuthorize("hasRole('ROLE_KW_MIGRATION')")
    public void migrateKeyworkerByPrison(String prisonId) {
        if (isMigrated(prisonId)) return;

        // If we get here, agency is eligible for migration and has not yet been migrated.

        // Do the migration...
        List<OffenderKeyworkerDto> allocations;

        long offset = 0;

        do {
            allocations = getOffenderKeyWorkerPage(prisonId, offset, migrationPageSize);

            log.debug("[{}] allocations retrieved for agency [{}]", allocations.size(), prisonId);

            importer.translateAndStore(allocations);

            offset += migrationPageSize;
        } while (allocations.size() == migrationPageSize);

        // Finally, persist all allocations
        importer.importAll();

        // Mark prison as migrated
        PrisonSupported prison = repository.findOne(prisonId);
        prison.setMigrated(true);
        prison.setMigratedDateTime(LocalDateTime.now());
    }

    private boolean isMigrated(String prisonId) {
        prisonSupportedService.verifyPrisonSupported(prisonId);
        return prisonSupportedService.isMigrated(prisonId);
    }

    private List<OffenderKeyworkerDto> getOffenderKeyWorkerPage(String prisonId, long offset, long limit) {
        log.debug("Retrieving allocation history for agency [{}] using offset [{}] and limit [{}].", prisonId, offset, limit);

        URI uri = new UriTemplate(URI_KEY_WORKER_GET_ALLOCATION_HISTORY).expand(prisonId);
        PagingAndSortingDto pagingAndSorting = PagingAndSortingDto.builder().pageOffset(offset).pageLimit(limit).build();

        return getWithPaging(uri, pagingAndSorting, PARAM_TYPE_REF_OFF_KEY_WORKER).getBody();
    }
}
