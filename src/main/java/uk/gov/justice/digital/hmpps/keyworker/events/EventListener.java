package uk.gov.justice.digital.hmpps.keyworker.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderEvent;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService;
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService;

import java.util.Optional;

@Service
@ConditionalOnProperty(name = "sqs.provider")
@Slf4j
@AllArgsConstructor
public class EventListener {
    private final ObjectMapper objectMapper;
    private final ReconciliationService reconciliationService;
    private final KeyworkerService keyworkerService;

    @JmsListener(destination = "${sqs.queue.name}")
    public void eventListener(final String requestJson) throws JsonProcessingException {
        final var message = getMessage(requestJson);
        final var eventType = message.getMessageAttributes().getEventType().getValue();
        log.info("Processing message of type {}", eventType);

        final var event = getOffenderEvent(message.getMessage());
        switch (eventType) {
            case "EXTERNAL_MOVEMENT_RECORD-INSERTED":
                reconciliationService.checkMovementAndDeallocate(event);
                break;
            case "BOOKING_NUMBER-CHANGED":
                reconciliationService.checkForMergeAndDeallocate(event);
                break;
            case "DATA_COMPLIANCE_DELETE-OFFENDER":
                Preconditions.checkState(StringUtils.isNotBlank(event.getOffenderIdDisplay()), "Found null offender id for %s", requestJson);
                keyworkerService.deleteKeyworkersForOffender(event.getOffenderIdDisplay());
                break;
        }
    }

    private Message getMessage(final String requestJson) throws JsonProcessingException {
        final var message = objectMapper.readValue(requestJson, new TypeReference<Message>() {
        });
        return Optional.ofNullable(message).orElseThrow(() -> new RuntimeException(String.format("Missing message from %s", requestJson)));
    }

    private OffenderEvent getOffenderEvent(final String requestJson) throws JsonProcessingException {
        final var event = objectMapper.readValue(requestJson, new TypeReference<OffenderEvent>() {
        });
        return Optional.ofNullable(event).orElseThrow(() -> new RuntimeException(String.format("Missing offender event from %s", requestJson)));
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Message {
        private final String message;
        private final MessageAttributes messageAttributes;

        @JsonCreator
        public Message(@JsonProperty("Message") final String message, @JsonProperty("MessageAttributes") final MessageAttributes messageAttributes) {
            this.message = message;
            this.messageAttributes = messageAttributes;
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MessageAttributes {
        private final Attribute eventType;

        @JsonCreator
        public MessageAttributes(@JsonProperty("eventType") final Attribute eventType) {
            this.eventType = eventType;
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Attribute {
        private final String value;

        @JsonCreator
        public Attribute(@JsonProperty("Value") final String value) {
            this.value = value;
        }
    }
}
