package uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles

import com.fasterxml.jackson.annotation.JsonAlias
import java.math.BigDecimal
import java.time.LocalDate

interface JobClassification {
  val position: String
  val scheduleType: String
  val hoursPerWeek: BigDecimal
  val fromDate: LocalDate
  val toDate: LocalDate?
}

data class StaffJobClassificationRequest(
  override val position: String,
  override val scheduleType: String,
  override val hoursPerWeek: BigDecimal,
  override val fromDate: LocalDate,
  override val toDate: LocalDate?,
) : JobClassification

data class StaffJobClassification(
  @JsonAlias("agencyId")
  val prisonCode: String,
  val staffId: Long,
  override val position: String,
  override val scheduleType: String,
  override val hoursPerWeek: BigDecimal,
  override val fromDate: LocalDate,
  override val toDate: LocalDate?,
) : JobClassification {
  constructor(prisonCode: String, staffId: Long, classification: JobClassification) : this(
    prisonCode,
    staffId,
    classification.position,
    classification.scheduleType,
    classification.hoursPerWeek,
    classification.fromDate,
    classification.toDate,
  )
}
