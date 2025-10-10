package uk.gov.justice.digital.hmpps.keyworker.domain

import com.fasterxml.jackson.annotation.JsonValue
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

enum class AllocationType(
  @JsonValue val code: String,
) {
  AUTO("A"),
  MANUAL("M"),
  ;

  companion object {
    fun getByCode(code: String): AllocationType? = entries.firstOrNull { it.code == code }
  }
}

@Converter
class AllocationTypeConvertor : AttributeConverter<AllocationType, String> {
  override fun convertToDatabaseColumn(attribute: AllocationType): String = attribute.code

  override fun convertToEntityAttribute(dbData: String): AllocationType = requireNotNull(AllocationType.getByCode(dbData))
}
