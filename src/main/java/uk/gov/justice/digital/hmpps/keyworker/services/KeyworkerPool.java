package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.util.ObjectUtils;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.exception.AllocationException;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a collection of Key workers that are available for allocation and encapsulates the implementation of
 * allocation rules which govern which Key worker is next in line for allocation at any particular moment.
 *
 * NB: KeyworkerPool is not thread safe. The pool is designed for short-lived, single-threaded operation. As a result
 * an instance of KeyworkerPool will not support multiple auto-allocation processes. Each auto-allocation process
 * should instantiate and use its own KeyworkerPool.
 */
@Slf4j
public class KeyworkerPool {
    public static final String OUTCOME_ALL_KEY_WORKERS_AT_CAPACITY = "All available Key workers are at full capacity.";

    private final SortedSet<KeyworkerDto> keyworkerPool;
    private final Map<Long,List<OffenderKeyworker>> keyworkerAllocations;
    private final SortedSet<Integer> capacityTiers;
    private final Set<Long> keyworkerStaffIds;
    private final Integer maxCapacity;

    private KeyworkerService keyworkerService;

    /**
     * Constructor.
     *
     * @param keyworkers set of Key workers in the pool.
     * @param capacityTiers optional set of capacity tier levels.
     */
    KeyworkerPool(Collection<KeyworkerDto> keyworkers, Collection<Integer> capacityTiers) {
        Validate.notEmpty(keyworkers, "Key worker pool must contain at least one Key worker.");

        // Initialise capacity tiers
        if (ObjectUtils.isEmpty(capacityTiers)) {
            this.capacityTiers = new TreeSet<>(Collections.singleton(Integer.MAX_VALUE));
        } else {
            this.capacityTiers = new TreeSet<>(capacityTiers);
        }

        maxCapacity = this.capacityTiers.last();

        // Initialise key worker pool
        keyworkerStaffIds = new HashSet<>();
        keyworkerAllocations = new HashMap<>();

        keyworkers.forEach(kw -> {
            keyworkerStaffIds.add(kw.getStaffId());
            keyworkerAllocations.put(kw.getStaffId(), null);
        });

        keyworkerPool = new TreeSet<>(buildKeyworkerComparator());
        keyworkerPool.addAll(keyworkers);

        log.debug("Key worker pool initialised with {} members.", keyworkers.size());
    }

    public void setKeyworkerService(KeyworkerService keyworkerService) {
        this.keyworkerService = keyworkerService;
    }

    // Constructs Key worker comparator which, effectively, implements Key worker allocation prioritisation algorithm.
    // Comparator function ensures that highest priority Key worker is at head of Key worker pool.
    private Comparator<KeyworkerDto> buildKeyworkerComparator() {

        // If a KW is full, kick it to last place
        Comparator<KeyworkerDto> isFullComparator = (kw1, kw2) -> {
            int enhancedCapacityKw1 = calculateEnhancedCapacity(kw1);
            int enhancedCapacityKw2 = calculateEnhancedCapacity(kw2);
            if (kw1.getNumberAllocated() >= enhancedCapacityKw1) {
                if (kw2.getNumberAllocated() >= enhancedCapacityKw2) {
                    return 0;
                }
                return 1;
            }
            if (kw2.getNumberAllocated() >= enhancedCapacityKw2) {
                return -1;
            }
            return 0;
        };

        final Comparator<KeyworkerDto> numberAllocatedComparator = Comparator.comparingInt(KeyworkerDto::getNumberAllocated);

        final Comparator<Long> staffIdComparator = (id1, id2) -> {
            Comparator<OffenderKeyworker> keyWorkerAllocationComparator = Comparator.comparing(OffenderKeyworker::getAssignedDateTime);

            SortedSet<OffenderKeyworker> id1Allocations = new TreeSet<>(keyWorkerAllocationComparator);
            SortedSet<OffenderKeyworker> id2Allocations = new TreeSet<>(keyWorkerAllocationComparator);

            Optional.ofNullable(keyworkerAllocations.get(id1)).ifPresent(id1Allocations::addAll);
            Optional.ofNullable(keyworkerAllocations.get(id2)).ifPresent(id2Allocations::addAll);

            int result;

            // If neither Key worker has any auto-allocations, or both have auto-allocations and an identical
            // assigned datetime for most recent allocation, arbitrarily sort by staffId (to ensure uniqueness).
            if (id1Allocations.isEmpty()) {
                result = id2Allocations.isEmpty() ? (id1.compareTo(id2)) : -1;
            } else if (id2Allocations.isEmpty()) {
                result = 1;
            } else {
                result = id1Allocations.first().getAssignedDateTime().compareTo(id2Allocations.first().getAssignedDateTime());

                if (result == 0) {
                    result = id1.compareTo(id2);
                }
            }
            return result;
        };

        Comparator<KeyworkerDto> comparator = isFullComparator
                .thenComparing(numberAllocatedComparator)
                .thenComparing(KeyworkerDto::getStaffId, staffIdComparator);

        return (kw1, kw2) -> {
            if (kw1 == kw2) {
                return 0;
            }
            return comparator.compare(kw1, kw2);
        };
    }

    private int calculateEnhancedCapacity(KeyworkerDto kw1) {
        final Integer capacity = kw1.getCapacity();
        if (capacityTiers.size() == 1) {
            return capacity;
        }
        final Iterator<Integer> iterator = capacityTiers.iterator();
        Integer first = iterator.next();
        Integer second = iterator.next();

        return capacity * second / first;
    }

    /**
     * Identifies and returns Key worker to whom offender should be allocated, as determined by allocation rules. Note
     * that if the returned Key worker is used for allocation of an offender, the pool must be updated with refreshed
     * Key worker details (via {@link #incrementAndRefreshKeyworker(KeyworkerDto)}).
     *
     * @param offenderNo offender number of offender for whom key worker allocation is required.
     * @return the priority {@code Keyworker}.
     */
    public KeyworkerDto getKeyworker(String offenderNo) {
        log.debug("Prioritising Key worker for allocation of offender with offenderNo [{}]", offenderNo);

        // Retrieve any previous Key worker allocations for offender.
        List<OffenderKeyworker> previousAllocations = keyworkerService.getAllocationHistoryForPrisoner(offenderNo);

        // First, determine if offender was previously allocated to any Key workers in the pool
        Optional<KeyworkerDto> previousKeyworker = findPreviousAllocation(offenderNo, previousAllocations);

        KeyworkerDto priorityKeyworker;

        if (previousKeyworker.isPresent()) {
            priorityKeyworker = previousKeyworker.get();

            log.debug("Previous allocation detected between Key worker with staffId [{}] and offender with offenderNo [{}].",
                    priorityKeyworker.getStaffId(), offenderNo);
        } else {
            log.debug("No previous Key worker allocations detected for offender with offenderNo [{}].", offenderNo);

            prioritiseKeyworkers();

            // Check allocation level for Key worker at head of pool list - if at or beyond maximum capacity, then
            // error as no Key worker currently in pool can accept any further allocations.
            checkMaxCapacity();

            priorityKeyworker = keyworkerPool.first();
        }

        log.debug("Key worker with staffId [{}] selected for allocation of offender with offenderNo [{}].",
                priorityKeyworker.getStaffId(), offenderNo);

        return priorityKeyworker;
    }

    /**
     * Increments allocation count of provided Key worker and Refreshes pool (to recalculate its position in pool).
     * If provided Key worker does not already exist in the pool, an
     * {@code IllegalStateException} is thrown.
     *
     * @param keyworker Key worker to process.
     * @throws IllegalStateException if Key worker is not already present in the pool.
     */
    public void incrementAndRefreshKeyworker(KeyworkerDto keyworker) {
        Validate.notNull(keyworker, "Key worker to refresh must be specified.");

        // Remove Key worker from pool (throwing exception if Key worker not in pool)
        if (!removeKeyworker(keyworker.getStaffId()).isPresent()) {
            log.error("Key worker with staffId [{}] not in pool.", keyworker.getStaffId());

            throw new IllegalStateException("Key worker to refresh is not in Key worker pool.");
        }
        keyworker.setNumberAllocated(keyworker.getNumberAllocated() + 1);

        // Add Key worker back to pool
        reinstateKeyworker(keyworker, null);
    }

    private Optional<KeyworkerDto> findPreviousAllocation(String offenderNo, List<OffenderKeyworker> keyWorkerAllocations) {
        log.debug("Assessing previous allocations for offender with offenderNo [{}].", offenderNo);

        Optional<KeyworkerDto> previousKeyworker;

        // Check if any were with a Key worker in the pool and sort allocations by assigned date
        if (ObjectUtils.isEmpty(keyWorkerAllocations)) {
            previousKeyworker = Optional.empty();
        } else {
            Optional<OffenderKeyworker> latestAllocation = keyWorkerAllocations.stream()
                    .filter(kwa -> kwa.getOffenderNo().equals(offenderNo) && keyworkerStaffIds.contains(kwa.getStaffId()))
                    .max(Comparator.comparing(OffenderKeyworker::getAssignedDateTime));

            if (latestAllocation.isPresent()) {
                // Key worker staff id of latest allocation
                Long keyworkerStaffId = latestAllocation.get().getStaffId();

                previousKeyworker = keyworkerPool.stream().filter(kw -> keyworkerStaffId.equals(kw.getStaffId())).findFirst();
            } else {
                previousKeyworker = Optional.empty();
            }
        }

        return previousKeyworker;
    }

    // Applies sorting algorithm to Key worker list to determine allocation priority of Key workers. Must be called
    // prior to each request for priority Key worker.
    private void prioritiseKeyworkers() {
        checkMaxCapacity();

        // Identify Key worker(s) with least number of allocations - first Key worker in pool will have least allocations
        int fewestAllocs = keyworkerPool.first().getNumberAllocated();

        // If priority Key worker hos no allocations, no further processing required, otherwise identify any other Key
        // workers in pool having same number of allocations.
        if (fewestAllocs > 0) {
            List<KeyworkerDto> fewestAllocKeyworkers =
                    keyworkerPool.stream().filter(kw -> (kw.getNumberAllocated() == fewestAllocs)).collect(Collectors.toList());

            // If only one Key worker with fewest allocations, no further processing required, otherwise retrieve
            // allocations for all Key workers with fewest allocations and update sort.
            if (fewestAllocKeyworkers.size() > 1) {
                // For each Key worker with fewest allocations, remove from pool, update allocations, reinstate to pool.
                fewestAllocKeyworkers.forEach(kw -> {
                    removeKeyworker(kw.getStaffId());

                    Long staffId = kw.getStaffId();
                    List<OffenderKeyworker> allocations = keyworkerService.getAllocationsForKeyworker(staffId);

                    reinstateKeyworker(kw, allocations);
                });
            }
        }

        log.debug("Key worker pool prioritised - priority Key worker has {} allocations.",
                keyworkerPool.first().getNumberAllocated());
    }

    private void checkMaxCapacity() {
        final KeyworkerDto first = keyworkerPool.first();
        if (first.getNumberAllocated() >= calculateEnhancedCapacity(first)) {
            log.error(OUTCOME_ALL_KEY_WORKERS_AT_CAPACITY);

            throw AllocationException.withMessage(OUTCOME_ALL_KEY_WORKERS_AT_CAPACITY);
        }
    }

    private Optional<KeyworkerDto> removeKeyworker(long staffId) {
        log.debug("Removing Key worker with staffId [{}] from pool.", staffId);

        keyworkerStaffIds.remove(staffId);

        Optional<KeyworkerDto> keyworker = keyworkerPool.stream().filter(kw -> kw.getStaffId().equals(staffId)).findFirst();

        keyworker.ifPresent(keyworkerPool::remove);

        keyworkerAllocations.remove(staffId);

        return keyworker;
    }

    private void reinstateKeyworker(KeyworkerDto keyworker, List<OffenderKeyworker> allocations) {
        Long staffId = keyworker.getStaffId();

        log.debug("Reinstating Key worker with staffId [{}], and having [{}] allocations, to pool.",
                staffId, keyworker.getNumberAllocated());

        // Filter out manual allocations (only interested in auto-allocations)
        List<OffenderKeyworker> autoAllocations = (allocations == null) ? null :
                allocations.stream().filter(kwa -> !kwa.getAllocationType().isManual()).collect(Collectors.toList());

        keyworkerAllocations.put(staffId, autoAllocations);
        keyworkerPool.add(keyworker);
        keyworkerStaffIds.add(staffId);

        log.debug("Key worker with staffId [{}] reinstated to pool. New pool size is [{}] and priority Key worker has staffId [{}] and [{}] allocations.",
                staffId, keyworkerPool.size(), keyworkerPool.first().getStaffId(), keyworkerPool.first().getNumberAllocated());
    }
}
