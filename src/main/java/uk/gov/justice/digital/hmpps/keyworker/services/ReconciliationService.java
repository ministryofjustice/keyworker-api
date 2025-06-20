package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerDetail;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerIdentifier;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;
import uk.gov.justice.digital.hmpps.keyworker.events.OffenderEventListener.OffenderEvent;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworkerAllocation;
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyKeyworkerAllocationRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ReconciliationService {

    private final NomisService nomisService;
    private final LegacyKeyworkerAllocationRepository offenderKeyworkerRepository;
    private final TelemetryClient telemetryClient;
    private final ReferenceDataRepository referenceDataRepository;

    @Transactional
    public ReconMetrics reconcileKeyWorkerAllocations(final String prisonId) {
        Validate.notNull(prisonId,"prisonId");

        // get all prisoners with an active key worker in prison
        final var assignedPrisonersToKW = offenderKeyworkerRepository.findByActiveAndPrisonCode(true, prisonId);
        log.info("There are {} active key-worker allocations in {}", assignedPrisonersToKW.size(), prisonId);

        final var offenderNosInPrison = getOffenderNosInPrison(prisonId);

        // find allocations no longer in this prison
        final var missingOffenders = assignedPrisonersToKW.stream()
                .filter(kw -> !offenderNosInPrison.contains(kw.getPersonIdentifier()))
                .collect(Collectors.toList());
        log.info("There are {} missing prisoners from {}", missingOffenders.size(), prisonId);

        final var reconMetrics = new ReconMetrics(prisonId, assignedPrisonersToKW.size(), missingOffenders.size());

        missingOffenders.forEach(notFoundOffender -> nomisService.getPrisonerDetail(notFoundOffender.getPersonIdentifier(), true).ifPresentOrElse(
                prisonerDetail -> deallocateIfMoved(prisonId, notFoundOffender, prisonerDetail, reconMetrics),
                () -> {
                    // check if its a merge
                    log.info("{} not found - Checking if it has been merged", notFoundOffender.getPersonIdentifier());
                    reconMetrics.notFoundOffenders.getAndIncrement();
                    checkAndMerge(prisonId, reconMetrics, notFoundOffender);
                }
        ));

        logMetrics(reconMetrics);
        return reconMetrics;
    }

    private void checkAndMerge(final String prisonId, final ReconMetrics reconMetrics, final LegacyKeyworkerAllocation notFoundOffender) {
        final var mergeData = nomisService.getIdentifierByTypeAndValue("MERGED", notFoundOffender.getPersonIdentifier());
        mergeData.stream().map(PrisonerIdentifier::getOffenderNo).findFirst().ifPresentOrElse(
                newOffenderNo -> offenderKeyworkerRepository.findByPersonIdentifier(notFoundOffender.getPersonIdentifier()).forEach(
                        offenderKeyWorker -> mergeRecord(prisonId, notFoundOffender.getPersonIdentifier(), newOffenderNo, offenderKeyWorker, reconMetrics)
                ),
                () -> removeMissingRecord(reconMetrics, notFoundOffender)
        );
    }

    private void removeMissingRecord(final ReconMetrics reconMetrics, final LegacyKeyworkerAllocation notFoundOffender) {
        // can't find but remove anyway.
        log.warn("Cannot find this prisoner {}, de-allocating...", notFoundOffender.getPersonIdentifier());
        notFoundOffender.deallocate(LocalDateTime.now(), getDeallocationReason(DeallocationReason.MISSING));
        reconMetrics.deAllocatedOffenders.getAndIncrement();
        reconMetrics.missingOffenders.getAndIncrement();
    }

    private void mergeRecord(final String prisonId, final String oldOffenderNo, final String newOffenderNo, final LegacyKeyworkerAllocation offenderKeyWorker, final ReconMetrics reconMetrics) {
        mergeOffenders(oldOffenderNo, newOffenderNo, offenderKeyWorker, reconMetrics);

        nomisService.getPrisonerDetail(newOffenderNo, true).ifPresent(
                prisonerDetail -> deallocateIfMoved(prisonId, offenderKeyWorker, prisonerDetail, reconMetrics)
        );
    }

    private void mergeOffenders(final String oldOffenderNo, final String newOffenderNo, final LegacyKeyworkerAllocation offenderKeyWorker, final ReconMetrics reconMetrics) {
        log.info("Allocation ID {} - Offender Merged from {} to {}", offenderKeyWorker.getId(), oldOffenderNo, newOffenderNo);
        if (offenderKeyWorker.isActive() && !offenderKeyworkerRepository.findByActiveAndPersonIdentifier(true, newOffenderNo).isEmpty()) {
            log.info("Offender already re-allocated - de-allocating {}", offenderKeyWorker.getPersonIdentifier());
            offenderKeyWorker.deallocate(LocalDateTime.now(), getDeallocationReason(DeallocationReason.MERGED));
        }
        offenderKeyWorker.setPersonIdentifier(newOffenderNo);
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

    private void deallocateIfMoved(final String prisonId, final LegacyKeyworkerAllocation offenderKeyWorker, final PrisonerDetail prisonerDetail, final ReconMetrics reconMetrics) {
        if (!prisonerDetail.getLatestLocationId().equals(prisonId)) {
            // deallocate
            log.info("Offender {} no longer in {}, now {}, in prison? = {}", prisonerDetail.getOffenderNo(), prisonId, prisonerDetail.getLatestLocationId(), prisonerDetail.isInPrison());
            offenderKeyWorker.deallocate(LocalDateTime.now(), getDeallocationReason(prisonerDetail.isInPrison() ? DeallocationReason.TRANSFER : DeallocationReason.RELEASED));
            reconMetrics.deAllocatedOffenders.getAndIncrement();
        }
    }

    public void raiseProcessingError(final String prisonId, final Exception exception) {
        final var logMap = new HashMap<String, String>();
        logMap.put("prisonId", prisonId);

        telemetryClient.trackException(exception, logMap, null);
    }

    public void checkMovementAndDeallocate(final OffenderEvent movement) {
        log.debug("Check for Transfer/Release and Deallocate for booking {} seq {}", movement.getBookingId(), movement.getMovementSeq());

        // check if movement out and rel or trn or in and adm
        if (("OUT".equals(movement.getDirectionCode()) && ("TRN".equals(movement.getMovementType()) || "REL".equals(movement.getMovementType())))
          || ("IN".equals(movement.getDirectionCode()) && "ADM".equals(movement.getMovementType())) ) {
            // check if prisoner is in this system if so, deallocate
            offenderKeyworkerRepository.findByActiveAndPersonIdentifier(true, movement.getOffenderIdDisplay())
                    .forEach(offenderKeyWorker -> checkValidTransferOrRelease(movement, offenderKeyWorker));
        }

    }

    private void checkValidTransferOrRelease(final OffenderEvent movement, final LegacyKeyworkerAllocation offenderKeyWorker) {
        log.debug("Offender {} moved from {} to {} (type {})", movement.getOffenderIdDisplay(), movement.getFromAgencyLocationId(), movement.getToAgencyLocationId(), movement.getMovementType());

        // check that FROM agency is from where the key-worker / prisoner relationship resides and the TO agency is not the same prison!
        if (!offenderKeyWorker.getPrisonCode().equals(movement.getToAgencyLocationId())) {
            // check if its a transfer then its to another prison
            if ("TRN".equals(movement.getMovementType())) {
                if (nomisService.isPrison(movement.getToAgencyLocationId())) {
                    offenderKeyWorker.deallocate(movement.getMovementDateTime(), getDeallocationReason(DeallocationReason.TRANSFER));
                }
            } else if ("REL".equals(movement.getMovementType())) {
                offenderKeyWorker.deallocate(movement.getMovementDateTime(), getDeallocationReason(DeallocationReason.RELEASED));

            } else if ("ADM".equals(movement.getMovementType())) {
                offenderKeyWorker.deallocate(movement.getMovementDateTime(), getDeallocationReason(DeallocationReason.TRANSFER));
            }
        }
    }

    private ReferenceData getDeallocationReason(DeallocationReason reason) {
        return referenceDataRepository.findByKey(new ReferenceDataKey(ReferenceDataDomain.DEALLOCATION_REASON, reason.getReasonCode()));
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
