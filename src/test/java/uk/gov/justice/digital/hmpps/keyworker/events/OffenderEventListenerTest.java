package uk.gov.justice.digital.hmpps.keyworker.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.digital.hmpps.keyworker.config.JsonConfig;
import uk.gov.justice.digital.hmpps.keyworker.events.OffenderEventListener.OffenderEvent;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService;
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService;
import wiremock.org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.time.LocalDateTime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OffenderEventListenerTest {
    @Mock
    private ReconciliationService reconciliationService;
    @Mock
    private KeyworkerService keyworkerService;

    private OffenderEventListener offenderEventListener;

    @BeforeEach
    void setUp() {
        offenderEventListener = new OffenderEventListener(reconciliationService, keyworkerService, new JsonConfig().gson());
    }

    @Test
    void testBookingNumberChanged() throws IOException {
        offenderEventListener.eventListener(getJson("booking-number-changed.json"));

        verify(reconciliationService).checkForMergeAndDeallocate(100001L);
        verifyNoInteractions(keyworkerService);
    }

    @Test
    void testExternalMovementRecordInserted() throws IOException {
        offenderEventListener.eventListener(getJson("external-movement-record-inserted.json"));

        final var movement = new OffenderEvent(100001L, 3L, "A1234AA", LocalDateTime.of(2020, 02, 29, 12, 34,56), "ADM", "ADM", "IN", "POL", "CRTTRN", "MDI");

        verify(reconciliationService).checkMovementAndDeallocate(movement);
        verifyNoInteractions(keyworkerService);
    }

    @Test
    void testDeleteEvent() throws IOException {
        offenderEventListener.eventListener(getJson("offender-deletion-request.json"));

        verify(keyworkerService).deleteKeyworkersForOffender("A1234AA");
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void testDeleteEventBadMessage() {
        assertThatThrownBy(() -> offenderEventListener.eventListener(getJson("offender-deletion-request-bad-message.json")))
                .hasMessageContaining("Expected BEGIN_OBJECT but was STRING at line 1");

        verifyNoInteractions(keyworkerService, reconciliationService);
    }

    @Test
    void testDeleteEventEmpty() throws IOException {
        offenderEventListener.eventListener(getJson("offender-deletion-request-empty.json"));

        verify(keyworkerService).deleteKeyworkersForOffender("");
        verifyNoInteractions(reconciliationService);
    }

    private String getJson(final String filename) throws IOException {
        return IOUtils.toString(getClass().getResourceAsStream(filename), UTF_8.toString());
    }
}
