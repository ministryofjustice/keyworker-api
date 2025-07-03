package uk.gov.justice.digital.hmpps.keyworker.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import org.openapitools.jackson.nullable.JsonNullable
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import java.math.BigDecimal
import java.time.LocalDate

@Schema(
  description = "Request to patch the configuration for a staff.",
)
data class StaffDetailsRequest(
  @param:Schema(nullable = false, type = "string", requiredMode = Schema.RequiredMode.NOT_REQUIRED, implementation = StaffStatus::class)
  val status: JsonNullable<StaffStatus> = JsonNullable.undefined(),
  @param:Schema(nullable = false, type = "integer", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  val capacity: JsonNullable<Int> = JsonNullable.undefined(),
  @param:Schema(nullable = false, type = "boolean", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  val allowAutoAllocation: JsonNullable<Boolean> = JsonNullable.undefined(),
  @param:Schema(nullable = true, type = "string", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1980-01-01")
  val reactivateOn: JsonNullable<LocalDate?> = JsonNullable.undefined(),
  @param:Schema(
    nullable = true,
    type = "object",
    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    implementation = StaffRoleRequest::class,
  )
  val staffRole: JsonNullable<StaffRoleRequest?> = JsonNullable.undefined(),
  @param:Schema(nullable = false, type = "boolean", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  val deactivateActiveAllocations: JsonNullable<Boolean> = JsonNullable.undefined(),
)

data class StaffRoleRequest(
  @param:Schema(nullable = false, type = "string", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  val position: JsonNullable<String> = JsonNullable.undefined(),
  @param:Schema(nullable = false, type = "string", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  val scheduleType: JsonNullable<String> = JsonNullable.undefined(),
  @param:Schema(nullable = false, type = "integer", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  val hoursPerWeek: JsonNullable<BigDecimal> = JsonNullable.undefined(),
  @param:Schema(nullable = false, type = "string", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1980-01-01")
  val fromDate: JsonNullable<LocalDate> = JsonNullable.undefined(),
) {
  @JsonIgnore
  fun isValidToCreate(): Boolean = position.isPresent && scheduleType.isPresent && hoursPerWeek.isPresent
}
