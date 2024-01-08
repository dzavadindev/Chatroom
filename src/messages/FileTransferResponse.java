package messages;

public record FileTransferResponse(boolean status, String receiver, String sender) {
}

// when compiling a response/request for the file transfer, you always specify the eventual receiver of a message as "receiver"
// and the initial sender of a message as "sender"