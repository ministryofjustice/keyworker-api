package uk.gov.justice.digital.hmpps.keyworker.config

import com.google.gson.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Configuration
open class JsonConfig() {

    @Bean
    @Primary
    open fun gson() : Gson {
        return GsonBuilder().setPrettyPrinting()
                .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
                .create();
    }

    internal class LocalDateAdapter : JsonSerializer<LocalDate?>, JsonDeserializer<LocalDate> {
        override fun serialize(src: LocalDate?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(src?.format(DateTimeFormatter.ISO_LOCAL_DATE))
        }

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): LocalDate {
            return LocalDate.parse(json?.asJsonPrimitive?.asString);
        }
    }

    internal class LocalDateTimeAdapter : JsonSerializer<LocalDateTime?>, JsonDeserializer<LocalDateTime> {
        override fun serialize(localDateTime: LocalDateTime?, srcType: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(localDateTime))
        }

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalDateTime {
            return LocalDateTime.parse(json.asString,
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.ENGLISH))
        }
    }
}