package util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Util {
    // For one value JSON objects, as those are not converted to the right format with the mapper.
    // As in, a single String object passed into writeValueAsString will just become that string.
    public static <T> String wrapInJson(T key, T value) {
        return "{\"" + key + "\":\"" + value + "\"}";
    }

    // Gets a certain property from a JSON objects
    public static String getPropertyFromJson(String json, String property) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(json);
        return node.path(property).asText();
    }
}
