package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService.ReconMetrics;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.time.LocalDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.class)
public class ReconciliationServiceTest {

    private static final String OFFENDER_NO = "A1234AA";
    private static final String MERGED_OFFENDER_NO = "B1234BB";
    @Mock
    private NomisService nomisService;

    @Mock
    private OffenderKeyworkerRepository repository;

    @Mock
    private TelemetryClient telemetryClient;

    private final static String TEST_AGENCY_ID = "LEI";

    private ReconciliationService service;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        service = new ReconciliationService(nomisService, repository, telemetryClient);
    }

    @Test
    public void testReconciliation() {

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

        ReconMetrics metrics = service.reconcileKeyWorkerAllocations(TEST_AGENCY_ID);

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
    public void testCheckWhenTransferredOutDeallocationOccurs() {
        final var now = now();
        when(nomisService.getMovement(eq(-1L), eq(1L)))
                .thenReturn(Optional.of(Movement.builder()
                        .directionCode("OUT")
                        .fromAgency("LEI")
                        .toAgency("MDI")
                        .offenderNo(OFFENDER_NO)
                        .movementType("TRN")
                        .createDateTime(now)
                        .build()));

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

        service.checkMovementAndDeallocate(OffenderEvent.builder().bookingId(-1L).movementSeq(1L).build());

        assertThat(offenderKw.isActive()).isFalse();
        assertThat(offenderKw.getDeallocationReason()).isEqualTo(DeallocationReason.TRANSFER);
        assertThat(offenderKw.getExpiryDateTime()).isNotNull();

    }

    @Test
    public void testCheckWhenAdmittedInDeallocationOccurs() {
        final var now = now();
        when(nomisService.getMovement(eq(-1L), eq(1L)))
                .thenReturn(Optional.of(Movement.builder()
                        .directionCode("IN")
                        .fromAgency("CRTTRN")
                        .toAgency("MDI")
                        .offenderNo(OFFENDER_NO)
                        .movementType("ADM")
                        .createDateTime(now)
                        .build()));

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

        service.checkMovementAndDeallocate(OffenderEvent.builder().bookingId(-1L).movementSeq(1L).build());

        assertThat(offenderKw.isActive()).isFalse();
        assertThat(offenderKw.getDeallocationReason()).isEqualTo(DeallocationReason.TRANSFER);
        assertThat(offenderKw.getExpiryDateTime()).isNotNull();
    }
    @Test
    public void testCheckWhenAdmittedIntoSamePrisonNoDeallocationOccurs() {
        final var now = now();
        when(nomisService.getMovement(eq(-1L), eq(1L)))
                .thenReturn(Optional.of(Movement.builder()
                        .directionCode("IN")
                        .fromAgency("CRTTRN")
                        .toAgency("LEI")
                        .offenderNo(OFFENDER_NO)
                        .movementType("ADM")
                        .createDateTime(now)
                        .build()));

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

        service.checkMovementAndDeallocate(OffenderEvent.builder().bookingId(-1L).movementSeq(1L).build());

        assertThat(offenderKw.isActive()).isTrue();
        assertThat(offenderKw.getDeallocationReason()).isNull();
        assertThat(offenderKw.getExpiryDateTime()).isNull();
    }

    @Test
    public void testCheckWhenReleasedDeallocationOccurs() {
        final var now = now();
        when(nomisService.getMovement(eq(-1L), eq(1L)))
                .thenReturn(Optional.of(Movement.builder()
                        .directionCode("OUT")
                        .offenderNo(OFFENDER_NO)
                        .fromAgency("LEI")
                        .toAgency("OUT")
                        .movementType("REL")
                        .createDateTime(now)
                        .build()));

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


        service.checkMovementAndDeallocate(OffenderEvent.builder().bookingId(-1L).movementSeq(1L).build());

        assertThat(offenderKw.isActive()).isFalse();
        assertThat(offenderKw.getDeallocationReason()).isEqualTo(DeallocationReason.RELEASED);
        assertThat(offenderKw.getExpiryDateTime()).isNotNull();

    }

    @Test
    public void testCheckWhenOtherMovementNoDeallocationOccurs() {
        final var now = now();

        when(nomisService.getMovement(eq(-1L), eq(1L)))
                .thenReturn(Optional.of(Movement.builder()
                        .directionCode("OUT")
                        .offenderNo(OFFENDER_NO)
                        .movementType("HOS")
                        .createDateTime(now)
                        .build()));

        service.checkMovementAndDeallocate(OffenderEvent.builder().bookingId(-1L).movementSeq(1L).build());

        verify(repository, never()).findByActiveAndOffenderNo(eq(true), eq(OFFENDER_NO));

    }

    @Test
    public void testCheckForExistingAllocatedThatNeedsMerging() {
        when(nomisService.getIdentifiersByBookingId(eq(-1L)))
                .thenReturn(List.of(
                        BookingIdentifier.builder().type("MERGED").value(MERGED_OFFENDER_NO).build(),
                        BookingIdentifier.builder().type("PNC").value("XX/11XX").build()
                        ));

        when(nomisService.getBooking(eq(-1L)))
                .thenReturn(Optional.of(OffenderBooking.builder()
                        .bookingId(-1L)
                        .offenderNo(OFFENDER_NO)
                        .agencyId("LEI")
                        .build()));

        final var now = now();

        final var oldOffenderKw = OffenderKeyworker.builder()
                .offenderNo(MERGED_OFFENDER_NO)
                .prisonId("LEI")
                .active(true)
                .assignedDateTime(now)
                .allocationType(AllocationType.AUTO)
                .offenderKeyworkerId(-100L)
                .build();

        when(repository.findByOffenderNo(eq(MERGED_OFFENDER_NO)))
                .thenReturn(List.of(oldOffenderKw));

        final var newOffenderKw = OffenderKeyworker.builder()
                .offenderNo(OFFENDER_NO)
                .prisonId("LEI")
                .active(true)
                .assignedDateTime(now)
                .allocationType(AllocationType.AUTO)
                .offenderKeyworkerId(-100L)
                .build();

        when(repository.findByActiveAndOffenderNo(eq(true), eq(OFFENDER_NO)))
                .thenReturn(List.of(newOffenderKw));

        service.checkForMergeAndDeallocate(OffenderEvent.builder().bookingId(-1L).build());

        assertThat(oldOffenderKw.isActive()).isFalse();
        assertThat(oldOffenderKw.getDeallocationReason()).isEqualTo(DeallocationReason.MERGED);
        assertThat(oldOffenderKw.getExpiryDateTime()).isNotNull();
        assertThat(oldOffenderKw.getOffenderNo()).isEqualTo(OFFENDER_NO);

        assertThat(newOffenderKw.isActive()).isTrue();
        assertThat(newOffenderKw.getDeallocationReason()).isNull();
        assertThat(newOffenderKw.getExpiryDateTime()).isNull();
    }

    @Test
    public void testCheckForExistingAllocatedThatNeedsMergingNotDealloc() {
        when(nomisService.getIdentifiersByBookingId(eq(-1L)))
                .thenReturn(List.of(
                        BookingIdentifier.builder().type("MERGED").value(MERGED_OFFENDER_NO).build(),
                        BookingIdentifier.builder().type("PNC").value("XX/11XX").build()
                ));

        when(nomisService.getBooking(eq(-1L)))
                .thenReturn(Optional.of(OffenderBooking.builder()
                        .bookingId(-1L)
                        .offenderNo(OFFENDER_NO)
                        .agencyId("LEI")
                        .build()));

        final var now = now();

        final var oldOffenderKw = OffenderKeyworker.builder()
                .offenderNo(MERGED_OFFENDER_NO)
                .prisonId("LEI")
                .active(true)
                .assignedDateTime(now)
                .allocationType(AllocationType.AUTO)
                .offenderKeyworkerId(-100L)
                .build();

        when(repository.findByOffenderNo(eq(MERGED_OFFENDER_NO)))
                .thenReturn(List.of(oldOffenderKw));

        when(repository.findByActiveAndOffenderNo(eq(true), eq(OFFENDER_NO)))
                .thenReturn(List.of());

        service.checkForMergeAndDeallocate(OffenderEvent.builder().bookingId(-1L).build());

        assertThat(oldOffenderKw.isActive()).isTrue();
        assertThat(oldOffenderKw.getDeallocationReason()).isNull();
        assertThat(oldOffenderKw.getExpiryDateTime()).isNull();
        assertThat(oldOffenderKw.getOffenderNo()).isEqualTo(OFFENDER_NO);


    }
}
