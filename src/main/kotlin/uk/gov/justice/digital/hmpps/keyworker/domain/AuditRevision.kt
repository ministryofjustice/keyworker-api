package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.envers.EntityTrackingRevisionListener
import org.hibernate.envers.RevisionEntity
import org.hibernate.envers.RevisionNumber
import org.hibernate.envers.RevisionTimestamp
import org.hibernate.envers.RevisionType
import org.hibernate.type.SqlTypes
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import java.time.LocalDateTime

@Entity
@Table
@RevisionEntity(AuditRevisionEntityListener::class)
class AuditRevision {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @RevisionNumber
  var id: Long = 0

  // must be called timestamp for EnversRevisionRepositoryImpl
  @RevisionTimestamp
  var timestamp: LocalDateTime = LocalDateTime.now()

  var username: String? = null
  var caseloadId: String? = null

  @JdbcTypeCode(SqlTypes.ARRAY)
  var affectedEntities: MutableSet<String> = sortedSetOf(String.CASE_INSENSITIVE_ORDER)
}

class AuditRevisionEntityListener : EntityTrackingRevisionListener {
  override fun newRevision(revision: Any?) {
    (revision as AuditRevision).apply {
      val context = AllocationContext.get()
      username = context.username
      caseloadId = context.activeCaseloadId
    }
  }

  override fun entityChanged(
    entityClass: Class<*>,
    entityName: String,
    entityId: Any,
    revisionType: RevisionType,
    revision: Any,
  ) {
    (revision as AuditRevision).apply {
      affectedEntities.add(entityClass.simpleName)
    }
  }
}
