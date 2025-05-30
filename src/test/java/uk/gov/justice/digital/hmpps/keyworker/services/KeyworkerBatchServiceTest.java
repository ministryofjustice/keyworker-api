package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository;
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworkerConfiguration;
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyKeyworkerConfigurationRepository;

import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyworkerBatchServiceTest {

    private KeyworkerBatchService batchService;

    @Mock
    private LegacyKeyworkerConfigurationRepository keyworkerRepository;

    @Mock
    private ReferenceDataRepository referenceDataRepository;

    @Mock
    private TelemetryClient telemetryClient;

    @BeforeEach
    void setUp() {
        batchService = new KeyworkerBatchService(keyworkerRepository, referenceDataRepository, telemetryClient);
    }


    @Test
    void testUpdateStatusBatchHappy() {
        final var DATE_14_JAN_2018 = LocalDate.of(2018, Month.JANUARY, 14);
        final var today = LocalDate.now();

        final var activeStatus = ReferenceDataMock.getKeyworkerStatuses().get(StaffStatus.ACTIVE.name());
        when(referenceDataRepository.findByKey(activeStatus.getKey())).thenReturn(activeStatus);

        final var status = ReferenceDataMock.getKeyworkerStatuses().get(StaffStatus.UNAVAILABLE_ANNUAL_LEAVE.name());
        final var keyworker_backFromLeave = new LegacyKeyworkerConfiguration(2L, 6, status, Boolean.TRUE, DATE_14_JAN_2018);

        when(keyworkerRepository.findByStatusKeyCodeAndReactivateOnBefore(StaffStatus.UNAVAILABLE_ANNUAL_LEAVE.name(), today.plusDays(1))).thenReturn(Collections.singletonList(keyworker_backFromLeave));

        final var keyworkerIds = batchService.executeUpdateStatus();

        assertThat(keyworkerIds).containsExactlyInAnyOrder(2L);
    }

    @Test
    void testUpdateStatusJobException() {
        final var today = LocalDate.now();

        when(keyworkerRepository.findByStatusKeyCodeAndReactivateOnBefore(StaffStatus.UNAVAILABLE_ANNUAL_LEAVE.name(), today.plusDays(1))).thenThrow(new RuntimeException("test"));

        batchService.executeUpdateStatus();

        final var exception = ArgumentCaptor.forClass(RuntimeException.class);
        verify(telemetryClient).trackException(exception.capture());

        assertThat(exception.getValue().getMessage()).isEqualTo("test");
    }
}
