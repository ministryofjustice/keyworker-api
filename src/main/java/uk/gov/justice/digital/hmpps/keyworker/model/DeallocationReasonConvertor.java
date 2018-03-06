package uk.gov.justice.digital.hmpps.keyworker.model;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

// JPA Attribute Convertor
@Converter
public class DeallocationReasonConvertor implements AttributeConverter<DeallocationReason,String> {
    @Override
    public String convertToDatabaseColumn(DeallocationReason attribute) {
        return (attribute != null) ? attribute.getReasonCode() : null;
    }

    @Override
    public DeallocationReason convertToEntityAttribute(String dbData) {
        return DeallocationReason.get(dbData);
    }
}
