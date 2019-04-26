package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerDetail;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerIdentifier;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class ReconciliationService {

    private final NomisService nomisService;
    private final OffenderKeyworkerRepository offenderKeyworkerRepository;
    private final TelemetryClient telemetryClient;

    public ReconciliationService(final NomisService nomisService,
                                 final OffenderKeyworkerRepository offenderKeyworkerRepository,
                                 final TelemetryClient telemetryClient) {
        this.nomisService = nomisService;
        this.offenderKeyworkerRepository = offenderKeyworkerRepository;
        this.telemetryClient = telemetryClient;
    }

    @Transactional
    public ReconMetrics reconcileKeyWorkerAllocations(final String prisonId) {
        Validate.notNull(prisonId,"prisonId");

        // get all prisoners with an active key worker in prison
        final var assignedPrisonersToKW = offenderKeyworkerRepository.findByActiveAndPrisonId(true, prisonId);
        log.info("There are {} active key-worker allocations in {}", assignedPrisonersToKW.size(), prisonId);

        final var offenderNosInPrison = getOffenderNosInPrison(prisonId);

        // find allocations no longer in this prison
        var missingOffenders = assignedPrisonersToKW.stream()
                .filter(kw -> !offenderNosInPrison.contains(kw.getOffenderNo()))
                .collect(Collectors.toList());
        log.info("There are {} missing prisoners from {}", missingOffenders.size(), prisonId);

        final var reconMetrics = new ReconMetrics(prisonId, assignedPrisonersToKW.size(), missingOffenders.size());

        missingOffenders.forEach(notFoundOffender -> nomisService.getPrisonerDetail(notFoundOffender.getOffenderNo(), true).ifPresentOrElse(
                prisonerDetail -> deallocateIfMoved(prisonId, notFoundOffender, prisonerDetail, reconMetrics),
                () -> {
                    // check if its a merge
                    log.info("{} not found - Checking if it has been merged", notFoundOffender.getOffenderNo());
                    reconMetrics.notFoundOffenders.getAndIncrement();
                    checkAndMerge(prisonId, reconMetrics, notFoundOffender);
                }
        ));

        logMetrics(reconMetrics);
        return reconMetrics;
    }

    private void checkAndMerge(String prisonId, ReconMetrics reconMetrics, OffenderKeyworker notFoundOffender) {
        final var mergeData = nomisService.getIdentifierByTypeAndValue("MERGED", notFoundOffender.getOffenderNo());
        mergeData.stream().map(PrisonerIdentifier::getOffenderNo).findFirst().ifPresentOrElse(
                newOffenderNo -> offenderKeyworkerRepository.findByOffenderNo(notFoundOffender.getOffenderNo()).forEach(
                        offenderKeyWorker -> mergeRecord(prisonId, reconMetrics, notFoundOffender.getOffenderNo(), newOffenderNo, offenderKeyWorker)
                ),
                () -> removeMissingRecord(reconMetrics, notFoundOffender)
        );
    }

    private void removeMissingRecord(ReconMetrics reconMetrics, OffenderKeyworker notFoundOffender) {
        // can't find but remove anyway.
        log.warn("Cannot find this prisoner {}, de-allocating...", notFoundOffender.getOffenderNo());
        notFoundOffender.deallocate(LocalDateTime.now(), DeallocationReason.MISSING);
        reconMetrics.deAllocatedOffenders.getAndIncrement();
        reconMetrics.missingOffenders.getAndIncrement();
    }

    private void mergeRecord(String prisonId, ReconMetrics reconMetrics, final String oldOffenderNo, final String newOffenderNo, OffenderKeyworker offenderKeyWorker) {
        log.info("Allocation ID {} - Offender Merged from {} to {}", offenderKeyWorker.getOffenderKeyworkerId(), oldOffenderNo, newOffenderNo);
        if (offenderKeyWorker.isActive() && !offenderKeyworkerRepository.findByActiveAndOffenderNo(true, newOffenderNo).isEmpty()) {
            offenderKeyWorker.deallocate(LocalDateTime.now(), DeallocationReason.MERGED);
            log.info("Offender already re-allocated - de-allocating {}", offenderKeyWorker.getOffenderNo());
        }
        offenderKeyWorker.setOffenderNo(newOffenderNo);

        nomisService.getPrisonerDetail(newOffenderNo, true).ifPresent(
                prisonerDetail -> deallocateIfMoved(prisonId, offenderKeyWorker, prisonerDetail, reconMetrics)
        );

        reconMetrics.mergedRecords.put(oldOffenderNo, newOffenderNo);
    }

    private void logMetrics(final ReconMetrics metrics) {
        log.info("Recon Metrics {}", metrics);
        telemetryClient.trackEvent("reconciliation", metrics.getProperties(), null);
    }

    private List<String> getOffenderNosInPrison(final String prisonId) {
        // get all offenders in prison at the moment
        final var activePrisoners = nomisService.getOffendersAtLocation(prisonId, "bookingId", SortOrder.ASC, true);
        log.info("There are currently {} prisoners in {}", activePrisoners.size(), prisonId);

        // get a distinct list of offenderNos
        return activePrisoners.stream().map(OffenderLocationDto::getOffenderNo).distinct().collect(Collectors.toList());
    }

    private void deallocateIfMoved(final String prisonId, final OffenderKeyworker offenderKeyWorker, final PrisonerDetail prisonerDetail, final ReconMetrics reconMetrics) {
        if (!prisonerDetail.getLatestLocationId().equals(prisonId)) {
            // deallocate
            log.info("Offender {} no longer in {}, now {}, in prison? = {}", prisonerDetail.getOffenderNo(), prisonId, prisonerDetail.getLatestLocationId(), prisonerDetail.isInPrison());
            offenderKeyWorker.deallocate(LocalDateTime.now(), prisonerDetail.isInPrison() ? DeallocationReason.TRANSFER : DeallocationReason.RELEASED);
            reconMetrics.deAllocatedOffenders.getAndIncrement();
        }
    }

    public void raiseProcessingError(final String prisonId, final Exchange exchange) {
        final var logMap = new HashMap<String, String>();
        logMap.put("prisonId", prisonId);

        telemetryClient.trackException(exchange.getException(), logMap, null);
    }

    @ToString
    @Getter
    public static class ReconMetrics {
        private final String prisonId;
        private final int activeKeyWorkerAllocations;
        private final int unmatchedOffenders;
        private final AtomicInteger notFoundOffenders = new AtomicInteger();
        private final AtomicInteger deAllocatedOffenders = new AtomicInteger();
        private final AtomicInteger missingOffenders = new AtomicInteger();
        private final Map<String, String> mergedRecords = new HashMap<>();

        public ReconMetrics(String prisonId, int activeKeyWorkerAllocations, int unmatchedOffenders) {
            this.prisonId = prisonId;
            this.activeKeyWorkerAllocations = activeKeyWorkerAllocations;
            this.unmatchedOffenders = unmatchedOffenders;
        }

        Map<String, String> getProperties() {
            final var props1 =  Map.of(
                    "prisonId", prisonId,
                    "activeKeyWorkerAllocations", String.valueOf(activeKeyWorkerAllocations),
                    "unmatchedOffenders", String.valueOf(unmatchedOffenders),
                    "notFoundOffenders", notFoundOffenders.toString(),
                    "deAllocatedOffenders", deAllocatedOffenders.toString(),
                    "missingOffenders", missingOffenders.toString(),
                    "numberMerged", String.valueOf(mergedRecords.size())
            );

            final var mergedProps = new HashMap<>(props1);
            mergedProps.putAll(mergedRecords);
            return mergedProps;
        }

    }
}
