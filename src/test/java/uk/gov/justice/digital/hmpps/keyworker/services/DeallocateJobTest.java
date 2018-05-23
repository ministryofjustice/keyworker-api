package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerCustodyStatusDto;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DeallocateJobTest {

    private DeallocateJob deallocateJob;

    @Mock
    private NomisService nomisService;
    @Mock
    private OffenderKeyworkerRepository repository;
    @Mock
    private TelemetryClient telemetryClient;

    @Before
    public void setUp() {
        deallocateJob = new DeallocateJob();
        ReflectionTestUtils.setField(deallocateJob, "nomisService", nomisService);
        ReflectionTestUtils.setField(deallocateJob, "repository", repository);
        ReflectionTestUtils.setField(deallocateJob, "telemetryClient", telemetryClient);
        ReflectionTestUtils.setField(deallocateJob, "lookBackDays", 3);
    }

    @Test
    public void testDeallocateJobHappy() {
        final LocalDateTime threshold = LocalDateTime.of(2018, Month.JANUARY, 14, 12, 00);
        final LocalDate today = LocalDate.now();

        List<PrisonerCustodyStatusDto> prisonerStatusesDay0 = Arrays.asList(
                PrisonerCustodyStatusDto.builder()
                        .offenderNo("AA1111A")
                        .createDateTime(threshold)
                        .build(),
                PrisonerCustodyStatusDto.builder()
                        .offenderNo("AA1111B")
                        .createDateTime(threshold.plusMinutes(1))
                        .build()
        );
        List<PrisonerCustodyStatusDto> prisonerStatusesDay3 = Arrays.asList(
                PrisonerCustodyStatusDto.builder()
                        .offenderNo("AA1111C-notinDB")
                        .createDateTime(threshold.plusMinutes(2))
                        .build()
        );
        when(nomisService.getPrisonerStatuses(threshold, today)).thenReturn(prisonerStatusesDay0);
        when(nomisService.getPrisonerStatuses(threshold, today.plusDays(-1))).thenReturn(Collections.emptyList());
        when(nomisService.getPrisonerStatuses(threshold, today.plusDays(-2))).thenReturn(Collections.emptyList());
        when(nomisService.getPrisonerStatuses(threshold, today.plusDays(-3))).thenReturn(prisonerStatusesDay3);

        List<OffenderKeyworker> offenderDetailsA = Arrays.asList(OffenderKeyworker.builder().offenderNo("AA1111A").active(true).build());
        List<OffenderKeyworker> offenderDetailsB = Arrays.asList(OffenderKeyworker.builder().offenderNo("AA1111B").active(true).build());
        when(repository.findByActiveAndOffenderNo(true, "AA1111A")).thenReturn(offenderDetailsA);
        when(repository.findByActiveAndOffenderNo(true, "AA1111B")).thenReturn(offenderDetailsB);

        deallocateJob.execute(threshold);

        assertThat(offenderDetailsA).asList().containsExactly(OffenderKeyworker.builder().offenderNo("AA1111A")
                .active(false)
                .expiryDateTime(threshold)
                .deallocationReason(DeallocationReason.RELEASED)
                .build());
        assertThat(offenderDetailsB).asList().containsExactly(OffenderKeyworker.builder().offenderNo("AA1111B")
                .active(false)
                .expiryDateTime(threshold.plusMinutes(1))
                .deallocationReason(DeallocationReason.RELEASED)
                .build());
        verify(repository).findByActiveAndOffenderNo(true, "AA1111C-notinDB");

        ArgumentCaptor<Map> event = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map> metrics = ArgumentCaptor.forClass(Map.class);
        verify(telemetryClient, times(5)).trackEvent(anyString(), event.capture(), metrics.capture());

        final List<Map> events = event.getAllValues();
        assertThat(events.get(0).get("date")).isEqualTo(today.format(DateTimeFormatter.ISO_LOCAL_DATE));
        assertThat(events.get(0).get("previousJobStart")).isEqualTo(threshold.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        assertThat(events.get(1).get("dayNumber")).isEqualTo("0");
        assertThat(events.get(1).get("prisonersFound")).isEqualTo("2");
        // assertThat(events.get(1).get("queryMs")).is(Condition);
        assertThat(events.get(2).get("dayNumber")).isEqualTo("-1");
        assertThat(events.get(2).get("prisonersFound")).isEqualTo("0");
        assertThat(events.get(3).get("dayNumber")).isEqualTo("-2");
        assertThat(events.get(3).get("prisonersFound")).isEqualTo("0");
        assertThat(events.get(4).get("dayNumber")).isEqualTo("-3");
        assertThat(events.get(4).get("prisonersFound")).isEqualTo("1");
    }

    @Test
    public void testDeallocateJobException() {
        final LocalDateTime threshold = LocalDateTime.of(2018, Month.JANUARY, 14, 12, 00);
        final LocalDate today = LocalDate.now();

        when(nomisService.getPrisonerStatuses(threshold, today)).thenThrow(new RuntimeException("test"));

        deallocateJob.execute(threshold);

        ArgumentCaptor<RuntimeException> exception = ArgumentCaptor.forClass(RuntimeException.class);
        verify(telemetryClient).trackException(exception.capture());

        assertThat(exception.getValue().getMessage()).isEqualTo("test");
    }
}
