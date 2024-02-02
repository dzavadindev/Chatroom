package util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exceptions.InputArgumentMismatchException;
import messages.TextMessage;

import static colors.ANSIColors.ANSI_RED;
import static colors.ANSIColors.coloredPrint;

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

    public static TextMessage textMessageFromCommand(String data) throws InputArgumentMismatchException {
        String[] tuple = data.split(" ", 2);
        if (tuple.length < 2 || tuple.length > 3) {
            coloredPrint(ANSI_RED, "Provide both username and the message");
            throw new InputArgumentMismatchException(tuple.length);
        }
        String receiver = tuple[0].trim();
        String message = tuple[1].trim();
        return new TextMessage(receiver, message);
    }

}
