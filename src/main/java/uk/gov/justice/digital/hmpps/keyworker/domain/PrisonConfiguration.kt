package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import org.hibernate.envers.Audited
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.utils.IdGenerator
import java.util.UUID

@Audited(withModifiedFlag = true)
@Entity
@Table(name = "prison_configuration")
class PrisonConfiguration(
  @Audited(withModifiedFlag = false)
  @Column(name = "prison_code")
  val code: String,
  @Audited(withModifiedFlag = true, modifiedColumnName = "is_enabled_modified")
  @Column(name = "is_enabled")
  var enabled: Boolean,
  @Column(name = "allow_auto_allocation")
  var allowAutoAllocation: Boolean,
  @Column(name = "capacity")
  var capacity: Int,
  @Column(name = "maximum_capacity")
  var maximumCapacity: Int,
  @Column(name = "frequency_in_weeks")
  var frequencyInWeeks: Int,
  @Column(name = "has_prisoners_with_high_complexity_needs")
  var hasPrisonersWithHighComplexityNeeds: Boolean,
  @TenantId
  @Audited(withModifiedFlag = false)
  @Column(name = "policy_code", updatable = false)
  val policy: String,
  @Id
  @Audited(withModifiedFlag = false)
  val id: UUID = IdGenerator.newUuid(),
) {
  fun update(request: PrisonConfigRequest) =
    apply {
      enabled = request.isEnabled
      capacity = request.capacity
      maximumCapacity = request.maximumCapacity
      allowAutoAllocation = request.allowAutoAllocation
      frequencyInWeeks = request.frequencyInWeeks
      request.hasPrisonersWithHighComplexityNeeds?.also { hasPrisonersWithHighComplexityNeeds = it }
    }

  companion object {
    fun default(code: String) =
      PrisonConfiguration(
        code,
        enabled = true,
        allowAutoAllocation = true,
        capacity = 6,
        maximumCapacity = 9,
        frequencyInWeeks = 1,
        hasPrisonersWithHighComplexityNeeds = false,
        AllocationContext.get().policy.name,
      )
  }
}

interface PrisonConfigurationRepository : JpaRepository<PrisonConfiguration, String> {
  fun findByCode(code: String): PrisonConfiguration?

  fun findAllByEnabledIsTrue(): List<PrisonConfiguration>
}
