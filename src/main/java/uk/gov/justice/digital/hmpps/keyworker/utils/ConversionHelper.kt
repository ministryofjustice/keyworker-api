package uk.gov.justice.digital.hmpps.keyworker.utils

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.Validate
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDetailsDto
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDto
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker
import java.time.LocalDateTime
import java.util.stream.Collectors

/**
 * Helper to convert model objects to DTOs and vice versa.
 */
object ConversionHelper {
  fun convertOffenderKeyworkerDto2Model(dtos: List<OffenderKeyworkerDto>): Set<OffenderKeyworker> {
    Validate.notNull(dtos)
    return dtos.stream().map { convertOffenderKeyworkerDto2Model(it) }.collect(Collectors.toSet())
  }

  fun convertOffenderKeyworkerDto2Model(dto: OffenderKeyworkerDto): OffenderKeyworker {
    Validate.notNull(dto)
    return OffenderKeyworker.builder()
      .offenderNo(dto.offenderNo)
      .staffId(dto.staffId)
      .prisonId(dto.agencyId)
      .active(StringUtils.equals("Y", dto.active))
      .assignedDateTime(dto.assigned)
      .expiryDateTime(dto.expired)
      .userId(dto.userId)
      .build()
  }

  fun convertOffenderKeyworkerModel2Dto(models: List<OffenderKeyworker>): List<OffenderKeyworkerDto> {
    Validate.notNull(models)
    return models.stream().map { convertOffenderKeyworkerModel2Dto(it) }.collect(Collectors.toList())
  }

  private fun convertOffenderKeyworkerModel2Dto(model: OffenderKeyworker): OffenderKeyworkerDto {
    Validate.notNull(model)
    return OffenderKeyworkerDto.builder()
      .offenderKeyworkerId(model.offenderKeyworkerId)
      .offenderNo(model.offenderNo)
      .staffId(model.staffId)
      .agencyId(model.prisonId)
      .active(if (model.isActive) "Y" else "N")
      .assigned(model.assignedDateTime)
      .expired(model.expiryDateTime)
      .userId(model.userId)
      .build()
  }

  fun getOffenderKeyworker(newAllocation: KeyworkerAllocationDto, userId: String?): OffenderKeyworker =
    OffenderKeyworker.builder()
      .offenderNo(newAllocation.offenderNo)
      .staffId(newAllocation.staffId)
      .prisonId(newAllocation.prisonId)
      .allocationReason(newAllocation.allocationReason)
      .active(true)
      .assignedDateTime(LocalDateTime.now())
      .allocationType(newAllocation.allocationType)
      .userId(userId)
      .build()

  fun convertOffenderKeyworkerModel2KeyworkerAllocationDetailsDto(model: OffenderKeyworker): KeyworkerAllocationDetailsDto {
    Validate.notNull(model)
    return KeyworkerAllocationDetailsDto.builder()
      .offenderNo(model.offenderNo)
      .staffId(model.staffId)
      .agencyId(model.prisonId) // TODO: remove
      .prisonId(model.prisonId)
      .assigned(model.assignedDateTime)
      .allocationType(model.allocationType)
      .build()
  }

  fun getKeyworkerDto(dto: StaffLocationRoleDto?): KeyworkerDto? = if (dto != null) {
    KeyworkerDto.builder()
      .firstName(dto.firstName)
      .lastName(dto.lastName)
      .email(dto.email)
      .staffId(dto.staffId)
      .thumbnailId(dto.thumbnailId)
      .scheduleType(dto.scheduleTypeDescription)
      .agencyDescription(dto.agencyDescription)
      .agencyId(dto.agencyId)
      .capacity(if (dto.hoursPerWeek != null) dto.hoursPerWeek.toInt() else null)
      .build()
  } else {
    null
  }

  fun getOffenderKeyworker(model: KeyworkerAllocationDetailsDto): OffenderKeyworker {
    Validate.notNull(model)
    return OffenderKeyworker.builder()
      .offenderNo(model.offenderNo)
      .staffId(model.staffId)
      .prisonId(model.agencyId)
      .active(true)
      .assignedDateTime(model.assigned)
      .allocationType(model.allocationType)
      .build()
  }
}
