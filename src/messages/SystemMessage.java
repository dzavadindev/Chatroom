package messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SystemMessage {
    private final String systemMessage;

    @JsonCreator
    public SystemMessage(
            @JsonProperty("msg") String msg,
            @JsonProperty("reason") String reason,
            @JsonProperty("code") String code) {
        if (msg != null) this.systemMessage = msg;
        else if (reason != null) this.systemMessage = reason;
        else if (code != null) this.systemMessage = code;
        else this.systemMessage = "";
    }

    public String systemMessage() {
        return systemMessage;
    }
}

