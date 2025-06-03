package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffWithAllocationCount
import uk.gov.justice.digital.hmpps.keyworker.domain.asKeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerSearchRequest.Status.ALL
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerSummary
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdRequest.Companion.lastMonthSessions
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdResponse
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus.ACTIVE
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus.valueOf
import java.util.Optional

@Service
class KeyworkerSearch(
  private val nomisService: NomisService,
  private val caseNoteApi: CaseNotesApiClient,
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val keyworkerConfigRepository: StaffConfigRepository,
) {
  fun findKeyworkers(
    prisonCode: String,
    request: KeyworkerSearchRequest,
  ): KeyworkerSearchResponse {
    val keyworkerStaff =
      checkNotNull(
        nomisService
          .getActiveStaffKeyWorkersForPrison(
            prisonCode,
            Optional.ofNullable(request.query),
            PagingAndSortingDto.activeStaffKeyWorkersPagingAndSorting(),
            false,
          ).body
          ?.toList(),
      )
    val keyworkerStaffIds = keyworkerStaff.map { it.staffId }.toSet()
    val sessions = caseNoteApi.getUsageByStaffIds(lastMonthSessions(keyworkerStaffIds.map(Long::toString).toSet()))
    val prisonConfig = prisonConfigRepository.findByCode(prisonCode) ?: PrisonConfiguration.default(prisonCode)
    val keyworkerDetail =
      if (keyworkerStaffIds.isEmpty()) {
        emptyMap()
      } else {
        keyworkerConfigRepository.findAllWithAllocationCount(prisonCode, keyworkerStaffIds).associateBy { it.staffId }
      }

    return KeyworkerSearchResponse(
      keyworkerStaff
        .map {
          it.searchResponse(
            { staffId -> keyworkerDetail[staffId] },
            prisonConfig,
            { staffId -> sessions.content[staffId.toString()]?.firstOrNull() },
          )
        }.filter {
          request.status == ALL || valueOf(request.status.name).statusCode == it.status.code
        },
    )
  }

  fun StaffLocationRoleDto.searchResponse(
    keyworkerConfig: (Long) -> StaffWithAllocationCount?,
    prisonConfig: PrisonConfiguration,
    keyworkerSessions: (Long) -> UsageByAuthorIdResponse?,
  ): KeyworkerSummary {
    val kwa = keyworkerConfig(staffId)
    val status = kwa?.staffConfig?.status
    return KeyworkerSummary(
      staffId,
      firstName,
      lastName,
      (status?.asKeyworkerStatus() ?: ACTIVE).codedDescription(),
      kwa?.staffConfig?.capacity ?: prisonConfig.capacity,
      kwa?.allocationCount ?: 0,
      kwa?.staffConfig?.allowAutoAllocation ?: prisonConfig.allowAutoAllocation,
      keyworkerSessions(staffId)?.count ?: 0,
    )
  }
}
