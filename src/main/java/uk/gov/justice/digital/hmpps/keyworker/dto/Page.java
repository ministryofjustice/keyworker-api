package uk.gov.justice.digital.hmpps.keyworker.dto;

import org.springframework.http.HttpHeaders;

import java.util.List;

public class Page<T> {
    public static final String HEADER_TOTAL_RECORDS = "Total-Records";
    public static final String HEADER_PAGE_OFFSET = "Page-Offset";
    public static final String HEADER_PAGE_LIMIT = "Page-Limit";

    private final List<T> items;
    private final long totalRecords;
    private final long pageOffset;
    private final long pageLimit;

    public Page(final List<T> items, final long totalRecords, final long pageOffset, final long pageLimit) {
        this.items = items;
        this.totalRecords = totalRecords;
        this.pageOffset = pageOffset;
        this.pageLimit = pageLimit;
    }

    public Page(final List<T> items, final HttpHeaders headers) {
        this.items = items;

        this.totalRecords = Long.parseLong(headers.getFirst(HEADER_TOTAL_RECORDS));
        this.pageOffset = Long.parseLong(headers.getFirst(HEADER_PAGE_OFFSET));
        this.pageLimit = Long.parseLong(headers.getFirst(HEADER_PAGE_LIMIT));
    }

    public List<T> getItems() {
        return items;
    }

    public long getTotalRecords() {
        return totalRecords;
    }

    public long getPageOffset() {
        return pageOffset;
    }

    public long getPageLimit() {
        return pageLimit;
    }

    public HttpHeaders toHeaders() {
        final var headers = new HttpHeaders();

        headers.add(HEADER_TOTAL_RECORDS, String.valueOf(totalRecords));
        headers.add(HEADER_PAGE_OFFSET, String.valueOf(pageOffset));
        headers.add(HEADER_PAGE_LIMIT, String.valueOf(pageLimit));

        return headers;
    }
}
