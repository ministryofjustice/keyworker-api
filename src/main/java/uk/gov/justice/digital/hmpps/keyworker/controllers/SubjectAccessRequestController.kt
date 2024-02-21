package uk.gov.justice.digital.hmpps.keyworker.controllers

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.services.SubjectAccessRequestService
import java.time.LocalDate

@Schema(description = "Success Response")
data class SuccessResponse(
  val content: JsonNode,
)

@Tag(name = "subject-access-request")
@RestController
class SubjectAccessRequestController(
  private val subjectAccessRequestService: SubjectAccessRequestService,
) {
  @Operation(
    summary = "Returns all data associated to subject in JSON format",
    responses = [
      io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Adjudication returned",
        content = [
          io.swagger.v3.oas.annotations.media.Content(
            mediaType = "application/json",
            schema = io.swagger.v3.oas.annotations.media.Schema(implementation = SuccessResponse::class),
          ),
        ],
      ),
      io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "No content found",
      ),
      io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "209",
        description = "Subject Identifier is not recognised by this service",
      ),
    ],
  )
  @GetMapping("/subject-access-request")
  fun subjectAccessRequest(
    @RequestParam("prn", required = true)
    prn: @NotNull String,
    @RequestParam("crn", required = false)
    crn: String? = null,
    @RequestParam(name = "fromDate", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    fromDate: LocalDate? = null,
    @RequestParam(name = "toDate", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    toDate: LocalDate? = null,
  ): SuccessResponse =
    SuccessResponse(
      content =
        subjectAccessRequestService.getSubjectAccessRequest(
          prn = prn,
          fromDate = fromDate,
          toDate = toDate,
        ),
    )
}
