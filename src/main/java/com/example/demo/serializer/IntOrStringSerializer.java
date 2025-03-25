package com.example.demo.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.kubernetes.client.custom.IntOrString;

import java.io.IOException;

public class IntOrStringSerializer extends JsonSerializer<IntOrString> {
    @Override
    public void serialize(IntOrString value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value.isInteger()) {
            gen.writeNumber(value.getIntValue());
        } else {
            gen.writeString(value.getStrValue());
        }
    }
}