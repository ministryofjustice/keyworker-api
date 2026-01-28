package uk.gov.justice.digital.hmpps.keyworker.config

import org.openapitools.jackson.nullable.JsonNullable
import org.springframework.stereotype.Component
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.BeanProperty
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JavaType
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdSerializer

@Component
class JsonNullableJackson3Module : SimpleModule() {
  init {
    addSerializer(JsonNullable::class.java, JsonNullableSerializer())
    addDeserializer(JsonNullable::class.java, JsonNullableDeserializer())
  }
}

class JsonNullableSerializer : StdSerializer<JsonNullable<*>>(JsonNullable::class.java) {
  override fun serialize(
    value: JsonNullable<*>?,
    gen: JsonGenerator,
    ctxt: SerializationContext,
  ) {
    if (value == null || !value.isPresent) {
      return
    }
    val v = value.get()
    if (v == null) {
      gen.writeNull()
    } else {
      ctxt.writeValue(gen, v)
    }
  }

  override fun isEmpty(
    ctxt: SerializationContext,
    value: JsonNullable<*>?,
  ): Boolean = value == null || !value.isPresent
}

class JsonNullableDeserializer(
  private val valueType: JavaType? = null,
) : StdDeserializer<JsonNullable<*>>(JsonNullable::class.java) {
  override fun createContextual(
    ctxt: DeserializationContext,
    property: BeanProperty?,
  ): StdDeserializer<JsonNullable<*>> {
    val wrapperType = property?.type ?: ctxt.contextualType
    val valueType = wrapperType?.containedType(0)
    return JsonNullableDeserializer(valueType)
  }

  override fun deserialize(
    p: JsonParser,
    ctxt: DeserializationContext,
  ): JsonNullable<*> {
    val value =
      if (valueType != null) {
        ctxt.readValue(p, valueType)
      } else {
        p.readValueAs(Any::class.java)
      }
    return JsonNullable.of(value)
  }

  override fun getNullValue(ctxt: DeserializationContext): JsonNullable<*> = JsonNullable.of<Any>(null)
}
