package uk.gov.justice.digital.hmpps.keyworker.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;


// JPA Attribute Convertor
@Converter
public class DeallocationReasonConvertor implements AttributeConverter<DeallocationReason,String> {
    @Override
    public String convertToDatabaseColumn(final DeallocationReason attribute) {
        return (attribute != null) ? attribute.getReasonCode() : null;
    }

    @Override
    public DeallocationReason convertToEntityAttribute(final String dbData) {
        return DeallocationReason.get(dbData);
    }
}
