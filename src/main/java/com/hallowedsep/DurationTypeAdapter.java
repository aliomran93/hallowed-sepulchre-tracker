package com.hallowedsep;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.Duration;

public class DurationTypeAdapter implements JsonSerializer<Duration>, JsonDeserializer<Duration>
{
	@Override
	public JsonElement serialize(Duration src, Type typeOfSrc, JsonSerializationContext context)
	{
		return new JsonPrimitive(src.toMillis());
	}
	
	@Override
	public Duration deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
	{
		return Duration.ofMillis(json.getAsLong());
	}
}
