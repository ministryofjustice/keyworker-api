package uk.gov.justice.digital.hmpps.keyworker.services;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.exception.AllocationException;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
    private static final String COUNTER_METRIC_KEYWORKER_AUTO_ALLOCATIONS = "counter.keyworker.allocations.auto";
    private static final String OUTCOME_NO_UNALLOCATED_OFFENDERS = "No unallocated offenders.";
    static final String OUTCOME_NO_AVAILABLE_KEY_WORKERS = "No Key workers available for allocation.";
    private static final String OUTCOME_AUTO_ALLOCATION_SUCCESS = "Offender with bookingId [{}] successfully auto-allocated to Key worker with staffId [{}].";

    private final KeyworkerService keyworkerService;
    private final KeyworkerPoolFactory keyworkerPoolFactory;
    private final OffenderKeyworkerRepository offenderKeyworkerRepository;
    private final PrisonSupportedService prisonSupportedService;
    private final ComplexityOfNeedService complexityOfNeedService;
    /**
     * Constructor.
     *
     * @param keyworkerService     key worker allocation service.
     * @param keyworkerPoolFactory factory that facilitates creation of Key worker pools.
     */
    public KeyworkerAutoAllocationService(final KeyworkerService keyworkerService,
                                          final KeyworkerPoolFactory keyworkerPoolFactory,
                                          final OffenderKeyworkerRepository offenderKeyworkerRepository,
                                          final PrisonSupportedService prisonSupportedService,
                                          final ComplexityOfNeedService complexityOfNeedService) {
        this.keyworkerService = keyworkerService;
        this.keyworkerPoolFactory = keyworkerPoolFactory;
        this.offenderKeyworkerRepository = offenderKeyworkerRepository;
        this.prisonSupportedService = prisonSupportedService;
        this.complexityOfNeedService = complexityOfNeedService;
    }

    @PreAuthorize("hasAnyRole('OMIC_ADMIN')")
    public long autoAllocate(final String prisonId) throws AllocationException {
        // Confirm a valid prison has been supplied.
        Validate.isTrue(StringUtils.isNotBlank(prisonId), "Prison id must be provided.");

        prisonSupportedService.verifyPrisonSupportsAutoAllocation(prisonId);

        log.info("Key worker auto-allocation process initiated for agency [{}].", prisonId);

        // Tidy up any abandoned previous run
        final var rows = clearExistingProvisionals(prisonId);
        if (rows > 0) {
            log.info("Cleared {} pre-existing provisional allocations at agency {}.", rows, prisonId);
        }

        // Get initial counter metric
        final var counter = initialiseCounter();
        final var startAllocCount = counter.count();

        // Get all unallocated offenders for agency
        final var unallocatedOffenders = stripComplexOffenders(prisonId, getUnallocatedOffenders(prisonId));

        // Are there any unallocated offenders? If not, log and exit, otherwise proceed.
        if (unallocatedOffenders.isEmpty()) {
            log.info(OUTCOME_NO_UNALLOCATED_OFFENDERS);
        } else {
            final var availableKeyworkers = keyworkerService.getKeyworkersAvailableForAutoAllocation(prisonId);

            if (availableKeyworkers.isEmpty()) {
                log.error(OUTCOME_NO_AVAILABLE_KEY_WORKERS);

                throw AllocationException.withMessage(OUTCOME_NO_AVAILABLE_KEY_WORKERS);
            }

            log.info("Proceeding with auto-allocation for {} unallocated offenders and {} available Key workers at agency [{}].",
                unallocatedOffenders.size(), availableKeyworkers.size(), prisonId);

            // At this point, we have some unallocated offenders and some available Key workers. Let's put the Key
            // workers into a pool then start processing allocations.
            final var keyworkerPool = keyworkerPoolFactory.getKeyworkerPool(prisonId, availableKeyworkers);

            // Continue processing allocations for unallocated offenders until no further unallocated offenders exist
            // or Key workers no longer have capacity.
            try {
                processAllocations(unallocatedOffenders, keyworkerPool, counter);
            } catch (final AllocationException aex) {
                final var allocCount = calcAndLogAllocationsProcessed(prisonId, startAllocCount, counter);

                log.info("Key worker auto-allocation terminated after processing {} allocations.", allocCount);
                log.error("Reason for termination: {}", aex.getMessage());

                throw aex;
            }
        }

        return (long) calcAndLogAllocationsProcessed(prisonId, startAllocCount, counter);
    }

    private List<OffenderLocationDto> stripComplexOffenders(final String prisonId, final List<OffenderLocationDto> unAllocated) {
        final var unAllocatedOffenderNos = unAllocated.stream()
            .map(OffenderLocationDto::getOffenderNo)
            .collect(Collectors.toSet());

        final var complexOffenders = complexityOfNeedService.getComplexOffenders(prisonId, unAllocatedOffenderNos);

        return unAllocated.stream()
            .filter(offenderLocation -> !complexOffenders.contains(offenderLocation.getOffenderNo()))
            .collect(Collectors.toList());
    }


    @PreAuthorize("hasAnyRole('OMIC_ADMIN')")
    public Long confirmAllocations(final String prisonId) {
        prisonSupportedService.verifyPrisonMigrated(prisonId);
        return (long) offenderKeyworkerRepository.confirmProvisionals(prisonId);
    }

    private int clearExistingProvisionals(final String prisonId) {
        return offenderKeyworkerRepository.deleteExistingProvisionals(prisonId);
    }

    private void processAllocations(final List<OffenderLocationDto> offenders, final KeyworkerPool keyworkerPool, final Counter counter) {
        // Process allocation for each unallocated offender
        for (final var offender : offenders) {
            processAllocation(offender, keyworkerPool, counter);
        }
    }

    private void processAllocation(final OffenderLocationDto offender, final KeyworkerPool keyworkerPool, final Counter counter) {
        final var keyworker = keyworkerPool.getKeyworker(offender.getOffenderNo());

        // At this point, Key worker to which offender will be allocated has been identified - create provisional allocation
        storeAllocation(offender, keyworker, counter);

        // Update Key worker pool with refreshed Key worker (following successful allocation)
        keyworkerPool.incrementAndRefreshKeyworker(keyworker);
    }

    private List<OffenderLocationDto> getUnallocatedOffenders(final String prisonId) {
        return keyworkerService.getUnallocatedOffenders(prisonId, null, null);
    }

    private void storeAllocation(final OffenderLocationDto offender, final KeyworkerDto keyworker, final Counter counter) {
        final var keyWorkerAllocation = buildKeyWorkerAllocation(offender, keyworker);

        keyworkerService.allocate(keyWorkerAllocation);

        counter.increment();

        log.info(OUTCOME_AUTO_ALLOCATION_SUCCESS, offender.getBookingId(), keyworker.getStaffId());
    }

    private OffenderKeyworker buildKeyWorkerAllocation(final OffenderLocationDto offender, final KeyworkerDto keyworker) {
        return OffenderKeyworker.builder()
            .offenderNo(offender.getOffenderNo())
            .staffId(keyworker.getStaffId())
            .prisonId(offender.getAgencyId())
            .allocationReason(AllocationReason.AUTO)
            .active(true)
            .assignedDateTime(LocalDateTime.now())
            .allocationType(AllocationType.PROVISIONAL)
            .build();
    }

    private double calcAndLogAllocationsProcessed(final String prisonId, final double startAllocCount, final Counter counter) {
        // Determine total allocations for this execution of auto-allocation process.
        final var allocCount = counter.count() - startAllocCount;

        log.info("Processed {} allocations for agency [{}].", allocCount, prisonId);

        return allocCount;
    }

    private Counter initialiseCounter() {
        return Counter
            .builder(COUNTER_METRIC_KEYWORKER_AUTO_ALLOCATIONS)
            .description("indicates number of allocations suggested")
            .tags("keyworker", "allocation")
            .register(new SimpleMeterRegistry());
    }
}
