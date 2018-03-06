package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagingAndSortingDto {
    public static final String HEADER_PAGE_OFFSET = "Page-Offset";
    public static final String HEADER_PAGE_LIMIT = "Page-Limit";
    public static final String HEADER_SORT_FIELDS = "Sort-Fields";
    public static final String HEADER_SORT_ORDER = "Sort-Order";

    private Long pageOffset;
    private Long pageLimit;
    private String sortFields;
    private SortOrder sortOrder;
}
