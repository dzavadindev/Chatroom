package client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import messages.BroadcastMessage;
import messages.SystemMessage;
import messages.Response;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

import static colors.ANSIColors.*;

public class Client {

    private Socket socket;
    private BufferedReader consoleReader;
    private PrintWriter out;
    private BufferedReader in;
    private ObjectMapper mapper;

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
        content = content.replace("\"", "\\\"");

        switch (command) {
            case "!help" -> help();
            case "!login" -> login(content);
            case "!send" -> send(content);
            case "!leave" -> leave();
            case "!direct" -> direct(content);
            case "!list" -> list();
            default -> System.out.println("Unknown operation");
        }
    }

    private void help() {
        System.out.println("### !login <username> - logs you into the chat");
        System.out.println("### !send <message> - sends a message to the chat");
        System.out.println("### !leave - logs you out of the chat");
        System.out.println("### !direct <username> <message> - sends a private message to a user");
        System.out.println("### !list - shows all the users that are currently online");
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
        out.println("LOGIN {\"username\":\"" + username + "\"}");
    }

    private void handleResponseMessages(Response<?> response) {
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
            // 700-710 reserved for disconnection reasons
            case 700 -> System.err.println("Pong timeout");
            case 701 -> System.err.println("Unterminated message");
            default -> System.err.println("Server responded with an unknown status code");
        }
    }

    private String findDisconnectionReason(String code) {
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

    private void successfulMessagesHandler(Response<?> response) {
        // this method doesn't do anything yet, as the server doesn't make use of the
        // possibility of any data type being transferred with the Response<> message
        switch (response.to()) {
            case "LOGIN" -> System.out.println("Logged in successfully!");
//            case "LIST" -> System.out.println(((ArrayList<String>) response.content()).forEach(user -> System.out.println(user))); // TODO: Cast the things to preform specific actions on them
            case "LIST" -> System.out.println(response.content());
            default -> System.out.println("OK status received. Unknown destination of the response: " + response.to());
        }
    }

    private void handleServerMessage(String message) throws IOException {
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
            case "PARSE_ERROR" -> coloredPrint(ANSI_MAGENTA, "Parse error occurred processing your message");
            case "UNKNOWN_COMMAND" -> coloredPrint(ANSI_MAGENTA, "The send command is unknown to the server");
            default -> coloredPrint(ANSI_MAGENTA, "Unknown message type from the server");
        }
    }
}