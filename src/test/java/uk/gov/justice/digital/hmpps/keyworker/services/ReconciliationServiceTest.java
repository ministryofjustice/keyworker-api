package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository;
import uk.gov.justice.digital.hmpps.keyworker.dto.BookingIdentifier;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderBooking;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerDetail;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerIdentifier;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;
import uk.gov.justice.digital.hmpps.keyworker.events.OffenderEventListener.OffenderEvent;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.utils.ReferenceDataHelper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.time.LocalDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.OngoingStubbingKt.whenever;


@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    private static final String OFFENDER_NO = "A1234AA";
    @Mock
    private NomisService nomisService;

    @Mock
    private OffenderKeyworkerRepository repository;

    @Mock
    private TelemetryClient telemetryClient;

    @Mock
    private ReferenceDataRepository referenceDataRepository;

    private final static String TEST_AGENCY_ID = "LEI";

    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        service = new ReconciliationService(nomisService, repository, telemetryClient, referenceDataRepository);
    }

    @Test
    void testReconciliation() {
        whenever(referenceDataRepository.findByKey(any())).thenAnswer(args -> ReferenceDataHelper.referenceDataOf(args.getArgument(0)));

        when(repository.findByActiveAndPrisonId(true, TEST_AGENCY_ID)).thenReturn(
                List.of(
                        OffenderKeyworker.builder().offenderNo(OFFENDER_NO).build(), //correct
                        OffenderKeyworker.builder().offenderNo("A1234AB").build(), //merged
                        OffenderKeyworker.builder().offenderNo("A1234AC").build(), //merged new now active
                        OffenderKeyworker.builder().offenderNo("A1234AD").build(), //released
                        OffenderKeyworker.builder().offenderNo("A1234AE").build(), //transferred
                        OffenderKeyworker.builder().offenderNo("A1234AF").build(), //cannot find
                        OffenderKeyworker.builder().offenderNo("A1234AG").build(), //merged now released
                        OffenderKeyworker.builder().offenderNo("A1234AH").build() //merged now transferred
                        )
        );

        when(nomisService.getOffendersAtLocation(TEST_AGENCY_ID, "bookingId", SortOrder.ASC, true)).thenReturn(
                List.of(
                        OffenderLocationDto.builder().offenderNo(OFFENDER_NO).build(),
                        OffenderLocationDto.builder().offenderNo("B1234AB").build(),
                        OffenderLocationDto.builder().offenderNo("B1234AC").build()
                )
        );

        when(nomisService.getPrisonerDetail("A1234AB", true)).thenReturn(Optional.empty());
        when(nomisService.getPrisonerDetail("A1234AC", true)).thenReturn(Optional.empty());
        when(nomisService.getPrisonerDetail("A1234AD", true)).thenReturn(Optional.of(PrisonerDetail.builder().offenderNo("A1234AD").latestLocationId("OUT").currentlyInPrison("N").build()));
        when(nomisService.getPrisonerDetail("A1234AE", true)).thenReturn(Optional.of(PrisonerDetail.builder().offenderNo("A1234AE").latestLocationId("MDI").currentlyInPrison("Y").build()));
        when(nomisService.getPrisonerDetail("A1234AF", true)).thenReturn(Optional.empty());
        when(nomisService.getPrisonerDetail("A1234AG", true)).thenReturn(Optional.empty());
        when(nomisService.getPrisonerDetail("A1234AH", true)).thenReturn(Optional.empty());

        when(nomisService.getIdentifierByTypeAndValue("MERGED", "A1234AB")).thenReturn(List.of(
                PrisonerIdentifier.builder().offenderNo("B1234AB").build()
        ));
        when(nomisService.getIdentifierByTypeAndValue("MERGED", "A1234AC")).thenReturn(List.of(
                PrisonerIdentifier.builder().offenderNo("B1234AC").build()
        ));
        when(nomisService.getIdentifierByTypeAndValue("MERGED", "A1234AF")).thenReturn(Collections.emptyList());
        when(nomisService.getIdentifierByTypeAndValue("MERGED", "A1234AG")).thenReturn(List.of(
                PrisonerIdentifier.builder().offenderNo("B1234AG").build()
        ));
        when(nomisService.getIdentifierByTypeAndValue("MERGED", "A1234AH")).thenReturn(List.of(
                PrisonerIdentifier.builder().offenderNo("B1234AH").build()
        ));

        when(repository.findByOffenderNo("A1234AB")).thenReturn(
                List.of(
                        OffenderKeyworker.builder().offenderKeyworkerId(1L).offenderNo("A1234AB").active(true).build(),
                        OffenderKeyworker.builder().offenderKeyworkerId(2L).offenderNo("A1234AB").active(false).build(),
                        OffenderKeyworker.builder().offenderKeyworkerId(3L).offenderNo("A1234AB").active(false).build()
                )
        );

        when(repository.findByOffenderNo("A1234AC")).thenReturn(
                List.of(OffenderKeyworker.builder().offenderKeyworkerId(4L).offenderNo("A1234AC").active(true).build())
        );

        when(repository.findByOffenderNo("A1234AG")).thenReturn(
                List.of(OffenderKeyworker.builder().offenderKeyworkerId(5L).offenderNo("A1234AG").active(true).build())
        );

        when(repository.findByOffenderNo("A1234AH")).thenReturn(
                List.of(OffenderKeyworker.builder().offenderKeyworkerId(6L).offenderNo("A1234AH").active(true).build())
        );

        when(repository.findByActiveAndOffenderNo(true,"B1234AB")).thenReturn(Collections.emptyList());
        when(repository.findByActiveAndOffenderNo(true,"B1234AC")).thenReturn(List.of(OffenderKeyworker.builder().offenderNo("B1234AC").active(true).build()));
        when(repository.findByActiveAndOffenderNo(true,"B1234AG")).thenReturn(Collections.emptyList());
        when(repository.findByActiveAndOffenderNo(true,"B1234AH")).thenReturn(Collections.emptyList());

        when(nomisService.getPrisonerDetail("B1234AB", true)).thenReturn(Optional.of(PrisonerDetail.builder().offenderNo("B1234AB").latestLocationId("LEI").currentlyInPrison("Y").build()));
        when(nomisService.getPrisonerDetail("B1234AC", true)).thenReturn(Optional.of(PrisonerDetail.builder().offenderNo("B1234AC").latestLocationId("LEI").currentlyInPrison("Y").build()));
        when(nomisService.getPrisonerDetail("B1234AG", true)).thenReturn(Optional.of(PrisonerDetail.builder().offenderNo("B1234AG").latestLocationId("OUT").currentlyInPrison("N").build()));
        when(nomisService.getPrisonerDetail("B1234AH", true)).thenReturn(Optional.of(PrisonerDetail.builder().offenderNo("B1234AH").latestLocationId("MDI").currentlyInPrison("Y").build()));

        final var metrics = service.reconcileKeyWorkerAllocations(TEST_AGENCY_ID);

        assertThat(metrics).isNotNull();

        assertThat(metrics.getPrisonId()).isEqualTo(TEST_AGENCY_ID);
        assertThat(metrics.getActiveKeyWorkerAllocations()).isEqualTo(8);
        assertThat(metrics.getUnmatchedOffenders()).isEqualTo(7);
        assertThat(metrics.getNotFoundOffenders().get()).isEqualTo(5);
        assertThat(metrics.getDeAllocatedOffenders().get()).isEqualTo(5);
        assertThat(metrics.getMissingOffenders().get()).isEqualTo(1);
        assertThat(metrics.getMergedRecords()).hasSize(4);
    }

    @Test
    void testCheckWhenTransferredOutDeallocationOccurs() {
        whenever(referenceDataRepository.findByKey(any())).thenAnswer(args -> ReferenceDataHelper.referenceDataOf(args.getArgument(0)));

        final var now = now();
        final var offenderKw = OffenderKeyworker.builder()
                .offenderNo(OFFENDER_NO)
                .prisonId("LEI")
                .active(true)
                .assignedDateTime(now)
                .allocationType(AllocationType.AUTO)
                .offenderKeyworkerId(-100L)
                .build();

        when(repository.findByActiveAndOffenderNo(eq(true), eq(OFFENDER_NO)))
                .thenReturn(List.of(offenderKw));

        when(nomisService.isPrison(eq("MDI"))).thenReturn(true);

        assertThat(offenderKw.isActive()).isTrue();
        assertThat(offenderKw.getDeallocationReason()).isNull();
        assertThat(offenderKw.getExpiryDateTime()).isNull();

        final var movement = new OffenderEvent(-1L, 1L, OFFENDER_NO, now, "TRN",
                null, "OUT", null, "LEI", "MDI");

        service.checkMovementAndDeallocate(movement);

        assertThat(offenderKw.isActive()).isFalse();
        assertThat(offenderKw.getDeallocationReason().getCode()).isEqualTo(DeallocationReason.TRANSFER.getReasonCode());
        assertThat(offenderKw.getExpiryDateTime()).isNotNull();

    }

    @Test
    void testCheckWhenAdmittedInDeallocationOccurs() {
        whenever(referenceDataRepository.findByKey(any())).thenAnswer(args -> ReferenceDataHelper.referenceDataOf(args.getArgument(0)));

        final var now = now();

        final var offenderKw = OffenderKeyworker.builder()
                .offenderNo(OFFENDER_NO)
                .prisonId("LEI")
                .active(true)
                .assignedDateTime(now)
                .allocationType(AllocationType.AUTO)
                .offenderKeyworkerId(-100L)
                .build();

        when(repository.findByActiveAndOffenderNo(eq(true), eq(OFFENDER_NO)))
                .thenReturn(List.of(offenderKw));

        assertThat(offenderKw.isActive()).isTrue();
        assertThat(offenderKw.getDeallocationReason()).isNull();
        assertThat(offenderKw.getExpiryDateTime()).isNull();

        final var movement = new OffenderEvent(-1L, 1L, OFFENDER_NO, now, "ADM", null, "IN", null, "CRTTRN", "MDI");
        service.checkMovementAndDeallocate(movement);

        assertThat(offenderKw.isActive()).isFalse();
        assertThat(offenderKw.getDeallocationReason().getCode()).isEqualTo(DeallocationReason.TRANSFER.getReasonCode());
        assertThat(offenderKw.getExpiryDateTime()).isNotNull();
    }
    @Test
    void testCheckWhenAdmittedIntoSamePrisonNoDeallocationOccurs() {
        final var now = now();

        final var offenderKw = OffenderKeyworker.builder()
                .offenderNo(OFFENDER_NO)
                .prisonId("LEI")
                .active(true)
                .assignedDateTime(now)
                .allocationType(AllocationType.AUTO)
                .offenderKeyworkerId(-100L)
                .build();

        when(repository.findByActiveAndOffenderNo(eq(true), eq(OFFENDER_NO)))
                .thenReturn(List.of(offenderKw));

        assertThat(offenderKw.isActive()).isTrue();
        assertThat(offenderKw.getDeallocationReason()).isNull();
        assertThat(offenderKw.getExpiryDateTime()).isNull();

        final var movement = new OffenderEvent(-1L, 1L, OFFENDER_NO, now, "ADM",
                null, "IN", null, "CRTTRN", "LEI");
        service.checkMovementAndDeallocate(movement);

        assertThat(offenderKw.isActive()).isTrue();
        assertThat(offenderKw.getDeallocationReason()).isNull();
        assertThat(offenderKw.getExpiryDateTime()).isNull();
    }

    @Test
    void testCheckWhenReleasedDeallocationOccurs() {
        whenever(referenceDataRepository.findByKey(any())).thenAnswer(args -> ReferenceDataHelper.referenceDataOf(args.getArgument(0)));
        final var now = now();

        final var offenderKw = OffenderKeyworker.builder()
                .offenderNo(OFFENDER_NO)
                .prisonId("LEI")
                .active(true)
                .assignedDateTime(now)
                .allocationType(AllocationType.AUTO)
                .offenderKeyworkerId(-100L)
                .build();

        when(repository.findByActiveAndOffenderNo(eq(true), eq(OFFENDER_NO)))
                .thenReturn(List.of(offenderKw));

        assertThat(offenderKw.isActive()).isTrue();
        assertThat(offenderKw.getDeallocationReason()).isNull();
        assertThat(offenderKw.getExpiryDateTime()).isNull();


        final var movement = new OffenderEvent(-1L, 1L, OFFENDER_NO, now, "REL",
                null, "OUT", null, "LEI", "OUT");
        service.checkMovementAndDeallocate(movement);

        assertThat(offenderKw.isActive()).isFalse();
        assertThat(offenderKw.getDeallocationReason().getCode()).isEqualTo(DeallocationReason.RELEASED.getReasonCode());
        assertThat(offenderKw.getExpiryDateTime()).isNotNull();

    }

    @Test
    void testCheckWhenOtherMovementNoDeallocationOccurs() {
        final var now = now();

        final var movement = new OffenderEvent(-1L, 1L, OFFENDER_NO, now, "HOS",
                null, "OUT", null, "MDI", "HOS1");
        service.checkMovementAndDeallocate(movement);

        verify(repository, never()).findByActiveAndOffenderNo(eq(true), eq(OFFENDER_NO));

    }
}
