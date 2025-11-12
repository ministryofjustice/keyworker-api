package uk.gov.justice.digital.hmpps.keyworker.migration

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.set
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.STAFF_POSITION
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.STAFF_SCHEDULE_TYPE
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import java.math.BigDecimal

@Service
class PolicySwitch(
  private val entityManager: EntityManager,
  private val transactionTemplate: TransactionTemplate,
  private val referenceDataRepository: ReferenceDataRepository,
  private val allocationRepository: AllocationRepository,
  private val staffRoleRepository: StaffRoleRepository,
) {
  fun switch(prisonCode: String) {
    AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER).set()
    transactionTemplate.executeWithoutResult {
      switchAllocationsAndAudit(prisonCode)
      switchStats(prisonCode)
      createStaffRoles(prisonCode)
    }
  }

  private fun switchAllocationsAndAudit(prisonCode: String) {
    entityManager
      .createNativeQuery(
        """
        update allocation set policy_code = 'PERSONAL_OFFICER'
        where prison_code = :prisonCode and policy_code = 'KEY_WORKER'
        """.trimIndent(),
      ).setParameter("prisonCode", prisonCode)
      .executeUpdate()
    entityManager
      .createNativeQuery(
        """
        update allocation_audit set policy_code = 'PERSONAL_OFFICER'
        where prison_code = :prisonCode and policy_code = 'KEY_WORKER'
        """.trimIndent(),
      ).setParameter("prisonCode", prisonCode)
      .executeUpdate()
  }

  private fun switchStats(prisonCode: String) {
    entityManager
      .createNativeQuery(
        """
        update prison_statistic set policy_code = 'PERSONAL_OFFICER'
        where prison_code = :prisonCode and policy_code = 'KEY_WORKER'
        """.trimIndent(),
      ).setParameter("prisonCode", prisonCode)
      .executeUpdate()
    entityManager
      .createNativeQuery(
        """
        update prisoner_statistic set policy_code = 'PERSONAL_OFFICER'
        from prison_statistic
        where prison_statistic.prison_code = :prisonCode and prisoner_statistic.policy_code = 'KEY_WORKER'
        and prisoner_statistic.prison_statistic_id = prison_statistic.id
        """.trimIndent(),
      ).setParameter("prisonCode", prisonCode)
      .executeUpdate()
  }

  private fun createStaffRoles(prisonCode: String) {
    val rd =
      referenceDataRepository
        .findAllByKeyIn(setOf(STAFF_POSITION of "PRO", STAFF_SCHEDULE_TYPE of "FT"))
        .associateBy { (it.domain to it.code) }
    val activeAllocations =
      allocationRepository
        .findByPrisonCodeAndIsActiveTrue(prisonCode)
        .groupBy { it.staffId }
    staffRoleRepository
      .saveAll(
        activeAllocations.map {
          StaffRole(
            requireNotNull(rd[STAFF_POSITION to "PRO"]),
            requireNotNull(rd[STAFF_SCHEDULE_TYPE to "FT"]),
            BigDecimal(35),
            it.value.minOf { a -> a.allocatedAt }.toLocalDate(),
            null,
            prisonCode,
            it.key,
          )
        },
      )
  }
}
