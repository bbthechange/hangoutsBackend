package com.bbthechange.inviter.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Polymorphic deserializer for {@link PollOptionInput}: accepts either a plain string
 * (legacy LOCATION/DESCRIPTION shape) or an object with {@code text} / {@code timeInput}.
 */
public class PollOptionInputDeserializer extends JsonDeserializer<PollOptionInput> {

    @Override
    public PollOptionInput deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_STRING) {
            return new PollOptionInput(p.getText());
        }
        if (token == JsonToken.START_OBJECT) {
            // Delegate object shape to the default deserializer for PollOptionInput.
            return p.readValueAs(PollOptionInputObject.class).toInput();
        }
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        throw com.fasterxml.jackson.databind.JsonMappingException.from(p,
            "Expected string or object for poll option input");
    }

    /**
     * Internal POJO used so Jackson can deserialize the object form without re-entering
     * the custom deserializer.
     */
    public static class PollOptionInputObject {
        public String text;
        public TimeInfo timeInput;

        PollOptionInput toInput() {
            return new PollOptionInput(text, timeInput);
        }
    }
}
