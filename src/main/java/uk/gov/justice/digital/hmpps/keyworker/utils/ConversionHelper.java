package uk.gov.justice.digital.hmpps.keyworker.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.model.CreateUpdate;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper to convert model objects to DTOs and vice versa.
 */
public final class ConversionHelper {
    private ConversionHelper() {
    }

    public static List<OffenderKeyworker> convertOffenderKeyworkerDto2Model(List<OffenderKeyworkerDto> dtos) {
        Validate.notNull(dtos);

        return dtos.stream().map(ConversionHelper::convertOffenderKeyworkerDto2Model).collect(Collectors.toList());
    }

    public static OffenderKeyworker convertOffenderKeyworkerDto2Model(OffenderKeyworkerDto dto) {
        Validate.notNull(dto);

        CreateUpdate createUpdate = CreateUpdate.builder()
                .creationDateTime(dto.getCreated())
                .createUserId(dto.getCreatedBy())
                .modifyDateTime(dto.getModified())
                .modifyUserId(dto.getModifiedBy())
                .build();

        return OffenderKeyworker.builder()
                .offenderNo(dto.getOffenderNo())
                .staffId(dto.getStaffId())
                .agencyId(dto.getAgencyId())
                .active(StringUtils.equals("Y", dto.getActive()))
                .assignedDateTime(dto.getAssigned())
                .expiryDateTime(dto.getExpired())
                .userId(dto.getUserId())
                .createUpdate(createUpdate)
                .build();
    }

    public static OffenderKeyworker getOffenderKeyworker(KeyworkerAllocationDto newAllocation, String userId) {
        return OffenderKeyworker.builder()
                .offenderNo(newAllocation.getOffenderNo())
                .staffId(newAllocation.getStaffId())
                .agencyId(newAllocation.getAgencyId())
                .allocationReason(newAllocation.getAllocationReason())
                .active(true)
                .assignedDateTime(LocalDateTime.now())
                .allocationType(newAllocation.getAllocationType())
                .userId(userId)
                .build();
    }
}
