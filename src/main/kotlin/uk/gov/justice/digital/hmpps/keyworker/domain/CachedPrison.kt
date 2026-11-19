package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "prison")
final class CachedPrison(
  @Id
  val code: String,
  var name: String,
) {
  @Version
  val version: Int? = null
}

interface PrisonRepository : JpaRepository<CachedPrison, String>
