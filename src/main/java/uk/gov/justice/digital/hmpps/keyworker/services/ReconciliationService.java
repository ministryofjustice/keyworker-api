package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository;
import uk.gov.justice.digital.hmpps.keyworker.events.OffenderEventListener.OffenderEvent;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworkerAllocation;
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyKeyworkerAllocationRepository;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ReconciliationService {

    private final NomisService nomisService;
    private final LegacyKeyworkerAllocationRepository offenderKeyworkerRepository;
    private final ReferenceDataRepository referenceDataRepository;

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
}
