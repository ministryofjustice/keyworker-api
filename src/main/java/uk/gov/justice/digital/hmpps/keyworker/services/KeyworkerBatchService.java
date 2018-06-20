package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpServerErrorException;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerCustodyStatusDto;
import uk.gov.justice.digital.hmpps.keyworker.model.*;
import uk.gov.justice.digital.hmpps.keyworker.repository.BatchHistoryRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.KeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class KeyworkerBatchService {
    private final NomisService nomisService;
    private final OffenderKeyworkerRepository repository;
    private final KeyworkerRepository keyworkerRepository;
    private TelemetryClient telemetryClient;
    private final BatchHistoryRepository batchHistoryRepository;
    @Value("${api.keyworker.deallocate.lookBackDays}")
    private int lookBackDays;
    @Value("${api.keyworker.deallocate.maxAttempts}")
    private int maxAttempts;
    @Value("${api.keyworker.deallocate.backoffMs}")
    private int backoffMs;

    public KeyworkerBatchService(OffenderKeyworkerRepository repository,
                                 KeyworkerRepository keyworkerRepository,
                                 NomisService nomisService,
                                 TelemetryClient telemetryClient, BatchHistoryRepository batchHistoryRepository) {
        this.keyworkerRepository = keyworkerRepository;
        this.telemetryClient = telemetryClient;
        this.repository = repository;
        this.nomisService = nomisService;
        this.batchHistoryRepository = batchHistoryRepository;
    }

    public void executeDeallocation(LocalDateTime previousJobStartParam) {
        try {
            BatchHistory deallocateJob = batchHistoryRepository.findByName("DeallocateJob");
            if (deallocateJob == null) {
                deallocateJob = BatchHistory.builder()
                        .name("DeallocateJob")
                        .lastRun(previousJobStartParam)
                        .build();
                batchHistoryRepository.save(deallocateJob);
                log.warn("Created BatchHistory record");
            }
            LocalDateTime previousJobStart = deallocateJob.getLastRun();
            final LocalDateTime thisJobStart = LocalDateTime.now();

            log.info("******** De-allocation Process Started using previousJobStart=" + previousJobStart);

            checkMovements(previousJobStart);

            deallocateJob.setLastRun(thisJobStart);

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

            final List<PrisonerCustodyStatusDto> prisonerStatuses = getFromNomis(previousJobStart, today, dayNumber);

            prisonerStatuses.forEach(ps -> {
                final List<OffenderKeyworker> ok = repository.findByActiveAndOffenderNo(true, ps.getOffenderNo());
                // There shouldnt ever be more than 1, but just in case
                ok.forEach(offenderKeyworker -> {
                    if (StringUtils.equals(ps.getToAgency(), offenderKeyworker.getPrisonId())) {
                        log.warn("Not proceeding with " + ps);
                    } else {
                        offenderKeyworker.deallocate(ps.getCreateDateTime(), "REL".equals(ps.getMovementType()) ? DeallocationReason.RELEASED : DeallocationReason.TRANSFER);
                        log.info("Deallocated offender from KW {} at {} due to record " + ps, offenderKeyworker.getStaffId(), offenderKeyworker.getPrisonId());
                    }
                });
            });
        }
    }

    private List<PrisonerCustodyStatusDto> getFromNomis(LocalDateTime previousJobStart, LocalDate today, int dayNumber) {

        for (int i = 1; i <= maxAttempts; i++) {
            final LocalDate movementDate = today.plusDays(dayNumber);
            try {

                // Use custody-statuses endpoint to get info from offender_external_movements
                // which matches when the trigger on this table fires to update offender_key_workers

                final long startTime = System.currentTimeMillis();
                final List<PrisonerCustodyStatusDto> prisonerStatuses = nomisService.getPrisonerStatuses(previousJobStart, movementDate);
                final long endTime = System.currentTimeMillis();

                log.info("Day offset {}: {} released or transferred prisoners found", dayNumber, prisonerStatuses.size());
                logSubEventToAzure(dayNumber, prisonerStatuses, endTime - startTime);
                return prisonerStatuses;

            } catch (HttpServerErrorException e) {
                // The gateway could timeout
                if (!e.getMessage().contains("502 Bad Gateway")) {
                    throw e;
                } else if (i == maxAttempts) {
                    log.warn("Detected a gateway timeout for movementDate=" + movementDate + ", attempt " + i + ", aborting", e);
                    // Throw toys out of pram and leave till next batch run
                    throw e;
                } else {
                    log.warn("Detected a gateway timeout for movementDate=" + movementDate + ", attempt " + i + ", retrying", e);
                    telemetryClient.trackException(e);
                    // don't hammer a struggling back end
                    pause();
                }
            }
        }
        // Should never get here
        throw new IllegalStateException();
    }

    private void pause() {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ie) {
            log.error("Unexpected error", ie);
        }
    }

    public List<Long> executeUpdateStatus() {
        try {
            log.info("******** Update status Process Started");

            logStartEventToAzure();

            final List<Long> keyworkerIds = applyKeyworkerActiveDate();

            logEndEventToAzure(keyworkerIds);


            log.info("******** Update Status Process Ended");

            return keyworkerIds;
        } catch (Exception e) {
            log.error("Batch exception", e);
            telemetryClient.trackException(e);
        }
        return null;
    }

    private List<Long> applyKeyworkerActiveDate() {

        final LocalDate today = LocalDate.now();

        final List<Keyworker> returningKeyworkers = keyworkerRepository.findByStatusAndActiveDateBefore(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, today.plusDays(1));

        returningKeyworkers.forEach(kw -> {
            log.debug("Updating keyworker {}, changing status to ACTIVE from {}", kw.getStaffId(), kw.getStatus());
            kw.setActiveDate(null);
            kw.setStatus(KeyworkerStatus.ACTIVE);
            kw.setAutoAllocationFlag(true);
        });

        return returningKeyworkers.stream().map(Keyworker::getStaffId).collect(Collectors.toList());
    }

    private void logStartEventToAzure() {
        final Map<String, String> logMap = new HashMap<>();
        telemetryClient.trackEvent("updateStatus", logMap, null);
    }

    private void logEndEventToAzure(List keyworkers) {
        final Map<String, String> logMap = new HashMap<>();
        logMap.put("KeyworkersUpdated", keyworkers.toString());
        telemetryClient.trackEvent("updateStatus", logMap, null);
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
