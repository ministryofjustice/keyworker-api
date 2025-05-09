package uk.gov.justice.digital.hmpps.keyworker.dto

import com.fasterxml.jackson.annotation.JsonIgnore

data class PersonStaffAllocations(
  val allocations: List<PersonStaffAllocation>,
  val deallocations: List<PersonStaffDeallocation>,
) {
  @JsonIgnore
  val personIdentifiersToAllocate: Set<String>

  @JsonIgnore
  val staffIdsToAllocate: Set<Long>

  @JsonIgnore
  val personIdentifiersToDeallocate: Set<String>

  @JsonIgnore
  val staffIdsToDeallocate: Set<Long>

  init {
    val toAllocate = allocations.peopleAndStaff()
    personIdentifiersToAllocate = toAllocate.keys
    staffIdsToAllocate = toAllocate.values.toSet()

    val toDeallocate = deallocations.peopleAndStaff()
    personIdentifiersToDeallocate = toDeallocate.keys
    staffIdsToDeallocate = toDeallocate.values.toSet()
  }

  @JsonIgnore
  fun isEmpty() = allocations.isEmpty() && deallocations.isEmpty()
}

fun List<PersonStaff>.peopleAndStaff() = associate { it.personIdentifier to it.staffId }

interface PersonStaff {
  val personIdentifier: String
  val staffId: Long
}

data class PersonStaffAllocation(
  override val personIdentifier: String,
  override val staffId: Long,
  val allocationReason: String,
) : PersonStaff

data class PersonStaffDeallocation(
  override val personIdentifier: String,
  override val staffId: Long,
  val deallocationReason: String,
) : PersonStaff
