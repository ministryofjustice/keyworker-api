package uk.gov.justice.digital.hmpps.keyworker.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// JPA Attribute Convertor
@Converter
public class AllocationTypeConvertor implements AttributeConverter<AllocationType,String> {
    @Override
    public String convertToDatabaseColumn(final AllocationType attribute) {
        return (attribute != null) ? attribute.getTypeCode() : null;
    }

    @Override
    public AllocationType convertToEntityAttribute(final String dbData) {
        return AllocationType.get(dbData);
    }
}
