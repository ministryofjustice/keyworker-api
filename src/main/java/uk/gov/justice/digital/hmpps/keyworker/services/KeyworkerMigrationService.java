package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported;
import uk.gov.justice.digital.hmpps.keyworker.repository.BulkOffenderKeyworkerImporter;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@Slf4j
public class KeyworkerMigrationService {

    private final long migrationPageSize;
    private final NomisService nomisService;
    private final PrisonSupportedRepository repository;
    private final BulkOffenderKeyworkerImporter importer;
    private final PrisonSupportedService prisonSupportedService;

    public KeyworkerMigrationService(NomisService nomisService,
                                     PrisonSupportedRepository repository,
                                     BulkOffenderKeyworkerImporter importer,
                                     PrisonSupportedService prisonSupportedService,
                                     @Value("${svc.kw.migration.page.limit:10}") long migrationPageSize) {
        this.repository = repository;
        this.importer = importer;
        this.nomisService = nomisService;
        this.prisonSupportedService = prisonSupportedService;
        this.migrationPageSize = migrationPageSize;
    }

    @PreAuthorize("hasRole('ROLE_KW_MIGRATION')")
    public void migrateKeyworkerByPrison(String prisonId) {
        if (prisonSupportedService.isMigrated(prisonId)) return;

        // If we get here, agency is eligible for migration and has not yet been migrated.

        // Do the migration...
        List<OffenderKeyworkerDto> allocations;

        long offset = 0;

        do {
            allocations = nomisService.getOffenderKeyWorkerPage(prisonId, offset, migrationPageSize);

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


}
