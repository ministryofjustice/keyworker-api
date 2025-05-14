package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyKeyworkerRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKt.getKeyworkerStatus;

@Service
@Transactional
@AllArgsConstructor
@Slf4j
public class KeyworkerBatchService {
    private final LegacyKeyworkerRepository keyworkerRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final TelemetryClient telemetryClient;

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

        final var returningKeyworkers = keyworkerRepository.findByStatusKeyCodeAndReactivateOnBefore(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE.name(), today.plusDays(1));
        final var status = getKeyworkerStatus(referenceDataRepository, KeyworkerStatus.ACTIVE);

        returningKeyworkers.forEach(kw -> {
            log.debug("Updating keyworker {}, changing status to ACTIVE from {}", kw.getStaffId(), kw.getStatus());
            kw.setReactivateOn(null);
            kw.setStatus(status);
            kw.setAutoAllocation(true);
        });

        return returningKeyworkers.stream().map(LegacyKeyworker::getStaffId).collect(Collectors.toList());
    }

    private void logUpdateStatusEventToAzure(final List keyworkers) {
        final Map<String, String> logMap = new HashMap<>();
        logMap.put("KeyworkersUpdated", keyworkers.toString());
        telemetryClient.trackEvent("updateStatus", logMap, null);
    }

}
