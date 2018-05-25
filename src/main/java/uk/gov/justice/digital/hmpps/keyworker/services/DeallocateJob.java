package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerCustodyStatusDto;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@Transactional
public class DeallocateJob {

    @Autowired
    private NomisService nomisService;
    @Autowired
    private OffenderKeyworkerRepository repository;
    @Autowired
    private TelemetryClient telemetryClient;
    @Value("${api.keyworker.deallocate.lookBackDays}")
    private int lookBackDays;

    public void execute(LocalDateTime previousJobStart) {
        try {
            log.info("******** De-allocation Process Started using threshold=" + previousJobStart);

            checkMovements(previousJobStart);

            log.info("******** De-allocation Process Ended");
        } catch (Exception e) {
            log.error("Batch exception", e);
            telemetryClient.trackException(e);
        }
    }

    public void checkMovements(LocalDateTime previousJobStart) {

        final LocalDate today = LocalDate.now();

        logEventToAzure(previousJobStart, today);

        for (int dayNumber = 0; dayNumber >= -lookBackDays; dayNumber--) {
            final LocalDate movementDate = today.plusDays(dayNumber);

            // Use custody-statuses endpoint to get info from offender_external_movements
            // which matches when the trigger on this table fires to update offender_key_workers

            final long startTime = System.currentTimeMillis();
            final List<PrisonerCustodyStatusDto> prisonerStatuses = nomisService.getPrisonerStatuses(previousJobStart, movementDate);
            final long endTime = System.currentTimeMillis();
            log.info("Day offset {}: {} released or transferred prisoners found", dayNumber, prisonerStatuses.size());

            logSubEventToAzure(dayNumber, prisonerStatuses, endTime - startTime);

            prisonerStatuses.forEach(ps -> {
                final List<OffenderKeyworker> ok = repository.findByActiveAndOffenderNo(true, ps.getOffenderNo());
                // There shouldnt ever be more than 1, but just in case
                ok.forEach(offenderKeyworker -> {
                    offenderKeyworker.setActive(false);
                    offenderKeyworker.setExpiryDateTime(ps.getCreateDateTime());
                    offenderKeyworker.setDeallocationReason(DeallocationReason.RELEASED);
                    log.info("Deallocated {} using timestamp {}", ps.getOffenderNo(), ps.getCreateDateTime());
                });
            });
        }
    }

    private void logEventToAzure(LocalDateTime previousJobStart, LocalDate today) {
        final Map<String, String> logMap = new HashMap<>();
        logMap.put("date", today.format(DateTimeFormatter.ISO_LOCAL_DATE));
        logMap.put("previousJobStart", previousJobStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        telemetryClient.trackEvent("deallocationCheck", logMap, null);
    }

    private void logSubEventToAzure(int dayNumber, List<PrisonerCustodyStatusDto> prisonerStatuses, long ms) {
        final Map<String, String> stepLogMap = new HashMap<>();
        stepLogMap.put("dayNumber", String.valueOf(dayNumber));
        stepLogMap.put("prisonersFound", String.valueOf(prisonerStatuses.size()));
        stepLogMap.put("queryMs", String.valueOf((ms)));
        telemetryClient.trackEvent("deallocationCheckStep", stepLogMap, null);
    }
}