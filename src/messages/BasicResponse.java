package messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BasicResponse {

    private String status;
    private Integer code;

    @JsonCreator
    public BasicResponse(
            @JsonProperty("status") String status,
            @JsonProperty("code") Integer code
    ) {
        // Check if "msg" is in the json, if not look for "message" property
        this.status = status;
        // Defaulting code to 0 if not present
        this.code = (code != null) ? code : 0;
    }

    public String getReasonMessage() {
        String message = "No existing message matches code " + code;
        switch (code) {
            case 5000 -> message = "User already logged in";
            case 5001 -> message = "Username has an invalid format or length";
            case 5002 -> message = "User cannot log in twice";
            case 6000 -> message = "User is not logged in";
            case 7000 -> message = "Pong timeout";
            case 7001 -> message = "Unterminated message";
            case 8000 -> message = "Pong without ping";
        }
        return message;
    }

    public String status() {
        return status;
    }

    public Integer code() {
        return code;
    }

    public boolean isError () {
        return code != 0;
    }
}
