package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerDetail;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerIdentifier;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;
import uk.gov.justice.digital.hmpps.keyworker.events.OffenderEventListener.OffenderEvent;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworkerAllocation;
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyKeyworkerAllocationRepository;
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
    private LegacyKeyworkerAllocationRepository repository;

    @Mock
    private ReferenceDataRepository referenceDataRepository;

    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        service = new ReconciliationService(nomisService, repository, referenceDataRepository);
    }

    @Test
    void testCheckWhenTransferredOutDeallocationOccurs() {
        whenever(referenceDataRepository.findByKey(any())).thenAnswer(args -> ReferenceDataHelper.referenceDataOf(args.getArgument(0)));

        final var now = now();
        final var offenderKw = LegacyKeyworkerAllocation.builder()
                .offenderNo(OFFENDER_NO)
                .prisonId("LEI")
                .active(true)
                .assignedDateTime(now)
                .allocationType(AllocationType.AUTO)
                .build();

        when(repository.findByActiveAndPersonIdentifier(eq(true), eq(OFFENDER_NO)))
                .thenReturn(List.of(offenderKw));

        when(nomisService.isPrison(eq("MDI"))).thenReturn(true);

        assertThat(offenderKw.isActive()).isTrue();
        assertThat(offenderKw.getDeallocationReason()).isNull();
        assertThat(offenderKw.getDeallocatedAt()).isNull();

        final var movement = new OffenderEvent(-1L, 1L, OFFENDER_NO, now, "TRN",
                null, "OUT", null, "LEI", "MDI");

        service.checkMovementAndDeallocate(movement);

        assertThat(offenderKw.isActive()).isFalse();
        assertThat(offenderKw.getDeallocationReason().getCode()).isEqualTo(DeallocationReason.TRANSFER.getReasonCode());
        assertThat(offenderKw.getDeallocatedAt()).isNotNull();

    }

    @Test
    void testCheckWhenAdmittedInDeallocationOccurs() {
        whenever(referenceDataRepository.findByKey(any())).thenAnswer(args -> ReferenceDataHelper.referenceDataOf(args.getArgument(0)));

        final var now = now();

        final var offenderKw = LegacyKeyworkerAllocation.builder()
                .offenderNo(OFFENDER_NO)
                .prisonId("LEI")
                .active(true)
                .assignedDateTime(now)
                .allocationType(AllocationType.AUTO)
                .build();

        when(repository.findByActiveAndPersonIdentifier(eq(true), eq(OFFENDER_NO)))
                .thenReturn(List.of(offenderKw));

        assertThat(offenderKw.isActive()).isTrue();
        assertThat(offenderKw.getDeallocationReason()).isNull();
        assertThat(offenderKw.getDeallocatedAt()).isNull();

        final var movement = new OffenderEvent(-1L, 1L, OFFENDER_NO, now, "ADM", null, "IN", null, "CRTTRN", "MDI");
        service.checkMovementAndDeallocate(movement);

        assertThat(offenderKw.isActive()).isFalse();
        assertThat(offenderKw.getDeallocationReason().getCode()).isEqualTo(DeallocationReason.TRANSFER.getReasonCode());
        assertThat(offenderKw.getDeallocatedAt()).isNotNull();
    }
    @Test
    void testCheckWhenAdmittedIntoSamePrisonNoDeallocationOccurs() {
        final var now = now();

        final var offenderKw = LegacyKeyworkerAllocation.builder()
                .offenderNo(OFFENDER_NO)
                .prisonId("LEI")
                .active(true)
                .assignedDateTime(now)
                .allocationType(AllocationType.AUTO)
                .build();

        when(repository.findByActiveAndPersonIdentifier(eq(true), eq(OFFENDER_NO)))
                .thenReturn(List.of(offenderKw));

        assertThat(offenderKw.isActive()).isTrue();
        assertThat(offenderKw.getDeallocationReason()).isNull();
        assertThat(offenderKw.getDeallocatedAt()).isNull();

        final var movement = new OffenderEvent(-1L, 1L, OFFENDER_NO, now, "ADM",
                null, "IN", null, "CRTTRN", "LEI");
        service.checkMovementAndDeallocate(movement);

        assertThat(offenderKw.isActive()).isTrue();
        assertThat(offenderKw.getDeallocationReason()).isNull();
        assertThat(offenderKw.getDeallocatedAt()).isNull();
    }

    @Test
    void testCheckWhenReleasedDeallocationOccurs() {
        whenever(referenceDataRepository.findByKey(any())).thenAnswer(args -> ReferenceDataHelper.referenceDataOf(args.getArgument(0)));
        final var now = now();

        final var offenderKw = LegacyKeyworkerAllocation.builder()
                .offenderNo(OFFENDER_NO)
                .prisonId("LEI")
                .active(true)
                .assignedDateTime(now)
                .allocationType(AllocationType.AUTO)
                .build();

        when(repository.findByActiveAndPersonIdentifier(eq(true), eq(OFFENDER_NO)))
                .thenReturn(List.of(offenderKw));

        assertThat(offenderKw.isActive()).isTrue();
        assertThat(offenderKw.getDeallocationReason()).isNull();
        assertThat(offenderKw.getDeallocatedAt()).isNull();


        final var movement = new OffenderEvent(-1L, 1L, OFFENDER_NO, now, "REL",
                null, "OUT", null, "LEI", "OUT");
        service.checkMovementAndDeallocate(movement);

        assertThat(offenderKw.isActive()).isFalse();
        assertThat(offenderKw.getDeallocationReason().getCode()).isEqualTo(DeallocationReason.RELEASED.getReasonCode());
        assertThat(offenderKw.getDeallocatedAt()).isNotNull();

    }

    @Test
    void testCheckWhenOtherMovementNoDeallocationOccurs() {
        final var now = now();

        final var movement = new OffenderEvent(-1L, 1L, OFFENDER_NO, now, "HOS",
                null, "OUT", null, "MDI", "HOS1");
        service.checkMovementAndDeallocate(movement);

        verify(repository, never()).findByActiveAndPersonIdentifier(eq(true), eq(OFFENDER_NO));

    }
}
