package messages;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SystemMessage(String systemMessage) {
    public SystemMessage(@JsonProperty("message") String systemMessage) {
        this.systemMessage = systemMessage;
    }
}

