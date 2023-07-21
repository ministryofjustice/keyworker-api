package uk.gov.justice.digital.hmpps.keyworker.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// JPA Attribute Convertor
@Converter
public class KeyworkerStatusConvertor implements AttributeConverter<KeyworkerStatus,String> {
    @Override
    public String convertToDatabaseColumn(final KeyworkerStatus attribute) {
        return (attribute != null) ? attribute.getStatusCode() : null;
    }

    @Override
    public KeyworkerStatus convertToEntityAttribute(final String dbData) {
        return KeyworkerStatus.get(dbData);
    }
}
