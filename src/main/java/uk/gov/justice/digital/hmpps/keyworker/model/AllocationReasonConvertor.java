package uk.gov.justice.digital.hmpps.keyworker.model;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

// JPA Attribute Convertor
@Converter
public class AllocationReasonConvertor implements AttributeConverter<AllocationReason,String> {
    @Override
    public String convertToDatabaseColumn(AllocationReason attribute) {
        return (attribute != null) ? attribute.getReasonCode() : null;
    }

    @Override
    public AllocationReason convertToEntityAttribute(String dbData) {
        return AllocationReason.get(dbData);
    }
}
