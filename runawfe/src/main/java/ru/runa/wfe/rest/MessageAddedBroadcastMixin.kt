package ru.runa.wfe.rest

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import ru.runa.wfe.restapi.model.Actor

abstract class MessageAddedBroadcastMixin {
    @get:JsonProperty("author")
    @get:JsonDeserialize(using = ActorDeserializer::class)
    abstract val author: Actor?

    @get:JsonProperty("createDate")
    @get:JsonFormat(pattern = "dd.MM.yyyy HH:mm")
    @get:JsonDeserialize(using = CreationDateDeserializer::class)
    abstract val createDate: java.time.OffsetDateTime?
}