package ru.runa.wfe.rest

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class CreationDateDeserializer: StdDeserializer<OffsetDateTime>(OffsetDateTime::class.java)  {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneOffset.UTC)

    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): OffsetDateTime {
        return try {
            LocalDateTime.parse(p?.text, dateFormatter)
                .atOffset(OffsetDateTime.now().offset)
        } catch (ex: DateTimeParseException) {
            try {
                OffsetDateTime.parse(p?.text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            } catch (ex: Exception) {
                throw JsonMappingException(p, "Parse error for date ${p?.text}: ${ex.message}")
            }
        }
    }
}