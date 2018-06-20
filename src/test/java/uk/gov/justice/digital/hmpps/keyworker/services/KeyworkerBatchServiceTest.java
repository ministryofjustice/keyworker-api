package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerCustodyStatusDto;
import uk.gov.justice.digital.hmpps.keyworker.model.BatchHistory;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.Keyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.BatchHistoryRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.KeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class KeyworkerBatchServiceTest {

    private KeyworkerBatchService batchService;

    @Mock
    private NomisService nomisService;
    @Mock
    private OffenderKeyworkerRepository repository;
    @Mock
    private BatchHistoryRepository batchHistoryRepository;
    @Mock
    private KeyworkerRepository keyworkerRepository;
    @Mock
    private TelemetryClient telemetryClient;

    final private LocalDateTime threshold = LocalDateTime.of(2018, Month.JANUARY, 14, 12, 00);

    final private List<PrisonerCustodyStatusDto> prisonerStatusesDay0 = Arrays.asList(
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
    final private List<PrisonerCustodyStatusDto> prisonerStatusesDay3 = Arrays.asList(
            PrisonerCustodyStatusDto.builder()
                    .offenderNo("AA1111C-notinDB")
                    .createDateTime(threshold.plusMinutes(2))
                    .build()
    );

    @Before
    public void setUp() {
        batchService = new KeyworkerBatchService(repository, keyworkerRepository, nomisService, telemetryClient, batchHistoryRepository);
        ReflectionTestUtils.setField(batchService, "lookBackDays", 3);
        ReflectionTestUtils.setField(batchService, "lookBackDays", 3);
        ReflectionTestUtils.setField(batchService, "lookBackDays", 3);
        ReflectionTestUtils.setField(batchService, "maxAttempts", 2);
        ReflectionTestUtils.setField(batchService, "backoffMs", 100);
    }

    @Test
    public void testDeallocateJobHappy() {
        final LocalDate today = LocalDate.now();

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
        assertThat(events.get(2).get("dayNumber")).isEqualTo("-1");
        assertThat(events.get(2).get("prisonersFound")).isEqualTo("0");
        assertThat(events.get(3).get("dayNumber")).isEqualTo("-2");
        assertThat(events.get(3).get("prisonersFound")).isEqualTo("0");
        assertThat(events.get(4).get("dayNumber")).isEqualTo("-3");
        assertThat(events.get(4).get("prisonersFound")).isEqualTo("1");
    }

    // Check that when the db table row for the batch is missing, it uses the param and adds the new time in
    @Test
    public void testDeallocateJobTransitionToDbThreshold() {
        final LocalDate today = LocalDate.now();

        // Ensure threshold from parameter is used when there is nothing in the batch_history db table
        ArgumentCaptor<LocalDateTime> thresholdParam = ArgumentCaptor.forClass(LocalDateTime.class);
        when(nomisService.getPrisonerStatuses(thresholdParam.capture(), any(LocalDate.class))).thenReturn(Collections.emptyList());

        batchService.executeDeallocation(threshold);

        assertThat(thresholdParam.getValue()).isEqualTo(threshold);

        ArgumentCaptor<BatchHistory> batchHistory = ArgumentCaptor.forClass(BatchHistory.class);
        verify(batchHistoryRepository).save(batchHistory.capture());
        final BatchHistory value = batchHistory.getValue();
        assertThat(value.getName()).isEqualTo("DeallocateJob");
        assertThat(value.getLastRun()).isCloseTo(LocalDateTime.now(), new TemporalUnitWithinOffset(1, ChronoUnit.HOURS));
    }

    @Test
    public void testDeallocateJobUsingDbThreshold() {
        final LocalDate today = LocalDate.now();
        LocalDateTime dbThreshold = LocalDateTime.of(2018, Month.JUNE, 2, 11, 00);
        final BatchHistory dbRecord = BatchHistory.builder()
                .batchId(1L)
                .name("DeallocateJob")
                .lastRun(dbThreshold)
                .build();
        when(batchHistoryRepository.findByName("DeallocateJob")).thenReturn(dbRecord);

        // Ensure threshold from database is used when present in the batch_history db table (otherwise RuntimeException)
        when(nomisService.getPrisonerStatuses(eq(dbThreshold), any(LocalDate.class))).thenReturn(Collections.emptyList());
        when(nomisService.getPrisonerStatuses(eq(threshold), any(LocalDate.class))).thenThrow(new RuntimeException("Failed"));

        batchService.executeDeallocation(threshold); // should ignore this param
    }

    @Test
    public void testDeallocateJobException() {
        final LocalDate today = LocalDate.now();

        when(nomisService.getPrisonerStatuses(threshold, today)).thenThrow(new RuntimeException("test"));

        batchService.executeDeallocation(threshold);

        ArgumentCaptor<RuntimeException> exception = ArgumentCaptor.forClass(RuntimeException.class);
        verify(telemetryClient).trackException(exception.capture());

        assertThat(exception.getValue().getMessage()).isEqualTo("test");
    }

    @Test
    public void testOtherHttpServerErrorException() {
        final LocalDate today = LocalDate.now();

        when(nomisService.getPrisonerStatuses(threshold, today)).thenThrow(new HttpServerErrorException(HttpStatus.BAD_REQUEST));

        batchService.executeDeallocation(threshold);

        ArgumentCaptor<RuntimeException> exception = ArgumentCaptor.forClass(RuntimeException.class);
        verify(telemetryClient).trackException(exception.capture());

        assertThat(exception.getValue().getMessage()).isEqualTo("400 BAD_REQUEST");
    }

    @Test
    public void testSingleGatewayTimeout() {
        final LocalDate today = LocalDate.now();

        when(nomisService.getPrisonerStatuses(threshold, today))
                .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY, "Bad Gateway"))
                .thenReturn(prisonerStatusesDay0);

        when(nomisService.getPrisonerStatuses(threshold, today.plusDays(-1))).thenReturn(Collections.emptyList());
        when(nomisService.getPrisonerStatuses(threshold, today.plusDays(-2))).thenReturn(Collections.emptyList());
        when(nomisService.getPrisonerStatuses(threshold, today.plusDays(-3))).thenReturn(prisonerStatusesDay3);

        List<OffenderKeyworker> offenderDetailsA = Arrays.asList(OffenderKeyworker.builder().offenderNo("AA1111A").active(true).staffId(1234L).prisonId("MDI").build());
        List<OffenderKeyworker> offenderDetailsB = Arrays.asList(OffenderKeyworker.builder().offenderNo("AA1111B").active(true).build());
        when(repository.findByActiveAndOffenderNo(true, "AA1111A")).thenReturn(offenderDetailsA);
        when(repository.findByActiveAndOffenderNo(true, "AA1111B")).thenReturn(offenderDetailsB);

        batchService.executeDeallocation(threshold);

        ArgumentCaptor<RuntimeException> exception = ArgumentCaptor.forClass(RuntimeException.class);
        verify(telemetryClient, times(1)).trackException(exception.capture());
        assertThat(exception.getValue().getMessage()).isEqualTo("502 Bad Gateway");
        // Check processing proceeded correctly
        assertThat(offenderDetailsA.get(0).getExpiryDateTime()).isEqualTo(threshold);
        assertThat(offenderDetailsB.get(0).getExpiryDateTime()).isEqualTo(threshold.plusMinutes(1));
    }

    @Test
    public void testPersistentGatewayTimeout() {
        final LocalDate today = LocalDate.now();

        when(nomisService.getPrisonerStatuses(threshold, today)).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY, "Bad Gateway"));

        batchService.executeDeallocation(threshold);

        ArgumentCaptor<RuntimeException> exception = ArgumentCaptor.forClass(RuntimeException.class);
        verify(telemetryClient, times(2)).trackException(exception.capture());

        assertThat(exception.getValue().getMessage()).isEqualTo("502 Bad Gateway");
    }

    @Test
    public void testDeallocateJobDontProceed() {
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
