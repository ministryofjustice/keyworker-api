package uk.gov.justice.digital.hmpps.keyworker.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderEvent;
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService;

import java.io.IOException;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "sqs.provider")
@Slf4j
public class EventListener {

    private final ObjectMapper objectMapper;
    private final ReconciliationService reconciliationService;

    public EventListener(final ReconciliationService reconciliationService, final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.reconciliationService = reconciliationService;
    }

    @JmsListener(destination = "${sqs.queue.name}")
    public void eventListener(final String requestJson) {

        log.debug("Processing Message {}", requestJson);
        final var event = getOffenderEvent(requestJson);
        if (event != null) {
            if ("EXTERNAL_MOVEMENT_RECORD-INSERTED".equals(event.getEventType())) {
                reconciliationService.checkMovementAndDeallocate(event);
            } else if ("BOOKING_NUMBER-CHANGED".equals(event.getEventType())) {
                reconciliationService.checkForMergeAndDeallocate(event);
            }
        }
    }

    private OffenderEvent getOffenderEvent(final String requestJson) {
        OffenderEvent event = null;
        try {
            final Map<String, String> message = objectMapper.readValue(requestJson, Map.class);
            if (message != null && message.get("Message") != null) {
                event = objectMapper.readValue(message.get("Message"), OffenderEvent.class);
            }
        } catch (IOException e) {
            log.error("Failed to Parse Message {}", requestJson);
        }
        return event;
    }


}
