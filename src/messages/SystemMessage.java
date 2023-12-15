package messages;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SystemMessage(String message) {
    public SystemMessage(@JsonProperty("message") String message) {
        this.message = message;
    }
}

