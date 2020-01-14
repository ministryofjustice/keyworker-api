package uk.gov.justice.digital.hmpps.keyworker.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.digital.hmpps.keyworker.events.EventListener.OffenderEvent;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService;
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService;
import wiremock.org.apache.commons.io.IOUtils;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EventListenerTest {
    @Mock
    private ReconciliationService reconciliationService;
    @Mock
    private KeyworkerService keyworkerService;

    private EventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener = new EventListener(reconciliationService, keyworkerService);
    }

    @Test
    void testBookingNumberChanged() throws IOException {
        eventListener.eventListener(getJson("booking-number-changed.json"));

        verify(reconciliationService).checkForMergeAndDeallocate(new OffenderEvent(100001L, null, null));
        verifyNoInteractions(keyworkerService);
    }

    @Test
    void testExternalMovementRecordInserted() throws IOException {
        eventListener.eventListener(getJson("external-movement-record-inserted.json"));

        verify(reconciliationService).checkMovementAndDeallocate(new OffenderEvent(100001L, 3L, null));
        verifyNoInteractions(keyworkerService);
    }

    @Test
    void testDeleteEvent() throws IOException {
        eventListener.eventListener(getJson("offender-deletion-request.json"));

        verify(keyworkerService).deleteKeyworkersForOffender("A1234AA");
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void testDeleteEventBadMessage() {
        assertThatThrownBy(() -> eventListener.eventListener(getJson("offender-deletion-request-bad-message.json")))
                .hasMessageContaining("Expected BEGIN_OBJECT but was STRING at line 1");

        verifyNoInteractions(keyworkerService, reconciliationService);
    }

    @Test
    void testDeleteEventEmpty() {
        assertThatThrownBy(() -> eventListener.eventListener(getJson("offender-deletion-request-empty.json")))
                .hasMessageContaining("Found blank offender id for");

        verifyNoInteractions(keyworkerService, reconciliationService);
    }

    private String getJson(final String filename) throws IOException {
        return IOUtils.toString(getClass().getResourceAsStream(filename), UTF_8.toString());
    }
}
