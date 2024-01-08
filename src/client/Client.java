package client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import messages.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map.Entry;

import static colors.ANSIColors.*;
import static util.Util.*;

public class Client {

    private Socket socket;
    private BufferedReader consoleReader;
    private PrintWriter out;
    private BufferedReader in;
    private ObjectMapper mapper;
    private String gameLobby = "";
    private String LFT_sender; // LFT stands for Latest File Transfer

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
        //todo: validation of command and file existing
        String filename = params[0];
        String receiver = params[1];
        out.println("SEND_FILE " + mapper.writeValueAsString(new FileTransferRequest(filename, receiver, "")));
    }

    private void accept() throws JsonProcessingException {
        out.println("TRANSFER_RESPONSE " + mapper.writeValueAsString(new FileTransferResponse(true, LFT_sender, "")));
    }

    private void reject() throws JsonProcessingException {
        out.println("TRANSFER_RESPONSE " + mapper.writeValueAsString(new FileTransferResponse(false, LFT_sender, "")));
    }

    private void handleResponseMessages(Response<?> response) throws JsonProcessingException {
        // todo: rewrite to a HashMap in "util" package
        switch (response.status()) {
            case 800 -> successfulMessagesHandler(response);
            // 810-819 reserved for login codes
            case 810 -> System.err.println("You can't log in twice");
            case 811 -> System.err.println("Invalid username format");
            // 820-829 reserved for message related codes
            case 820 -> System.err.println("Can't send messages anonymously");
            case 821 -> System.err.println("Cannot find user with name " + response.content());
            case 822 -> System.err.println("Cannot send a private message to yourself");
            // 830-839 reserved for heartbeat codes
            // case 830 -> System.err.println("Pong without ping"); TODO: THE PING/PONG ERROR
            // 840-849 reserved for user list related errors
            case 840 -> System.err.println("To view the list of users you need to log in");
            // 850-859 reserved for game related errors
            case 850 -> System.err.println("You need to log in before starting a game");
            case 851 ->
                    System.err.println("You can't create a lobby with name " + response.content() + ". Use only latin letters and numbers");
            case 852 -> System.err.println("Can't join a game without logging in");
            case 853 -> System.err.println("You are not in a game to send your guesses to");
            case 854 -> System.err.println("You can't send a guess for a game when not logged in");
            case 855 -> System.err.println("Invalid guess provided. Only numbers are acceptable");
            case 856 ->
                    System.err.println("Your guess in not in the games range: " + response.content()); // response.content() is the game range
            case 857 -> System.err.println("Can't join two games at the same time");
            case 858 -> System.err.println("Game" + response.content() + "not found");
            // 860-869 reserved for file transfer related errors
            case 860 -> System.err.println();
            // 700-710 reserved for disconnection reasons
            case 700 -> System.err.println("Pong timeout");
            case 701 -> System.err.println("Unterminated message");
            // 710-720 reserved for general codes
            case 710 -> System.err.println("You are not logged in");
            case 711 -> System.err.println("User " + response.content() + " was not found");

            default -> System.err.println("Server responded with an unknown status code");
        }
    }

    private String findDisconnectionReason(String code) {
        // this should go to the hashmap too
        switch (code) {
            case "700" -> {
                return "Bye bye!";
            }
            case "701" -> {
                return "Disconnected due to inactivity";
            }
        }
        return "Unknown code";
    }

    private void successfulMessagesHandler(Response<?> response) throws JsonProcessingException {
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
                FileTransferResponse ftr = mapper.readValue((String) response.content(), FileTransferResponse.class);
                if (ftr.status()) {
                    coloredPrint(ANSI_GREEN, ftr.sender() + " has ACCEPTED your file transfer inquiry! Preparing transmission...");
                    initFileTransfer();
                } else
                    coloredPrint(ANSI_GREEN, ftr.sender() + " has REJECTED your file transfer inquiry.");
            }
            default -> System.out.println("OK status received. Unknown destination of the response: " + response.to());
        }
    }

    private void initFileTransfer() {
    }

    private void handleServerMessage(String message) throws IOException {
        String[] messageParts = message.split(" ", 2);
        String type = messageParts[0];
        String json = messageParts.length == 2 ? messageParts[1] : "";

        if (!type.equalsIgnoreCase("ping"))
            System.out.println(message);
        //todo: DEBUGGING - the accepted/rejected response is not received by the sender
        // todo: EVEN FUCKING SENDING STOPPED WORKING??????????????

        switch (type) {
            case "RESPONSE" -> {
                JavaType javaType = mapper.getTypeFactory().constructParametricType(Response.class, Object.class);
                Response<Object> response = mapper.readValue(json, javaType);
                handleResponseMessages(response);
            }
            case "DISCONNECTED" -> {
                SystemMessage response = mapper.readValue(json, SystemMessage.class);
                String disconnectionReason = findDisconnectionReason(response.message());
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
                LFT_sender = ftm.sender();
            }
            case "PARSE_ERROR" -> coloredPrint(ANSI_MAGENTA, "Parse error occurred processing your message");
            default -> coloredPrint(ANSI_MAGENTA, "Unknown message type or command from the server");
        }
    }
}