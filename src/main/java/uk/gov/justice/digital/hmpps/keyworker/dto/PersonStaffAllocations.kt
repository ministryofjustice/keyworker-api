package uk.gov.justice.digital.hmpps.keyworker.dto

import com.fasterxml.jackson.annotation.JsonIgnore

data class PersonStaffAllocations(
  val allocations: List<PersonStaffAllocation>,
  val deallocations: List<PersonStaffDeallocation>,
) {
  @JsonIgnore
  val personIdentifiersToAllocate: Set<String> = allocations.identifiers()

  @JsonIgnore
  val personIdentifiersToDeallocate: Set<String> = deallocations.identifiers()

  @JsonIgnore
  fun isEmpty() = allocations.isEmpty() && deallocations.isEmpty()
}

fun List<PersonStaff>.identifiers() = map { it.personIdentifier }.toSet()

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
