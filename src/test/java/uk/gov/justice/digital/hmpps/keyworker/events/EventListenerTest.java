package uk.gov.justice.digital.hmpps.keyworker.events;

import com.google.gson.*;
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
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
        eventListener = new EventListener(reconciliationService, keyworkerService, new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer()).create());
    }

    static class LocalDateTimeSerializer implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        @Override
        public JsonElement serialize(LocalDateTime localDateTime, Type srcType, JsonSerializationContext context) {
            return new JsonPrimitive(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(localDateTime));
        }
        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return LocalDateTime.parse(json.getAsString(),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.ENGLISH));
        }
    }

    @Test
    void testBookingNumberChanged() throws IOException {
        eventListener.eventListener(getJson("booking-number-changed.json"));

        verify(reconciliationService).checkForMergeAndDeallocate(100001L);
        verifyNoInteractions(keyworkerService);
    }

    @Test
    void testExternalMovementRecordInserted() throws IOException {
        eventListener.eventListener(getJson("external-movement-record-inserted.json"));

        final var movement = new OffenderEvent(100001L, 3L, "A1234AA", LocalDateTime.of(2020, 02, 29, 12, 34,56), "ADM", "ADM", "IN", "POL", "CRTTRN", "MDI");

        verify(reconciliationService).checkMovementAndDeallocate(movement);
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
    void testDeleteEventEmpty() throws IOException {
        eventListener.eventListener(getJson("offender-deletion-request-empty.json"));

        verify(keyworkerService).deleteKeyworkersForOffender("");
        verifyNoInteractions(reconciliationService);
    }

    private String getJson(final String filename) throws IOException {
        return IOUtils.toString(getClass().getResourceAsStream(filename), UTF_8.toString());
    }
}
