package uk.gov.justice.digital.hmpps.keyworker.dto;

public enum KeyworkerStatusBehaviour {
    KEEP_ALLOCATIONS(false, false),
    KEEP_ALLOCATIONS_NO_AUTO(false,true),
    REMOVE_ALLOCATION_NO_AUTO(true,true);


    private final boolean removeAllocations;
    private final boolean removeFromAutoAllocation;

    KeyworkerStatusBehaviour(boolean removeAllocations, boolean removeFromAutoAllocation) {
        this.removeAllocations = removeAllocations;
        this.removeFromAutoAllocation = removeFromAutoAllocation;
    }

    public boolean isRemoveAllocations() {
        return removeAllocations;
    }

    public boolean isRemoveFromAutoAllocation() {
        return removeFromAutoAllocation;
    }
}