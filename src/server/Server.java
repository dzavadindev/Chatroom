package server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import messages.BroadcastMessage;
import messages.Response;
import messages.SystemMessage;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Server {
    private final ObjectMapper mapper;
    private final Set<Connection> users = new HashSet<>();
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
                Connection connection = new Connection(socket);
                new Thread(connection).start();
            }
        } catch (Exception err) {
            throw new RuntimeException(err);
        }
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
                out.println("GREET " + mapper.writeValueAsString(new SystemMessage(greeting)));
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
                    case "PRIVATE" -> handlePrivate(json);
                    case "LIST" -> handleList();
                    case "LEAVE" -> disconnect(700);
                    default -> out.println("UNKNOWN_ACTION");
                }
            } catch (JsonProcessingException e) {
                out.println("PARSE_ERROR");
            }
        }

        private void handleList() throws JsonProcessingException {
            List<String> online = users.stream()
                    .filter(user -> !this.username.equals(user.username))
                    .map(user -> user.username)
                    .collect(Collectors.toList());
            sendResponse("LIST", 800, online);
        }

        private void handlePrivate(String json) throws JsonProcessingException {
            if (this.username.isBlank()) {
                sendResponse("BROADCAST", 820, "ERROR");
                return;
            }
            String message = getPropertyFromJson(json, "message");
            String receiverName = getPropertyFromJson(json, "username");
            if (this.username.equals(receiverName)) {
                sendResponse("BROADCAST", 822, "ERROR");
                return;
            }
            Connection receiver = users.stream()
                    .filter(user -> user.username.equals(receiverName))
                    .findAny()
                    .orElse(null);
            if (receiver == null) {
                sendResponse("BROADCAST", 821, receiverName);
                return;
            }
            receiver.out.println("PRIVATE " + mapper.writeValueAsString(new BroadcastMessage(this.username, message)));
        }

        private void handleBroadcast(String json) throws JsonProcessingException {
            if (username.isBlank()) {
                sendResponse("BROADCAST", 820, "ERROR");
                return;
            }
            String message = getPropertyFromJson(json, "message");
            users.stream()
                    .filter(user -> !user.username.equals(this.username))
                    .forEach(user -> {
                        try {
                            user.out.println("BROADCAST " + mapper.writeValueAsString(new BroadcastMessage(this.username, message)));
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    });
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
                users.forEach(user -> {
                    try {
                        user.out.println("ARRIVED " + mapper.writeValueAsString(new SystemMessage(this.username)));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
                users.add(this);
            } else sendResponse("LOGIN", 811, "ERROR");
        }

        private void handleHeartbeat() {
            // TODO: is the PONG send without PING is an actual error code? It doesn't do anything???
            alive = true;
        }

        private void disconnect(int reason) throws IOException {
            out.println("DISCONNECTED " + mapper.writeValueAsString(new SystemMessage(String.valueOf(reason))));
            users.remove(this);
            users.forEach(user -> {
                try {
                    user.out.println("LEFT " + mapper.writeValueAsString(new SystemMessage(this.username)));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
            allocatedSocket.close();
        }

        private void sendResponse(String to, int status, Object content) throws JsonProcessingException {
            out.println("RESPONSE " + mapper.writeValueAsString(new Response<>(content, status, to)));
        }

        private String getPropertyFromJson(String json, String property) throws JsonProcessingException {
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

// actual TODO: guessing game