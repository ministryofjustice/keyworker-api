package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)

public class PagingAndSortingDto {
    private Integer pageOffset;
    private Integer pageLimit;
    private String sortFields;
    private SortOrder sortOrder;
}
