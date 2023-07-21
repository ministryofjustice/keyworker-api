package uk.gov.justice.digital.hmpps.keyworker.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// JPA Attribute Convertor
@Converter
public class AllocationReasonConvertor implements AttributeConverter<AllocationReason,String> {
    @Override
    public String convertToDatabaseColumn(final AllocationReason attribute) {
        return (attribute != null) ? attribute.getReasonCode() : null;
    }

    @Override
    public AllocationReason convertToEntityAttribute(final String dbData) {
        return AllocationReason.get(dbData);
    }
}
