package messages;

import java.util.UUID;

public record FileTransferRequest(String filename, String receiver, String sender, UUID sessionId, String checksum) {
}

// when compiling a response/request for the file transfer, you always specify the eventual receiver of a message as "receiver"
// and the initial sender of a message as "sender"