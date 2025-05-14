package uk.gov.justice.digital.hmpps.keyworker.domain

import io.hypersistence.utils.hibernate.type.array.EnumArrayType
import io.hypersistence.utils.hibernate.type.array.ListArrayType
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.Type
import org.hibernate.envers.EntityTrackingRevisionListener
import org.hibernate.envers.RevisionEntity
import org.hibernate.envers.RevisionNumber
import org.hibernate.envers.RevisionTimestamp
import org.hibernate.envers.RevisionType
import uk.gov.justice.digital.hmpps.keyworker.config.KeyworkerContext
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

  @Type(ListArrayType::class, parameters = [Parameter(name = EnumArrayType.SQL_ARRAY_TYPE, value = "varchar")])
  var affectedEntities: MutableSet<String> = sortedSetOf(String.CASE_INSENSITIVE_ORDER)
}

class AuditRevisionEntityListener : EntityTrackingRevisionListener {
  override fun newRevision(revision: Any?) {
    (revision as AuditRevision).apply {
      val context = KeyworkerContext.get()
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
