package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.actuate.metrics.buffer.BufferMetricReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderSummaryDto;
import uk.gov.justice.digital.hmpps.keyworker.exception.AllocationException;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service implementation of Key worker auto-allocation. On initiation the auto-allocation process will attempt to
 * allocate all unallocated offenders within specified agency. If there is insufficient capacity among available Key
 * workers to accommodate allocation of all unallocated offenders, offenders will be allocated until there is no
 * further capacity and then the auto-allocation process will terminate.
 */
@Service
@Transactional(noRollbackFor = {AllocationException.class})
@Slf4j
public class KeyworkerAutoAllocationService {
    public static final String COUNTER_METRIC_KEYWORKER_AUTO_ALLOCATIONS = "counter.keyworker.allocations.auto";
    public static final String OUTCOME_NO_UNALLOCATED_OFFENDERS = "No unallocated offenders.";
    public static final String OUTCOME_NO_AVAILABLE_KEY_WORKERS = "No Key workers available for allocation.";
    public static final String OUTCOME_AUTO_ALLOCATION_SUCCESS = "Offender with bookingId [{}] successfully auto-allocated to Key worker with staffId [{}].";

    private final KeyworkerService keyworkerService;
    private final KeyworkerPoolFactory keyworkerPoolFactory;
    private final CounterService counterService;
    private final BufferMetricReader metricReader;
    private final long offenderPageLimit;

    /**
     * Constructor.
     *
     * @param keyworkerService key worker allocation service.
     * @param keyworkerPoolFactory factory that facilitates creation of Key worker pools.
     */
    public KeyworkerAutoAllocationService(KeyworkerService keyworkerService,
                                          KeyworkerPoolFactory keyworkerPoolFactory,
                                          CounterService counterService,
                                          BufferMetricReader metricReader) {
        this.keyworkerService = keyworkerService;
        this.keyworkerPoolFactory = keyworkerPoolFactory;
        this.counterService = counterService;
        this.metricReader = metricReader;

        this.offenderPageLimit = 10L;
    }

//    @VerifyAgencyAccess
    public Long autoAllocate(String agencyId) throws AllocationException {
        // Confirm a valid agency has been supplied.
        Validate.isTrue(StringUtils.isNotBlank(agencyId), "Agency id must be provided.");

        keyworkerService.verifyAgencySupport(agencyId);

        log.info("Key worker auto-allocation process initiated for agency [{}].", agencyId);

        // Get initial counter metric
        long startAllocCount = getCurrentAllocationCount();

        // Get all unallocated offenders for agency
        List<OffenderSummaryDto> unallocatedOffenders = getUnallocatedOffenders(agencyId);

        // Are there any unallocated offenders? If not, log and exit, otherwise proceed.
        if (unallocatedOffenders.isEmpty()) {
            log.info(OUTCOME_NO_UNALLOCATED_OFFENDERS);
        } else {
            List<KeyworkerDto> availableKeyworkers = keyworkerService.getAvailableKeyworkers(agencyId);

            if (availableKeyworkers.isEmpty()) {
                log.error(OUTCOME_NO_AVAILABLE_KEY_WORKERS);

                throw AllocationException.withMessage(OUTCOME_NO_AVAILABLE_KEY_WORKERS);
            }

            log.info("Proceeding with auto-allocation for {} unallocated offenders and {} available Key workers at agency [{}].",
                    unallocatedOffenders.size(), availableKeyworkers.size(), agencyId);

            // At this point, we have some unallocated offenders and some available Key workers. Let's put the Key
            // workers into a pool then start processing allocations.
            KeyworkerPool keyworkerPool = keyworkerPoolFactory.getKeyworkerPool(availableKeyworkers);

            // Continue processing allocations for unallocated offenders until no further unallocated offenders exist
            // or Key workers no longer have capacity.
            try {
                processAllocations(unallocatedOffenders, keyworkerPool);
            } catch(AllocationException aex) {
                long allocCount = calcAndLogAllocationsProcessed(agencyId, startAllocCount);

                log.info("Key worker auto-allocation terminated after processing {} allocations.", allocCount);
                log.error("Reason for termination: {}", aex.getMessage());

                throw aex;
            }
        }

        return calcAndLogAllocationsProcessed(agencyId, startAllocCount);
    }

    private void processAllocations(List<OffenderSummaryDto> offenders, KeyworkerPool keyworkerPool) {
        // Process allocation for each unallocated offender
        for (OffenderSummaryDto offender : offenders) {
            processAllocation(offender, keyworkerPool);
        }
    }

    private void processAllocation(OffenderSummaryDto offender, KeyworkerPool keyworkerPool) {
        KeyworkerDto keyworker = keyworkerPool.getKeyworker(offender.getOffenderNo());

        // At this point, Key worker to which offender will be allocated has been identified - create allocation
        confirmAllocation(offender, keyworker);

        // Update Key worker pool with refreshed Key worker (following successful allocation)
        KeyworkerDto refreshedKeyworker = keyworkerService.getKeyworkerDetails(keyworker.getStaffId());

        keyworkerPool.refreshKeyworker(refreshedKeyworker);
    }

    private List<OffenderSummaryDto> getUnallocatedOffenders(String agencyId) {
        return keyworkerService.getUnallocatedOffenders(agencyId, null,null);
    }

    private void confirmAllocation(OffenderSummaryDto offender, KeyworkerDto keyworker) {
        OffenderKeyworker keyWorkerAllocation = buildKeyWorkerAllocation(offender, keyworker);

        keyworkerService.allocate(keyWorkerAllocation);

        counterService.increment(COUNTER_METRIC_KEYWORKER_AUTO_ALLOCATIONS);

        log.info(OUTCOME_AUTO_ALLOCATION_SUCCESS, offender.getBookingId(), keyworker.getStaffId());
    }

    private OffenderKeyworker buildKeyWorkerAllocation(OffenderSummaryDto offender, KeyworkerDto keyworker) {
        return OffenderKeyworker.builder()
                .offenderNo(offender.getOffenderNo())
                .staffId(keyworker.getStaffId())
                .agencyId(offender.getAgencyLocationId())
                .allocationReason(AllocationReason.AUTO)
                .active(true)
                .assignedDateTime(LocalDateTime.now())
                .allocationType(AllocationType.AUTO)
                .build();
    }

    private Long calcAndLogAllocationsProcessed(String agencyId, long startAllocCount) {
        // Determine total allocations for this execution of auto-allocation process.
        long allocCount = getCurrentAllocationCount() - startAllocCount;

        log.info("Processed {} allocations for agency [{}].", allocCount, agencyId);

        return allocCount;
    }

    private long getCurrentAllocationCount() {
        long allocCount = 0;

        Metric metricAllocCount = metricReader.findOne(COUNTER_METRIC_KEYWORKER_AUTO_ALLOCATIONS);

        if (metricAllocCount != null) {
            allocCount = metricAllocCount.getValue().longValue();
        }

        return allocCount;
    }
}
