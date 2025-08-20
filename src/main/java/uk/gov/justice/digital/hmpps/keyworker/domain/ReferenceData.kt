package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EntityNotFoundException
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.SecondaryTable
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.TenantId
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus

@Immutable
@Entity
@Table(name = "reference_data_policy")
@SecondaryTable(name = "reference_data")
class ReferenceData(
  @Embedded
  val key: ReferenceDataKey,
  @Column(table = "reference_data")
  val description: String,
  @Column(table = "reference_data")
  val sequenceNumber: Int,
  @TenantId
  val policyCode: String,
  @Id
  val id: Long,
) : ReferenceDataLookup by key

interface ReferenceDataLookup {
  val domain: ReferenceDataDomain
  val code: String
}

@Embeddable
data class ReferenceDataKey(
  @Column(table = "reference_data")
  @Enumerated(EnumType.STRING)
  override val domain: ReferenceDataDomain,
  @Column(table = "reference_data")
  override val code: String,
) : ReferenceDataLookup

enum class ReferenceDataDomain {
  ALLOCATION_REASON,
  DEALLOCATION_REASON,
  STAFF_POSITION,
  STAFF_SCHEDULE_TYPE,
  STAFF_STATUS,
  ;

  companion object {
    fun of(domain: String): ReferenceDataDomain =
      entries.firstOrNull {
        it.name.lowercase().replace("_", "") == domain.lowercase().replace("[_|-]".toRegex(), "")
      } ?: throw IllegalArgumentException("Reference data domain not recognised")
  }
}

infix fun ReferenceDataDomain.of(code: String): ReferenceDataKey = ReferenceDataKey(this, code)

interface ReferenceDataRepository : JpaRepository<ReferenceData, Long> {
  fun findByKeyDomain(domain: ReferenceDataDomain): List<ReferenceData>

  fun findByKey(key: ReferenceDataKey): ReferenceData?

  fun findAllByKeyIn(keys: Set<ReferenceDataKey>): List<ReferenceData>
}

fun ReferenceDataRepository.getKeyworkerStatus(status: StaffStatus): ReferenceData =
  findByKey(ReferenceDataKey(ReferenceDataDomain.STAFF_STATUS, status.name))
    ?: throw EntityNotFoundException("Keyworker status not found")

fun ReferenceDataRepository.getReferenceData(key: ReferenceDataKey) =
  findByKey(key)
    ?: throw EntityNotFoundException("Reference data not found")

fun ReferenceData.asKeyworkerStatus(): StaffStatus = StaffStatus.valueOf(code)

fun ReferenceData?.toKeyworkerStatusCodedDescription(): CodedDescription =
  this?.let {
    CodedDescription(code, description)
  } ?: CodedDescription("ACTIVE", "Active")

fun ReferenceData.asCodedDescription(): CodedDescription = CodedDescription(code, description)
