package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import org.hibernate.envers.Audited
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
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
  @Enumerated(EnumType.STRING)
  @Column(name = "allocation_order")
  var allocationOrder: AllocationOrder,
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
      maximumCapacity = request.capacity
      allowAutoAllocation = request.allowAutoAllocation
      frequencyInWeeks = request.frequencyInWeeks
      request.hasPrisonersWithHighComplexityNeeds?.also { hasPrisonersWithHighComplexityNeeds = it }
      allocationOrder = request.allocationOrder
    }

  companion object {
    fun default(
      code: String,
      policy: AllocationPolicy = AllocationContext.get().policy,
    ) = PrisonConfiguration(
      code,
      enabled = true,
      allowAutoAllocation = true,
      capacity = 6,
      maximumCapacity = 9,
      frequencyInWeeks = 1,
      hasPrisonersWithHighComplexityNeeds = false,
      allocationOrder = AllocationOrder.BY_ALLOCATIONS,
      policy.name,
    )
  }
}

enum class AllocationOrder {
  BY_ALLOCATIONS,
  BY_NAME,
}

interface PrisonConfigurationRepository : JpaRepository<PrisonConfiguration, UUID> {
  fun findByCode(code: String): PrisonConfiguration?

  @Query(
    """
      select * from prison_configuration where is_enabled = true and policy_code = :policyCode
    """,
    nativeQuery = true,
  )
  fun findEnabledPrisonsForPolicyCode(policyCode: String): List<PrisonConfiguration>

  @Query(
    """
      select * from prison_configuration where prison_code = :prisonCode and policy_code in :policies
    """,
    nativeQuery = true,
  )
  fun findConfigurationsForPolicies(
    prisonCode: String,
    policies: Set<String>,
  ): List<PrisonConfiguration>

  @Query(
    """
      select policy_code from prison_configuration where prison_code = :prisonCode and is_enabled = true
    """,
    nativeQuery = true,
  )
  fun findEnabledPrisonPolicies(prisonCode: String): Set<String>
}
