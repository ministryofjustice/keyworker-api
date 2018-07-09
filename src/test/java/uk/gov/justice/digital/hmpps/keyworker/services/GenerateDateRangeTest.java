package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class GenerateDateRangeTest {

    @Test
    public void testDateGenerationLastWeekForMonday() {
        GenerateDateRange dateRange1 = new GenerateDateRange(1, LocalDate.of(2018, 7, 9));

        assertThat(dateRange1.getFromDate()).isEqualTo(LocalDate.of(2018, 7, 1));
        assertThat(dateRange1.getToDate()).isEqualTo(LocalDate.of(2018, 7, 8));
    }

    @Test
    public void testDateGenerationMidWeekForSunday() {
        GenerateDateRange dateRange1 = new GenerateDateRange(1, LocalDate.of(2018, 7, 8));

        assertThat(dateRange1.getFromDate()).isEqualTo(LocalDate.of(2018, 7, 1));
        assertThat(dateRange1.getToDate()).isEqualTo(LocalDate.of(2018, 7, 8));
    }

    @Test
    public void testDateGenerationLastWeekForSaturday() {
        GenerateDateRange dateRange1 = new GenerateDateRange(1, LocalDate.of(2018, 7, 7));

        assertThat(dateRange1.getFromDate()).isEqualTo(LocalDate.of(2018, 6, 24));
        assertThat(dateRange1.getToDate()).isEqualTo(LocalDate.of(2018, 7, 1));
    }

    @Test
    public void testDateGenerationLastWeekFutureDate() {
        GenerateDateRange dateRange1 = new GenerateDateRange(1, LocalDate.of(2018, 7, 14));

        assertThat(dateRange1.getFromDate()).isEqualTo(LocalDate.of(2018, 7, 1));
        assertThat(dateRange1.getToDate()).isEqualTo(LocalDate.of(2018, 7, 8));
    }

    @Test
    public void testDateGenerationLastWeekFutureDate1WeekOn() {
        GenerateDateRange dateRange1 = new GenerateDateRange(1, LocalDate.of(2018, 7, 15));

        assertThat(dateRange1.getFromDate()).isEqualTo(LocalDate.of(2018, 7, 8));
        assertThat(dateRange1.getToDate()).isEqualTo(LocalDate.of(2018, 7, 15));
    }
}
