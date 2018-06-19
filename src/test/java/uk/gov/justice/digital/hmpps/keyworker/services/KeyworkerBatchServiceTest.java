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
import uk.gov.justice.digital.hmpps.keyworker.model.Keyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.KeyworkerRepository;
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
public class KeyworkerBatchServiceTest {

    private KeyworkerBatchService batchService;

    @Mock
    private NomisService nomisService;
    @Mock
    private OffenderKeyworkerRepository repository;
    @Mock
    private KeyworkerRepository keyworkerRepository;
    @Mock
    private TelemetryClient telemetryClient;

    @Before
    public void setUp() {
        batchService = new KeyworkerBatchService(repository, keyworkerRepository, nomisService, telemetryClient);
        ReflectionTestUtils.setField(batchService, "lookBackDays", 3);
    }
    @Test
    public void testDeallocateJobHappy() {
        final LocalDateTime threshold = LocalDateTime.of(2018, Month.JANUARY, 14, 12, 00);
        final LocalDate today = LocalDate.now();

        List<PrisonerCustodyStatusDto> prisonerStatusesDay0 = Arrays.asList(
                PrisonerCustodyStatusDto.builder()
                        .offenderNo("AA1111A")
                        .toAgency("LEI")
                        .createDateTime(threshold)
                        .build(),
                PrisonerCustodyStatusDto.builder()
                        .offenderNo("AA1111B")
                        .toAgency("SYI")
                        .createDateTime(threshold.plusMinutes(1))
                        .movementType("REL")
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

        List<OffenderKeyworker> offenderDetailsA = Arrays.asList(OffenderKeyworker.builder().offenderNo("AA1111A").active(true).staffId(1234L).prisonId("MDI").build());
        List<OffenderKeyworker> offenderDetailsB = Arrays.asList(OffenderKeyworker.builder().offenderNo("AA1111B").active(true).build());
        when(repository.findByActiveAndOffenderNo(true, "AA1111A")).thenReturn(offenderDetailsA);
        when(repository.findByActiveAndOffenderNo(true, "AA1111B")).thenReturn(offenderDetailsB);

        batchService.executeDeallocation(threshold);

        assertThat(offenderDetailsA.get(0).getOffenderNo()).isEqualTo("AA1111A");
        assertThat(offenderDetailsA.get(0).isActive()).isFalse();
        assertThat(offenderDetailsA.get(0).getExpiryDateTime()).isEqualTo(threshold);
        assertThat(offenderDetailsA.get(0).getDeallocationReason()).isEqualTo(DeallocationReason.TRANSFER);

        assertThat(offenderDetailsB.get(0).getOffenderNo()).isEqualTo("AA1111B");
        assertThat(offenderDetailsB.get(0).isActive()).isFalse();
        assertThat(offenderDetailsB.get(0).getExpiryDateTime()).isEqualTo(threshold.plusMinutes(1));
        assertThat(offenderDetailsB.get(0).getDeallocationReason()).isEqualTo(DeallocationReason.RELEASED);

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

        batchService.executeDeallocation(threshold);

        ArgumentCaptor<RuntimeException> exception = ArgumentCaptor.forClass(RuntimeException.class);
        verify(telemetryClient).trackException(exception.capture());

        assertThat(exception.getValue().getMessage()).isEqualTo("test");
    }

    @Test
    public void testDeallocateJobDontProceed() {
        final LocalDateTime threshold = LocalDateTime.of(2018, Month.JANUARY, 14, 12, 00);
        final LocalDate today = LocalDate.now();

        List<PrisonerCustodyStatusDto> prisonerStatusesDay0 = Arrays.asList(
                PrisonerCustodyStatusDto.builder()
                        .offenderNo("AA1111A")
                        .createDateTime(threshold)
                        .movementType("TRN")
                        .directionCode("OUT")
                        .fromAgency("BMI")
                        .toAgency("BXI")
                        .build()
        );
        when(nomisService.getPrisonerStatuses(threshold, today)).thenReturn(prisonerStatusesDay0);

        List<OffenderKeyworker> offenderDetailsA = Arrays.asList(OffenderKeyworker.builder().offenderNo("AA1111A").prisonId("BXI").active(true).build());
        when(repository.findByActiveAndOffenderNo(true, "AA1111A")).thenReturn(offenderDetailsA);

        batchService.executeDeallocation(threshold);

        verify(repository).findByActiveAndOffenderNo(true, "AA1111A");
        // Check that nothing happened
        assertThat(offenderDetailsA.get(0).getOffenderNo()).isEqualTo("AA1111A");
        assertThat(offenderDetailsA.get(0).getPrisonId()).isEqualTo("BXI");
        assertThat(offenderDetailsA.get(0).isActive()).isTrue();
        assertThat(offenderDetailsA.get(0).getExpiryDateTime()).isNull();
        assertThat(offenderDetailsA.get(0).getDeallocationReason()).isNull();
    }

    @Test
    public void testUpdateStatusBatchHappy() {
        final LocalDate DATE_14_JAN_2018 = LocalDate.of(2018, Month.JANUARY, 14);
        final LocalDate today = LocalDate.now();

        final Keyworker keyworker_backFromLeave = new Keyworker(2l, 6, KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, Boolean.TRUE, DATE_14_JAN_2018);

        when(keyworkerRepository.findByStatusAndActiveDateBefore(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, today.plusDays(1))).thenReturn(Arrays.asList(keyworker_backFromLeave));

        final List<Long> keyworkerIds = batchService.executeUpdateStatus();

        assertThat(keyworkerIds).containsExactlyInAnyOrder(2l);
    }

    @Test
    public void testUpdateStatusJobException() {
        final LocalDateTime threshold = LocalDateTime.of(2018, Month.JANUARY, 14, 12, 00);
        final LocalDate today = LocalDate.now();

        when(keyworkerRepository.findByStatusAndActiveDateBefore(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, today.plusDays(1))).thenThrow(new RuntimeException("test"));

        batchService.executeUpdateStatus();

        ArgumentCaptor<RuntimeException> exception = ArgumentCaptor.forClass(RuntimeException.class);
        verify(telemetryClient).trackException(exception.capture());

        assertThat(exception.getValue().getMessage()).isEqualTo("test");
    }
}
