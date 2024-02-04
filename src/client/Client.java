package client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import exceptions.InputArgumentMismatchException;
import messages.*;

import javax.crypto.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.security.*;
import java.util.*;

import static colors.ANSIColors.*;
import static util.Util.*;
import static util.Codes.codeToMessage;

public class Client {
    // --------------- tools ---------------
    private Socket socket;
    private BufferedReader consoleReader;
    private PrintWriter out;
    private BufferedReader in;
    private ObjectMapper mapper;
    // --------------- features ---------------
    private GuessingGameManager guessingGameManager;
    private SecureManager secureManager;
    private FileTransferManager fileTransferManager;
    // --------------- config ---------------

    private final static String SERVER_ADDRESS = "127.0.0.1";
    private final static int SERVER_PORT = 1337;

    public Client(String address, int port) {
        try {
            mapper = new ObjectMapper();
            socket = new Socket(address, port);
            System.out.println("Connected to the server");

            // This thingey is how I read from the client to send messages to the server
            consoleReader = new BufferedReader(new InputStreamReader(System.in));

            // With "out" I am able to use client's console to send messages to the server's output stream
            // "Flushing" is the process of, in my case, "sending" the message.
            // Auto-flush ensures that once a message is received in the stream its sent in without buffering
            // (one message sent per instance of out.print()).
            out = new PrintWriter(socket.getOutputStream(), true);
            // With "in" I am able to read the input stream (server messages) into the application currently running
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            guessingGameManager = new GuessingGameManager(out);
            fileTransferManager = new FileTransferManager(out, address);

            System.out.println("Type \"!help\" to receive the list of commands");
            startClient();
        } catch (IOException e) {
            System.err.println("Error connecting to the server: " + e.getMessage());
        }
    }

    private void startClient() {
        new Thread(new Sender()).start();
        new Thread(new Listener()).start();
    }

    public static void main(String[] args) {
        new Client(SERVER_ADDRESS, SERVER_PORT);
    }

    // ----------------------------   SENDER AND LISTENER   -----------------------------------------
    // --------------------------   TO AND FROM THE SERVER   ----------------------------------------

    private class Sender implements Runnable {
        @Override
        public void run() {
            try {
                while (!socket.isClosed()) {
                    menu();
                }
            } catch (IOException e) {
//                throw new RuntimeException(e);
                System.err.println("Error in sending message: " + e.getMessage());
            }
        }
    }

    private class Listener implements Runnable {
        @Override
        public void run() {
            try {
                String serverMessage;
                while ((serverMessage = in.readLine()) != null) { // if the input stream contains any data.
                    handleServerMessage(serverMessage);
                }
            } catch (SocketException se) {
                System.err.println("| ------------------------------------- |");
                System.err.println("| Connection with the server was closed |");
                System.err.println("| due to an internal error or shutdown  |");
                System.err.println("| Exiting...                            |");
                System.err.println("| ------------------------------------- |");
                System.exit(0);
            } catch (IOException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
//                throw new RuntimeException(e);
                System.err.println("Error in receiving message: " + e.getMessage());
            }
        }
    }

    // ------------------------------   COMMAND HANDLERS   -------------------------------------------

    private void menu() throws IOException {
        String option = consoleReader.readLine();

        String[] messageParts = option.split(" ", 2);
        String command = messageParts[0];
        String content = messageParts.length == 2 ? messageParts[1] : "";

        switch (command) {
            case "!help" -> help();
            case "!login" -> login(content);
            case "!send" -> send(content);
            case "!leave" -> leave();
            case "!direct" -> direct(content);
            case "!secure" -> secure(content);
            case "!list" -> list();
            case "!create" -> create(content);
            case "!join" -> join(content);
            case "!guess" -> guess(content);
            case "!file" -> file(content);
            case "!ls" -> showFiles();
            case "!accept" -> accept();
            case "!reject" -> reject();
            default -> System.out.println("Unknown operation");
        }
    }

    private void help() {
        System.out.println("### !login <username> - logs you into the chat");
        System.out.println("### !send <message> - sends a message to the chat");
        System.out.println("### !leave - logs you out of the chat");
        System.out.println("### !direct <username> <message> - sends a private message to a user");
        System.out.println("### !secure <username> <message> - send an encrypted message to another user");
        System.out.println("### !list - shows all the users that are currently online");
        System.out.println("### !create <lobby name> - create a lobby for guessing game");
        System.out.println("### !join <lobby name> - enter a number guessing game is one currently is active");
        System.out.println("### !guess <guess> - enter your guess for the number guessing game if you're in a game");
        System.out.println("### !ls - list the files that are available for file transfer (inside your exchange directory)");
        System.out.println("### !file <filename> <receiver> - send a file to the specified user");
        System.out.println("###### NOTE:");
        System.out.printf("###### The file you're planning to send must be in the \"%s\" directory.\n", fileTransferManager.getFileTransferDirectory());
        System.out.println("###### When specifying the file for transmission, include only the name and extension");
        System.out.println("### !accept/reject - accept or decline the latest file transfer offered");
    }

    private void login(String username) {
        out.println("LOGIN " + wrapInJson("username", username.trim()));
        this.secureManager = new SecureManager(out);
    }

    private void list() {
        out.println("LIST");
    }

    private void send(String message) throws JsonProcessingException {
        if (!message.isBlank()) {
            out.println("BROADCAST " + mapper.writeValueAsString(new TextMessage("", message.trim())));
        } else System.out.println("Invalid message format");
    }

    private void direct(String data) throws JsonProcessingException {
        try {
            out.println("PRIVATE " + mapper.writeValueAsString(textMessageFromCommand(data)));
        } catch (InputArgumentMismatchException e) {
            System.err.println(e.getMessage());
        }
    }

    private void create(String lobby) {
        guessingGameManager.handleCreate(lobby);
    }

    private void join(String lobby) {
        guessingGameManager.handleJoin(lobby);
    }

    private void guess(String guess) {
        guessingGameManager.handleGuess(guess);
    }

    private void file(String content) {
        fileTransferManager.handleSendFile(content);
    }

    private void showFiles() {
        fileTransferManager.handleShowFilesInDirectory();
    }

    private void accept() {
        fileTransferManager.handleAccept();
    }

    private void reject() {
        fileTransferManager.handleReject();
    }

    private void secure(String data) {
        secureManager.handleSendSecure(data);
    }

    private void leave() {
        try {
            out.println("LEAVE");
            socket.close();
            System.out.println("Bye bye!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void disconnect(String json) throws IOException {
        String disconnectionReason = "Unknown cause";
        try {
            SystemMessage response = mapper.readValue(json, SystemMessage.class);
            disconnectionReason = codeToMessage.get(Integer.parseInt(response.message()));
        } catch (NumberFormatException ignored) {
        }
        System.out.println("You were disconnected from the server. " + disconnectionReason);
        socket.close();
    }

    // --------------------------   RECEIVED MESSAGE HANDLER   ---------------------------------------

    private void handleServerMessage(String message) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        String[] messageParts = message.split(" ", 2);
        String type = messageParts[0];
        String json = messageParts.length == 2 ? messageParts[1] : "";

        switch (type) {
            case "RESPONSE" -> {
                JavaType javaType = mapper.getTypeFactory().constructParametricType(Response.class, Object.class);
                Response<Object> response = mapper.readValue(json, javaType);
                handleResponseMessages(response);
            }
            case "DISCONNECTED" -> disconnect(json);
            case "GREET" -> {
                SystemMessage response = mapper.readValue(json, SystemMessage.class);
                System.out.println(response.message());
            }
            case "ARRIVED" -> {
                SystemMessage response = mapper.readValue(json, SystemMessage.class);
                System.out.println(response.message() + " has joined!");
            }
            case "LEFT" -> {
                SystemMessage response = mapper.readValue(json, SystemMessage.class);
                System.out.println(response.message() + " has left the chatroom");
            }
            case "BROADCAST" -> {
                TextMessage response = mapper.readValue(json, TextMessage.class);
                System.out.println("[" + response.username() + "] : " + response.message());
            }
            case "PRIVATE" -> {
                TextMessage response = mapper.readValue(json, TextMessage.class);
                coloredPrint(ANSI_CYAN, "[" + response.username() + "] : " + response.message());
            }
            case "PING" -> out.println("PONG");
            case "GAME_LAUNCHED" -> guessingGameManager.handleReceiveLaunched(json);
            case "GAME_START" -> guessingGameManager.handleReceiveStart(json);
            case "GAME_GUESSED" -> guessingGameManager.handleReceiveGuessed(json);
            case "GAME_END" -> guessingGameManager.handleReceiveEnd(json);
            case "GAME_FAIL" -> guessingGameManager.handleReceiveFailed(json);
            case "SECURE" -> secureManager.handleReceiveSecure(json);
            case "PUBLIC_KEY_REQ" -> secureManager.handleReceivePublicKeyReq(json);
            case "PUBLIC_KEY_RES" -> secureManager.handleReceivePublicKeyRes(json);
            case "SESSION_KEY" -> secureManager.handleReceiveSessionKey(json);
            case "SECURE_READY" -> secureManager.handleReceiveSecureReady(json);
            case "TRANSFER_REQUEST" -> fileTransferManager.handleReceiveTransferRequest(json);
            case "PARSE_ERROR" -> coloredPrint(ANSI_MAGENTA, "Parse error occurred processing your message");
            default -> coloredPrint(ANSI_MAGENTA, "Unknown message type or command from the server");
        }
    }

    private void handleResponseMessages(Response<?> response) {
        if (response.status() == 800) {
            successfulMessagesHandler(response);
            return;
        }
        String message = codeToMessage.get(response.status());
        if (message == null) {
            System.err.println("Server responded with an unknown status code");
            return;
        }

        try {
            if (response.status() == 711) {
                NotFound notFound = mapper.readValue((String) response.content(), NotFound.class);
                message = String.format(message, notFound.resource(), notFound.content());
                coloredPrint(ANSI_RED, message);
                return;
            }

            message = String.format(message, response.content());
            coloredPrint(ANSI_RED, message);
        } catch (MissingFormatArgumentException e) {
            coloredPrint(ANSI_RED, message);
        } catch (JsonProcessingException e) {
            coloredPrint(ANSI_RED, "Couldn't deserialize JSON from the response");
        }
    }

    private void successfulMessagesHandler(Response<?> response) {
        switch (response.to()) {
            /* SUCCESSFUL RESPONSES SOMETIMES TAKE A WILDCARD GENERIC TYPE, HOWEVER THE PROTOCOL SPECIFIES THE EXPECTED CONTENT TYPE

             the response.content() that is received at this point is an Object instance
             thus it can be anything. For that reason, casting is required when receiving
             any input. This is working on the fact that the client knows what type of
             command was received

             case "COMMAND" -> System.out.println(((ArrayList<String>) response.content()) */
            // general
            case "LOGIN" -> coloredPrint(ANSI_CYAN, "Logged in successfully!");
            case "LIST" -> System.out.println(response.content());
            case "BROADCAST" -> { /*NOOP*/ } // There is nothing useful to signify if received OK from server at this point.
            case "PRIVATE" -> { /*NOOP*/ } // Maybe "received" could have been a thing, but I don't want to amke the CLI messy
            // game
            case "GAME_LAUNCH" -> guessingGameManager.handleSuccessfulLaunch();
            case "GAME_JOIN" -> guessingGameManager.handleSuccessfulJoin(response);
            case "GAME_GUESS" -> guessingGameManager.handleSuccessfulGuess(response);
            // file
            case "TRANSFER_RESPONSE" -> coloredPrint(ANSI_GREEN, "Your response was sent to the sender");
            case "SEND_FILE" -> fileTransferManager.handleResponseSendFile(response);
            // unknown
            default -> System.out.println("OK status received. Unknown destination of the response: " + response.to());
        }
    }

}