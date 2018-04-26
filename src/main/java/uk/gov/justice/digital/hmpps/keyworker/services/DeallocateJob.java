package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerCustodyStatusDto;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@Transactional
public class DeallocateJob {

    @Autowired
    private NomisService nomisService;
    @Autowired
    private OffenderKeyworkerRepository repository;

    public void execute(LocalDateTime previousJobStart) {
        try {
            log.info("******** De-allocation Process Started using threshold=" + previousJobStart);

            checkReleases(previousJobStart);

            log.info("******** De-allocation Process Ended");

        } catch (Exception e) {
            log.error("Batch exception", e);
        }
    }

    private void checkReleases(LocalDateTime previousJobStart) {

        // Use custody-statuses endpoint to get info from offender_external_movements
        // which matches when the trigger on this table fires to update offender_key_workers
        final List<PrisonerCustodyStatusDto> prisonerStatuses = nomisService.getPrisonerStatuses(previousJobStart);
        log.info(prisonerStatuses.size() + " released prisoners found");

        prisonerStatuses.forEach(p -> {
            final List<OffenderKeyworker> ok = repository.findByActiveAndOffenderNo(true, p.getOffenderNo());
            // There shouldnt ever be more than 1, but just in case
            ok.forEach(offenderKeyworker -> {
                offenderKeyworker.setActive(false);
                offenderKeyworker.setExpiryDateTime(p.getCreateDateTime());
                offenderKeyworker.setDeallocationReason(DeallocationReason.RELEASED);
                log.info("Deallocated " + p.getOffenderNo() + " with timestamp " + p.getCreateDateTime());
            });
        });
    }
}