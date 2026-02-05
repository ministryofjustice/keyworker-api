package uk.gov.justice.digital.hmpps.keyworker.model.staff

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "Request to patch the configuration for a staff.")
data class StaffDetailsRequest(
  @param:Schema(nullable = false, type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
  val status: String,
  @param:Schema(nullable = false, type = "integer", requiredMode = Schema.RequiredMode.REQUIRED)
  val capacity: Int,
  @param:Schema(
    nullable = true,
    type = "string",
    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    example = "1980-01-01",
  )
  val reactivateOn: LocalDate?,
  @param:Schema(
    nullable = false,
    type = "object",
    requiredMode = Schema.RequiredMode.REQUIRED,
    implementation = StaffRoleRequest::class,
  )
  val staffRole: StaffRoleRequest,
  @param:Schema(nullable = false, type = "boolean", requiredMode = Schema.RequiredMode.REQUIRED)
  val deactivateActiveAllocations: Boolean,
)

data class StaffRoleRequest(
  @param:Schema(nullable = false, type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
  val position: String,
  @param:Schema(nullable = false, type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
  val scheduleType: String,
  @param:Schema(nullable = false, type = "number", requiredMode = Schema.RequiredMode.REQUIRED)
  val hoursPerWeek: BigDecimal,
  @param:Schema(
    nullable = false,
    type = "string",
    requiredMode = Schema.RequiredMode.REQUIRED,
    example = "1980-01-01",
  )
  val fromDate: LocalDate,
  @param:Schema(
    nullable = true,
    type = "string",
    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    example = "1980-01-01",
  )
  val toDate: LocalDate?,
)
