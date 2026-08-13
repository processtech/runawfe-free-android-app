package ru.runa.wfe.rest

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import ru.runa.wfe.restapi.model.Actor

class ActorDeserializer: StdDeserializer<Actor>(Actor::class.java) {
    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): Actor {
        return when (p?.currentToken()) {
            JsonToken.VALUE_STRING -> {
                Actor(name = p.text)
            }
            JsonToken.START_OBJECT -> {
                p.codec.readValue(p, Actor::class.java)
            }
            else -> throw JsonMappingException(p, "Error when try to deserialize ${Actor::class.simpleName}")
        }
    }
}