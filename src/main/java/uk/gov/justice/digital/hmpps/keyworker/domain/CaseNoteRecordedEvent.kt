package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.TenantId
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

@Immutable
@Entity
@Table(name = "case_note_type_recorded_event_type")
class CaseNoteRecordedEvent(
  @Embedded
  val key: CaseNoteTypeKey,
  @ManyToOne
  @JoinColumn(name = "recorded_event_type_id")
  val type: ReferenceData,
  @TenantId
  val policyCode: String,
  @Id
  val id: Long,
) : CaseNoteType by key

interface CaseNoteType {
  val cnType: String
  val cnSubType: String
}

@Embeddable
data class CaseNoteTypeKey(
  override val cnType: String,
  override val cnSubType: String,
) : CaseNoteType

interface CaseNoteRecordedEventRepository : JpaRepository<CaseNoteRecordedEvent, Long> {
  @EntityGraph("type")
  fun findByKey(key: CaseNoteTypeKey): CaseNoteRecordedEvent?

  @Query(
    "select policy_code from case_note_type_recorded_event_type where cn_type = :type and cn_sub_type = :subType",
    nativeQuery = true,
  )
  fun findPolicyFor(
    type: String,
    subType: String,
  ): String?
}
