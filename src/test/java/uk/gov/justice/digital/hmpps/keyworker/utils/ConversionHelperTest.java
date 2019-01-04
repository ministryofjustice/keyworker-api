package uk.gov.justice.digital.hmpps.keyworker.utils;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for {@link ConversionHelper}.
 */
public class ConversionHelperTest {

    @Test
    public void convertOffenderKeyworkerDto2ModelItem1() {
        OffenderKeyworkerDto testDto = getActiveOffenderKeyworkerDto();

        OffenderKeyworker okw = ConversionHelper.convertOffenderKeyworkerDto2Model(testDto);

        verifyConversion(testDto, okw);
    }

    @Test
    public void convertOffenderKeyworkerDto2ModelItem2() {
        OffenderKeyworkerDto testDto = getExpiredOffenderKeyworkerDto();

        OffenderKeyworker okw = ConversionHelper.convertOffenderKeyworkerDto2Model(testDto);

        verifyConversion(testDto, okw);
    }

    @Test
    public void convertOffenderKeyworkerDto2ModelList() {
        OffenderKeyworkerDto testActiveDto = getActiveOffenderKeyworkerDto();
        OffenderKeyworkerDto testExpiredDto = getExpiredOffenderKeyworkerDto();

        List<OffenderKeyworkerDto> testDtos = List.of(testActiveDto, testExpiredDto);

        Set<OffenderKeyworker> okws = ConversionHelper.convertOffenderKeyworkerDto2Model(testDtos);

        assertThat(okws.size()).isEqualTo(testDtos.size());

        okws.forEach(okw -> {
            if (okw.isActive()) {
                verifyConversion(testActiveDto, okw);
            } else {
                verifyConversion(testExpiredDto, okw);
            }
        });
    }

    public static OffenderKeyworkerDto getActiveOffenderKeyworkerDto() {
        return OffenderKeyworkerDto.builder()
                .offenderNo("A1234AA")
                .staffId(1L)
                .active("Y")
                .agencyId("XXX")
                .assigned(LocalDateTime.of(2016, 9, 9, 9, 9, 9))
                .userId("MANAGER")
                .build();
    }

    public static OffenderKeyworkerDto getExpiredOffenderKeyworkerDto() {
        return OffenderKeyworkerDto.builder()
                .offenderNo("A1234AB")
                .staffId(2L)
                .active("N")
                .agencyId("XXX")
                .assigned(LocalDateTime.of(2015, 9, 9, 9, 9, 9))
                .userId("MANAGER")
                .expired(LocalDateTime.of(2016, 9, 9, 9, 9, 9))
                .build();
    }

    public static void verifyConversion(OffenderKeyworkerDto dto, OffenderKeyworker okw) {
        assertThat(okw.getOffenderNo()).isEqualTo(dto.getOffenderNo());
        assertThat(okw.getStaffId()).isEqualTo(dto.getStaffId());
        assertThat(okw.isActive()).isEqualTo(StringUtils.equals("Y", dto.getActive()));
        assertThat(okw.getPrisonId()).isEqualTo(dto.getAgencyId());
        assertThat(okw.getAssignedDateTime()).isEqualTo(dto.getAssigned());
        assertThat(okw.getUserId()).isEqualTo(dto.getUserId());
        assertThat(okw.getExpiryDateTime()).isEqualTo(dto.getExpired());
    }
}
