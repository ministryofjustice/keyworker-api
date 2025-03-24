package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EntityNotFoundException
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus

@Immutable
@Entity
@Table
class ReferenceData(
  @Embedded
  val key: ReferenceDataKey,
  val description: String,
  val sequenceNumber: Int,
  @Id
  @Column(name = "id")
  val id: Long,
) : ReferenceDataLookup by key

interface ReferenceDataLookup {
  val domain: ReferenceDataDomain
  val code: String
}

@Embeddable
data class ReferenceDataKey(
  @Enumerated(EnumType.STRING)
  override val domain: ReferenceDataDomain,
  override val code: String,
) : ReferenceDataLookup

enum class ReferenceDataDomain {
  KEYWORKER_STATUS,
  ALLOCATION_REASON,
  DEALLOCATION_REASON,
  ;

  companion object {
    fun of(domain: String): ReferenceDataDomain =
      entries.firstOrNull {
        it.name.lowercase().replace("_", "") == domain.lowercase().replace("[_|-]".toRegex(), "")
      } ?: throw IllegalArgumentException("Reference data domain not recognised")
  }
}

interface ReferenceDataRepository : JpaRepository<ReferenceData, Long> {
  fun findByKeyDomain(domain: ReferenceDataDomain): List<ReferenceData>

  fun findByKey(key: ReferenceDataKey): ReferenceData?
}

fun ReferenceDataRepository.getKeyworkerStatus(status: KeyworkerStatus): ReferenceData =
  findByKey(ReferenceDataKey(ReferenceDataDomain.KEYWORKER_STATUS, status.name))
    ?: throw EntityNotFoundException("Keyworker status not found")

fun ReferenceData.asKeyworkerStatus(): KeyworkerStatus = KeyworkerStatus.valueOf(code)
