package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.model.Keyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;
import uk.gov.justice.digital.hmpps.keyworker.repository.KeyworkerRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class KeyworkerBatchService {
    private final KeyworkerRepository keyworkerRepository;
    private TelemetryClient telemetryClient;

    public KeyworkerBatchService(final KeyworkerRepository keyworkerRepository,
                                 final TelemetryClient telemetryClient) {
        this.keyworkerRepository = keyworkerRepository;
        this.telemetryClient = telemetryClient;
    }

    public List<Long> executeUpdateStatus() {
        try {
            log.info("******** Update status Process Started");

            final var keyworkerIds = applyKeyworkerActiveDate();

            logUpdateStatusEventToAzure(keyworkerIds);

            log.info("******** Update Status Process Ended");

            return keyworkerIds;
        } catch (final Exception e) {
            log.error("Batch exception", e);
            telemetryClient.trackException(e);
        }
        return null;
    }

    private List<Long> applyKeyworkerActiveDate() {

        final var today = LocalDate.now();

        final var returningKeyworkers = keyworkerRepository.findByStatusAndActiveDateBefore(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, today.plusDays(1));

        returningKeyworkers.forEach(kw -> {
            log.debug("Updating keyworker {}, changing status to ACTIVE from {}", kw.getStaffId(), kw.getStatus());
            kw.setActiveDate(null);
            kw.setStatus(KeyworkerStatus.ACTIVE);
            kw.setAutoAllocationFlag(true);
        });

        return returningKeyworkers.stream().map(Keyworker::getStaffId).collect(Collectors.toList());
    }

    private void logUpdateStatusEventToAzure(final List keyworkers) {
        final Map<String, String> logMap = new HashMap<>();
        logMap.put("KeyworkersUpdated", keyworkers.toString());
        telemetryClient.trackEvent("updateStatus", logMap, null);
    }

}
