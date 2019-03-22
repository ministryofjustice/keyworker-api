package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.core.dependencies.google.gson.Gson;
import com.microsoft.applicationinsights.core.dependencies.google.gson.GsonBuilder;
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
    private final Gson gson;

    public ReconciliationService(final NomisService nomisService,
                                 final OffenderKeyworkerRepository offenderKeyworkerRepository,
                                 final TelemetryClient telemetryClient) {
        this.nomisService = nomisService;
        this.offenderKeyworkerRepository = offenderKeyworkerRepository;
        this.telemetryClient = telemetryClient;
        gson = new GsonBuilder().create();
    }

    @Transactional
    public void reconcileKeyWorkerAllocations(final String prisonId) {
        Validate.notNull(prisonId,"prisonId");

        // get all prisoners with an active key worker in prison
        final var assignedPrisonersToKW = offenderKeyworkerRepository.findByActiveAndPrisonId(true, prisonId);
        final var offenderNosInPrison = getOffenderNosInPrison(prisonId);

        // find allocations no longer in this prison
        var missingOffenders = assignedPrisonersToKW.stream()
                .filter(kw -> !offenderNosInPrison.contains(kw.getOffenderNo()))
                .collect(Collectors.toList());
        log.info("There are {} missing prisoners from {}", missingOffenders.size(), prisonId);

        final var notFoundOffenders = new AtomicInteger();
        final var deallocatedOffenders = new AtomicInteger();
        final var mergedRecords = new HashMap<String, String>();
        missingOffenders.forEach( notFoundOffender -> nomisService.getPrisonerDetail(notFoundOffender.getOffenderNo()).ifPresentOrElse(
                prisonerDetail -> deallocateIfMoved(prisonId, notFoundOffender, prisonerDetail, deallocatedOffenders),
                () -> {
                    // check if its a merge
                    log.info("{} not found - Checking if it has been merged", notFoundOffender.getOffenderNo());
                    notFoundOffenders.getAndIncrement();
                    final var mergeData = nomisService.getIdentifierByTypeAndValue("MERGED", notFoundOffender.getOffenderNo());
                    mergeData.stream().map(PrisonerIdentifier::getOffenderNo).findFirst().ifPresentOrElse(
                            newOffenderNo -> offenderKeyworkerRepository.findByOffenderNo(notFoundOffender.getOffenderNo()).forEach(
                                    offenderKeyWorker -> {
                                        log.info("Offender Merged from {} to {}", offenderKeyWorker.getOffenderNo(), newOffenderNo);
                                        if (offenderKeyWorker.isActive() && !offenderKeyworkerRepository.findByActiveAndOffenderNo(true, newOffenderNo).isEmpty()) {
                                            offenderKeyWorker.deallocate(LocalDateTime.now(), DeallocationReason.MERGED);
                                            log.info("Offender already re-allocated - deallocating {}", offenderKeyWorker.getOffenderNo());
                                        }
                                        offenderKeyWorker.setOffenderNo(newOffenderNo);

                                        nomisService.getPrisonerDetail(newOffenderNo).ifPresent(
                                                prisonerDetail -> deallocateIfMoved(prisonId, offenderKeyWorker, prisonerDetail, deallocatedOffenders)
                                        );

                                        mergedRecords.put(notFoundOffender.getOffenderNo(), newOffenderNo);
                                    }
                            ),
                            () -> {
                                // can't find but remove anyway.
                                log.warn("Cannot find this prisoner {}, de-allocating...", notFoundOffender.getOffenderNo());
                                notFoundOffender.deallocate(LocalDateTime.now(), DeallocationReason.MISSING);
                                deallocatedOffenders.getAndIncrement();
                            }
                    );
                }
        ));

        logMetrics(prisonId, assignedPrisonersToKW, missingOffenders, deallocatedOffenders, mergedRecords);
    }

    private void logMetrics(final String prisonId, final List<OffenderKeyworker> assignedPrisonersToKW, final List<OffenderKeyworker> missingOffenders, final AtomicInteger deallocatedOffenders, final Map<String, String> mergedRecords) {
        final var metrics = new HashMap<String, String>();

        metrics.put("prisonId", prisonId);
        metrics.put("activeKeyWorkerAllocations", String.valueOf(assignedPrisonersToKW.size()));
        metrics.put("unmatchedOffenders", String.valueOf(missingOffenders.size()));
        metrics.put("deallocatedOffenders", String.valueOf(deallocatedOffenders.doubleValue()));
        metrics.put("numberMerged", String.valueOf(mergedRecords.size()));
        metrics.put("mergedRecords", gson.toJson(mergedRecords));

        log.info("Recon Metrics {}", metrics);

        telemetryClient.trackEvent("reconciliation", metrics, null);
    }

    private List<String> getOffenderNosInPrison(final String prisonId) {
        // get all offenders in prison at the moment
        final var activePrisoners = nomisService.getOffendersAtLocation(prisonId, "bookingId", SortOrder.ASC, true);
        log.info("There are currently {} prisoners in {}", activePrisoners.size(), prisonId);

        // get a distinct list of offenderNos
        return activePrisoners.stream().map(OffenderLocationDto::getOffenderNo).distinct().collect(Collectors.toList());
    }

    private void deallocateIfMoved(final String prisonId, final OffenderKeyworker offenderKeyWorker, final PrisonerDetail prisonerDetail, final AtomicInteger deallocatedOffenders) {
        if (!prisonerDetail.getLatestLocationId().equals(prisonId)) {
            // deallocate
            log.info("Offender {} no longer in {}, now {}, in prison? = {}", prisonerDetail.getOffenderNo(), prisonId, prisonerDetail.getLatestLocationId(), prisonerDetail.isInPrison());
            offenderKeyWorker.deallocate(LocalDateTime.now(), prisonerDetail.isInPrison() ? DeallocationReason.TRANSFER : DeallocationReason.RELEASED);
            deallocatedOffenders.getAndIncrement();
        }
    }

    public void raiseStatsProcessingError(final String prisonId, final Exchange exchange) {
        final var logMap = new HashMap<String, String>();
        logMap.put("prisonId", prisonId);

        telemetryClient.trackException(exchange.getException(), logMap, null);
    }

}
