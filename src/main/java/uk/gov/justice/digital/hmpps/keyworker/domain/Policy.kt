package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.springframework.data.jpa.repository.JpaRepository

@Immutable
@Entity
@Table
class Policy(
  @Id
  val code: String,
  val description: String,
)

interface PolicyRepository : JpaRepository<Policy, String>
