package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;
import uk.gov.justice.digital.hmpps.keyworker.events.EventListener.OffenderEvent;
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
        final var missingOffenders = assignedPrisonersToKW.stream()
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

    private void checkAndMerge(final String prisonId, final ReconMetrics reconMetrics, final OffenderKeyworker notFoundOffender) {
        final var mergeData = nomisService.getIdentifierByTypeAndValue("MERGED", notFoundOffender.getOffenderNo());
        mergeData.stream().map(PrisonerIdentifier::getOffenderNo).findFirst().ifPresentOrElse(
                newOffenderNo -> offenderKeyworkerRepository.findByOffenderNo(notFoundOffender.getOffenderNo()).forEach(
                        offenderKeyWorker -> mergeRecord(prisonId, notFoundOffender.getOffenderNo(), newOffenderNo, offenderKeyWorker, reconMetrics)
                ),
                () -> removeMissingRecord(reconMetrics, notFoundOffender)
        );
    }

    private void removeMissingRecord(final ReconMetrics reconMetrics, final OffenderKeyworker notFoundOffender) {
        // can't find but remove anyway.
        log.warn("Cannot find this prisoner {}, de-allocating...", notFoundOffender.getOffenderNo());
        notFoundOffender.deallocate(LocalDateTime.now(), DeallocationReason.MISSING);
        reconMetrics.deAllocatedOffenders.getAndIncrement();
        reconMetrics.missingOffenders.getAndIncrement();
    }

    private void mergeRecord(final String prisonId, final String oldOffenderNo, final String newOffenderNo, final OffenderKeyworker offenderKeyWorker, final ReconMetrics reconMetrics) {
        mergeOffenders(oldOffenderNo, newOffenderNo, offenderKeyWorker, reconMetrics);

        nomisService.getPrisonerDetail(newOffenderNo, true).ifPresent(
                prisonerDetail -> deallocateIfMoved(prisonId, offenderKeyWorker, prisonerDetail, reconMetrics)
        );
    }

    private void mergeOffenders(final String oldOffenderNo, final String newOffenderNo, final OffenderKeyworker offenderKeyWorker, final ReconMetrics reconMetrics) {
        log.info("Allocation ID {} - Offender Merged from {} to {}", offenderKeyWorker.getOffenderKeyworkerId(), oldOffenderNo, newOffenderNo);
        if (offenderKeyWorker.isActive() && !offenderKeyworkerRepository.findByActiveAndOffenderNo(true, newOffenderNo).isEmpty()) {
            log.info("Offender already re-allocated - de-allocating {}", offenderKeyWorker.getOffenderNo());
            offenderKeyWorker.deallocate(LocalDateTime.now(), DeallocationReason.MERGED);
        }
        offenderKeyWorker.setOffenderNo(newOffenderNo);
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

    public void checkForMergeAndDeallocate(final OffenderEvent offenderEvent) {
        log.debug("Check for merged booking for ID {}", offenderEvent.getBookingId());
        nomisService.getIdentifiersByBookingId(offenderEvent.getBookingId()).stream()
                .filter(id -> "MERGED".equals(id.getType()))
                .forEach(id -> nomisService.getBooking(offenderEvent.getBookingId())
                        .ifPresent(booking -> offenderKeyworkerRepository.findByOffenderNo(id.getValue())
                                .forEach(offenderKeyWorker -> mergeOffenders(id.getValue(), booking.getOffenderNo(), offenderKeyWorker, new ReconMetrics(offenderKeyWorker.getPrisonId(), 0, 0)
                                ))));
    }

    public void checkMovementAndDeallocate(final OffenderEvent offenderEvent) {
        log.debug("Check for Transfer/Release and Deallocate for booking {} seq {}", offenderEvent.getBookingId(), offenderEvent.getMovementSeq());
        nomisService.getMovement(offenderEvent.getBookingId(), offenderEvent.getMovementSeq())
                .ifPresent(movement -> {
                    // check if movement out
                    if (("OUT".equals(movement.getDirectionCode()) && ("TRN".equals(movement.getMovementType()) || "REL".equals(movement.getMovementType())))
                      || ("IN".equals(movement.getDirectionCode()) && "ADM".equals(movement.getMovementType())) ) {
                        // check if prisoner is in this system if so, deallocate
                        offenderKeyworkerRepository.findByActiveAndOffenderNo(true, movement.getOffenderNo())
                                .forEach(offenderKeyWorker -> checkValidTransferOrRelease(movement, offenderKeyWorker));
                    }
                });
    }

    private void checkValidTransferOrRelease(final Movement movement, final OffenderKeyworker offenderKeyWorker) {
        log.debug("Offender {} moved from {} to {} (type {})", movement.getOffenderNo(), movement.getFromAgency(), movement.getToAgency(), movement.getMovementType());

        // check that FROM agency is from where the key-worker / prisoner relationship resides and the TO agency is not the same prison!
        if (!offenderKeyWorker.getPrisonId().equals(movement.getToAgency())) {
            // check if its a transfer then its to another prison
            if ("TRN".equals(movement.getMovementType())) {
                if (nomisService.isPrison(movement.getToAgency())) {
                    offenderKeyWorker.deallocate(movement.getCreateDateTime(), DeallocationReason.TRANSFER);
                }
            } else if ("REL".equals(movement.getMovementType())) {
                offenderKeyWorker.deallocate(movement.getCreateDateTime(), DeallocationReason.RELEASED);

            } else if ("ADM".equals(movement.getMovementType())) {
                offenderKeyWorker.deallocate(movement.getCreateDateTime(), DeallocationReason.TRANSFER);
            }
        }
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

        public ReconMetrics(final String prisonId, final int activeKeyWorkerAllocations, final int unmatchedOffenders) {
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
