package server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import messages.Response;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {

    private final ObjectMapper mapper;
    private long users = 0;
    private final String greeting = "Welcome to the chatroom! Please login to start chatting!";

    public Server(int SERVER_PORT) {
        try {
            this.mapper = new ObjectMapper();
            startServer(SERVER_PORT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        new Server(1337);
    }

    private void startServer(int port) throws IOException {
        System.out.println("Server now running on port " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new Connection(socket)).start();
            }
        } catch (Exception err) {
            throw new RuntimeException(err);
        }
    }

    public synchronized void addUser() {
        users++;
    }

    public synchronized void removeUser() {
        users--;
    }

    private class Connection implements Runnable {
        private final Socket allocatedSocket;
        private final PrintWriter out;
        private final BufferedReader in;
        private boolean alive = true;
        private String username = "";
        private final long HEARTBEAT_REACTION = 1000 * 5;
        private final long HEARTBEAT_PERIOD = 1000 * 30;

        public Connection(Socket allocatedSocket) throws IOException {
            this.allocatedSocket = allocatedSocket;
            this.out = new PrintWriter(allocatedSocket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(allocatedSocket.getInputStream()));
        }

        @Override
        public void run() {
            try {
                System.out.println("New connection to the server established");
                out.println("GREET {\"message\": \"" + greeting + "\"}");
                addUser();
                while (!allocatedSocket.isClosed()) {
                    messageHandler(in.readLine());
                }
            } catch (IOException | InterruptedException e) {
//                throw new RuntimeException(e);
                System.err.println(e.getMessage());
            }
        }

        private void messageHandler(String message) throws InterruptedException, IOException {
            String[] messageParts = message.split(" ", 2);
            String type = messageParts[0];
            String json = messageParts.length == 2 ? messageParts[1] : "";

            try {
                switch (type) {
                    case "PONG" -> handleHeartbeat();
                    case "LOGIN" -> handleLogin(json);
                    case "BROADCAST" -> handleBroadcast(json);
                    case "LEAVE" -> disconnect(700);
                    default -> out.println("UNKNOWN_ACTION");
                }
            } catch (JsonProcessingException e) {
                out.println("PARSE_ERROR");
            }
        }

        private void handleBroadcast(String json) throws JsonProcessingException {
            System.out.println(json);
            if (username.isBlank()) {
                sendResponse("BROADCAST", 820, "ERROR");
                return;
            }
            String message = getPropertyFromJson(json, "message");
            System.out.println("[" + this.username + "] : " + message);
        }

        private void handleLogin(String json) throws JsonProcessingException {
            if (!username.isBlank()) {
                sendResponse("LOGIN", 810, "ERROR");
                return;
            }
            String username = getPropertyFromJson(json, "username");
            Matcher matcher = Pattern.compile("^[a-zA-Z-_]{4,14}$").matcher(username);
            if (matcher.find()) {
                this.username = username;
                new Thread(new Heartbeat()).start();
                sendResponse("LOGIN", 800, "OK");
                // todo: make a set of Connections, filter to exclude the sender and do things
            } else sendResponse("LOGIN", 811, "ERROR");
        }

        private void handleHeartbeat() {
            alive = true;
        }

        private void disconnect(int reason) throws IOException {
            out.println("DISCONNECTED {\"message\": \"" + reason + "\"}");
            removeUser();
            username = "";
            allocatedSocket.close();
        }

        private void sendResponse(String to, int status, Object content) throws JsonProcessingException {
            out.println("RESPONSE " + mapper.writeValueAsString(new Response<>(content, status, to)));
        }

        private String getPropertyFromJson(String json, String property) throws JsonProcessingException {
            // can I enrich the exception with the "to"?
            JsonNode node = mapper.readTree(json);
            return node.path(property).asText();
        }

        private class Heartbeat implements Runnable {
            @Override
            public void run() {
                Timer timer = new Timer("Heartbeat");
                // Every X milliseconds execute a heartbeat.
                timer.scheduleAtFixedRate(new HeartbeatTask(), 0, HEARTBEAT_PERIOD);
            }

            private class HeartbeatTask extends TimerTask {
                @Override
                public void run() {
                    try {
                        alive = false;
                        out.println("PING");
                        Thread.sleep(HEARTBEAT_REACTION);
                        if (!alive) disconnect(701);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}

// actual TODO: private message, list of users, guessing game