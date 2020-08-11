package uk.gov.justice.digital.hmpps.keyworker.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDetailsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper to convert model objects to DTOs and vice versa.
 */
public final class ConversionHelper {
    private ConversionHelper() {
    }

    public static Set<OffenderKeyworker> convertOffenderKeyworkerDto2Model(final List<OffenderKeyworkerDto> dtos) {
        Validate.notNull(dtos);

        return dtos.stream().map(ConversionHelper::convertOffenderKeyworkerDto2Model).collect(Collectors.toSet());
    }

    public static OffenderKeyworker convertOffenderKeyworkerDto2Model(final OffenderKeyworkerDto dto) {
        Validate.notNull(dto);

        return OffenderKeyworker.builder()
                .offenderNo(dto.getOffenderNo())
                .staffId(dto.getStaffId())
                .prisonId(dto.getAgencyId())
                .active(StringUtils.equals("Y", dto.getActive()))
                .assignedDateTime(dto.getAssigned())
                .expiryDateTime(dto.getExpired())
                .userId(dto.getUserId())
                .build();
    }

    public static List<OffenderKeyworkerDto> convertOffenderKeyworkerModel2Dto(final List<OffenderKeyworker> models) {
        Validate.notNull(models);
        return models.stream().map(ConversionHelper::convertOffenderKeyworkerModel2Dto).collect(Collectors.toList());
    }

    private static OffenderKeyworkerDto convertOffenderKeyworkerModel2Dto(final OffenderKeyworker model) {
        Validate.notNull(model);

        return OffenderKeyworkerDto.builder()
                .offenderKeyworkerId(model.getOffenderKeyworkerId())
                .offenderNo(model.getOffenderNo())
                .staffId(model.getStaffId())
                .agencyId(model.getPrisonId())
                .active(model.isActive() ? "Y" : "N")
                .assigned(model.getAssignedDateTime())
                .expired(model.getExpiryDateTime())
                .userId(model.getUserId())
                .build();
    }

    public static OffenderKeyworker getOffenderKeyworker(final KeyworkerAllocationDto newAllocation, final String userId) {
        return OffenderKeyworker.builder()
                .offenderNo(newAllocation.getOffenderNo())
                .staffId(newAllocation.getStaffId())
                .prisonId(newAllocation.getPrisonId())
                .allocationReason(newAllocation.getAllocationReason())
                .active(true)
                .assignedDateTime(LocalDateTime.now())
                .allocationType(newAllocation.getAllocationType())
                .userId(userId)
                .build();
    }

    public static KeyworkerAllocationDetailsDto convertOffenderKeyworkerModel2KeyworkerAllocationDetailsDto(final OffenderKeyworker model) {
        Validate.notNull(model);

        return KeyworkerAllocationDetailsDto.builder()
                .offenderNo(model.getOffenderNo())
                .staffId(model.getStaffId())
                .agencyId(model.getPrisonId()) //TODO: remove
                .prisonId(model.getPrisonId())
                .assigned(model.getAssignedDateTime())
                .allocationType(model.getAllocationType())
                .build();
    }

    public static KeyworkerDto getKeyworkerDto(final StaffLocationRoleDto dto) {
        if (dto != null) {
            return KeyworkerDto.builder()
                    .firstName(dto.getFirstName())
                    .lastName(dto.getLastName())
                    .email(dto.getEmail())
                    .staffId(dto.getStaffId())
                    .thumbnailId(dto.getThumbnailId())
                    .scheduleType(dto.getScheduleTypeDescription())
                    .agencyDescription(dto.getAgencyDescription())
                    .agencyId(dto.getAgencyId())
                    .capacity(dto.getHoursPerWeek() != null ? dto.getHoursPerWeek().intValue() : null)
                    .build();
        }
        return null;
    }

    public static OffenderKeyworker getOffenderKeyworker(final KeyworkerAllocationDetailsDto model) {
        Validate.notNull(model);

        return OffenderKeyworker.builder()
                .offenderNo(model.getOffenderNo())
                .staffId(model.getStaffId())
                .prisonId(model.getAgencyId())
                .active(true)
                .assignedDateTime(model.getAssigned())
                .allocationType(model.getAllocationType())
                .build();
    }
}
