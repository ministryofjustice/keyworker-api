package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerCustodyStatusDto;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DeallocateJobTest {

    private DeallocateJob deallocateJob;

    @Mock
    private NomisService nomisService;
    @Mock
    private OffenderKeyworkerRepository repository;

    @Before
    public void setUp() {
        deallocateJob = new DeallocateJob();
        ReflectionTestUtils.setField(deallocateJob, "nomisService", nomisService);
        ReflectionTestUtils.setField(deallocateJob, "repository", repository);
    }

    @Test
    public void testVerifyPrisonSupportForUnsupportedPrison() {
        final LocalDateTime threshold = LocalDateTime.of(2018, Month.JANUARY, 14, 12, 00);
        List<PrisonerCustodyStatusDto> prisonerStatuses = Arrays.asList(
                PrisonerCustodyStatusDto.builder()
                        .offenderNo("AA1111A")
                        .createDateTime(threshold)
                        .build(),
                PrisonerCustodyStatusDto.builder()
                        .offenderNo("AA1111B")
                        .createDateTime(threshold.plusMinutes(1))
                        .build(),
                PrisonerCustodyStatusDto.builder()
                        .offenderNo("AA1111C-notinDB")
                        .createDateTime(threshold.plusMinutes(2))
                        .build()
        );
        when(nomisService.getPrisonerStatuses(threshold)).thenReturn(prisonerStatuses);
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
    }


}
