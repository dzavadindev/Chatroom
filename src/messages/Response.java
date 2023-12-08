package messages;

public record Response<T>(T content, int status, String to) {

    @Override
    public String toString() {
        return "Response to " + to + "; status: " + status + "; content: " + content;
    }
}
