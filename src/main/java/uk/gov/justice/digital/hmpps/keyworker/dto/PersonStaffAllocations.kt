package uk.gov.justice.digital.hmpps.keyworker.dto

data class PersonStaffAllocations(
  val allocations: List<PersonStaffAllocation>,
  val deallocations: List<PersonStaffDeallocation>,
) {
  val personIdentifiersToAllocate: Set<String>
  val staffIdsToAllocate: Set<Long>

  val personIdentifiersToDeallocate: Set<String>
  val staffIdsToDeallocate: Set<Long>

  init {
    val toAllocate = allocations.peopleAndStaff().toMap()
    personIdentifiersToAllocate = toAllocate.keys
    staffIdsToAllocate = toAllocate.values.toSet()

    val toDeallocate = deallocations.peopleAndStaff().toMap()
    personIdentifiersToDeallocate = toDeallocate.keys
    staffIdsToDeallocate = toDeallocate.values.toSet()
  }

  fun isEmpty() = allocations.isEmpty() && deallocations.isEmpty()
}

fun List<PersonStaff>.peopleAndStaff() = map { it.personIdentifier to it.staffId }

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
