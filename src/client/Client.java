package client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import messages.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map.Entry;
import java.util.MissingFormatArgumentException;
import java.util.UUID;

import static colors.ANSIColors.*;
import static util.Util.*;
import static util.Codes.codeToMessage;

public class Client {

    private Socket socket;
    private BufferedReader consoleReader;
    private PrintWriter out;
    private BufferedReader in;
    private ObjectMapper mapper;
    private String gameLobby = "";
    private UUID latestFileTransferSessionId;
    private final String FILE_TRANSFER_DIRECTORY = "src/exchange/";

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
        new Client("127.0.0.1", 1337);
    }

    private class Sender implements Runnable {
        @Override
        public void run() {
            try {
                while (!socket.isClosed()) {
                    menu();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
//                System.err.println("Error in sending message: " + e.getMessage());
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
                System.err.println("Connection closed, exiting...");
                System.exit(0);
            } catch (IOException e) {
                throw new RuntimeException(e);
//                System.err.println("Error in receiving message: " + e.getMessage());
            }
        }
    }

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
            case "!list" -> list();
            case "!create" -> create(content);
            case "!join" -> join(content);
            case "!guess" -> guess(content);
            case "!file" -> file(content);
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
        System.out.println("### !list - shows all the users that are currently online");
        System.out.println("### !create <lobby name> - create a lobby for guessing game");
        System.out.println("### !join <lobby name> - enter a number guessing game is one currently is active");
        System.out.println("### !guess <guess> - enter your guess for the number guessing game if you're in a game");
        System.out.println("### !file <filename> <receiver> - send a file to the specified user");
        System.out.println("###### NOTE:");
        System.out.println("###### The file you're planning to send must be in the \"exchange\" directory.");
        System.out.println("###### When specifying the file for transmission, include only the name and extension");
        System.out.println("### !accept/reject - accept or decline the latest file transfer offered");
    }


    private void create(String lobby) {
        out.println("GAME_LAUNCH " + wrapInJson("lobby", lobby));
        gameLobby = lobby;
    }

    private void join(String lobby) {
        out.println("GAME_JOIN " + wrapInJson("lobby", lobby));
        gameLobby = lobby;
    }

    private void guess(String guess) throws JsonProcessingException {
        out.println("GAME_GUESS " + mapper.writeValueAsString(guess));
    }

    private void leave() {
        out.println("LEAVE");
        System.out.println("Bye bye!");
    }

    private void direct(String data) throws JsonProcessingException {
        String[] tuple = data.split(" ", 2);
        if (tuple.length < 2 || tuple.length > 3) {
            System.err.println("Provide both username and the message");
            return;
        }
        String receiver = tuple[0];
        String message = tuple[1];
        out.println("PRIVATE " + mapper.writeValueAsString(new BroadcastMessage(receiver, message)));
    }

    private void list() {
        out.println("LIST");
    }

    private void send(String message) throws JsonProcessingException {
        if (!message.isBlank()) {
            out.println("BROADCAST " + mapper.writeValueAsString(new BroadcastMessage("", message)));
        } else System.out.println("Invalid message format");
    }

    private void login(String username) {
        out.println("LOGIN " + wrapInJson("username", username));
    }

    private void file(String content) throws JsonProcessingException {
        String[] params = content.split(" ", 2);
        String filename = params[0];
        String receiver = params[1];
        File file = new File(FILE_TRANSFER_DIRECTORY + filename);
        if (!file.exists()) {
            coloredPrint(ANSI_MAGENTA, "FILE WITH NAME " + filename + " DOES NOT EXIST");
            return;
        }
        out.println("SEND_FILE " + mapper.writeValueAsString(new FileTransferRequest(filename, receiver, "", UUID.randomUUID())));
    }

    private void accept() throws JsonProcessingException {
        out.println("TRANSFER_RESPONSE " + mapper.writeValueAsString(new FileTransferResponse(true, "this.username", latestFileTransferSessionId)));
    }

    private void reject() throws JsonProcessingException {
        out.println("TRANSFER_RESPONSE " + mapper.writeValueAsString(new FileTransferResponse(false, "this.username", latestFileTransferSessionId)));
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
                System.out.println(response);
                //todo: I receive no this end not a record, but a LinkedHashMap,which I believe is a parent of the record class
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
            // the response.content() that is received at this point is an Object instance
            // thus it can be anything. For that reason, casting is required when receiving
            // any input. This is working on the fact that the client knows what type of
            // command was received

            // case "COMMAND" -> System.out.println(((ArrayList<String>) response.content())

            case "LOGIN" -> System.out.println("Logged in successfully!");
            case "LIST" -> System.out.println(response.content());
            case "GAME_LAUNCH" -> System.out.println("Game started!");
            case "TRANSFER_RESPONSE" -> coloredPrint(ANSI_GREEN, "Your response was sent to the sender");
            case "SEND_FILE" -> {
                if (response.content().equals("OK")) {
                    coloredPrint(ANSI_GREEN, "Request sent to the user");
                    return;
                }
                try {
                    FileTransferResponse ftr = mapper.readValue((String) response.content(), FileTransferResponse.class);

                    if (ftr.status())
                        coloredPrint(ANSI_GREEN, ftr.sender() + " has ACCEPTED your file transfer inquiry! Preparing transmission...");
                    else coloredPrint(ANSI_GREEN, ftr.sender() + " has REJECTED your file transfer inquiry.");
                } catch (JsonProcessingException e) {
                    System.err.println("Failed to parse the response to the file transfer response format");
                }
            }
            default -> System.out.println("OK status received. Unknown destination of the response: " + response.to());
        }
    }


    private void handleServerMessage(String message) throws IOException {
//        System.out.println(message);
        String[] messageParts = message.split(" ", 2);
        String type = messageParts[0];
        String json = messageParts.length == 2 ? messageParts[1] : "";

        switch (type) {
            case "RESPONSE" -> {
                JavaType javaType = mapper.getTypeFactory().constructParametricType(Response.class, Object.class);
                Response<Object> response = mapper.readValue(json, javaType);
                handleResponseMessages(response);
            }
            case "DISCONNECTED" -> {
                String disconnectionReason = "Unknown cause";
                try {
                    SystemMessage response = mapper.readValue(json, SystemMessage.class);
                    disconnectionReason = codeToMessage.get(Integer.parseInt(response.message()));
                } catch (NumberFormatException ignored) {
                }
                System.out.println("You were disconnected from the server. " + disconnectionReason);
                socket.close();
            }
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
                BroadcastMessage response = mapper.readValue(json, BroadcastMessage.class);
                System.out.println("[" + response.username() + "] : " + response.message());
            }
            case "PRIVATE" -> {
                BroadcastMessage response = mapper.readValue(json, BroadcastMessage.class);
                coloredPrint(ANSI_CYAN, "[" + response.username() + "] : " + response.message());
            }
            case "PING" -> out.println("PONG");
            case "GAME_LAUNCHED" ->
                    coloredPrint(ANSI_YELLOW, "A new game is brewing in lobby " + getPropertyFromJson(json, "lobby") + "! Join in!");
            case "GAME_START" ->
                    coloredPrint(ANSI_YELLOW, "The game in lobby \"" + getPropertyFromJson(json, "lobby") + "\" elapsed!");
            case "GAME_GUESSED" ->
                    coloredPrint(ANSI_YELLOW, getPropertyFromJson(json, "username") + " has guessed the number!");
            case "GAME_END" -> {
                Leaderboard leaderboard = mapper.readValue(json, Leaderboard.class);
                coloredPrint(ANSI_YELLOW, "Game in lobby " + leaderboard.lobby() + " has ended! Here is the scoreboard:");
                int index = 1;
                for (Entry<String, Integer> score : leaderboard.leaderboard().entrySet()) {
                    coloredPrint(ANSI_YELLOW, index + ".) " + score.getKey() + ": " + score.getValue() + "ms");
                }
            }
            case "TRANSFER_REQUEST" -> {
                FileTransferRequest ftm = mapper.readValue(json, FileTransferRequest.class);
                coloredPrint(ANSI_GREEN, "You are receiving an inquiry for file exchange from " + ftm.sender() + " (" + ftm.filename() + ")");
                latestFileTransferSessionId = ftm.sessionId();
            }
            case "PARSE_ERROR" -> coloredPrint(ANSI_MAGENTA, "Parse error occurred processing your message");
            default -> coloredPrint(ANSI_MAGENTA, "Unknown message type or command from the server");
        }
    }
}