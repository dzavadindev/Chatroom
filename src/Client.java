import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;

public class Client {

    private Socket socket;
    private BufferedReader consoleReader;
    private PrintWriter out;
    private BufferedReader in;
    private ObjectMapper mapper;
    public Client(String address, int port) {
        try {
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
                    if (serverMessage.equals("PING")) { // ping pong :D
                        out.println("PONG");
                        continue;
                    }
                    System.out.println("Server: " + serverMessage); // show the message from the server
                }
            } catch (IOException e) {
                System.err.println("Error in receiving message: " + e.getMessage());
            }
        }
    }

    private void menu() throws IOException {
        String option = consoleReader.readLine();
        String[] parts = option.split(" ");
        switch (parts[0]) {
            case "!help" -> help();
            case "!login" -> login(parts[1]);
            case "!send" -> send(option.replace("!send ", ""));
            case "!leave" -> leave();
            default -> System.out.println("Unknown operation");
        }
    }

    private void help() {
        System.out.println("!login <username> - logs you into the chat");
    }

    private void leave() {
        out.println("BYE");
    }

    private void send(String message) {
        out.println("BROADCAST_REQ {\"message\":\"" + message + "\"}");
    }

    private void login(String username) {
        out.println("LOGIN {\"username\":\"" + username + "\"}");
    }
}
