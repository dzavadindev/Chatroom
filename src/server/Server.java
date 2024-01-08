package server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import exceptions.UserNotFoundException;
import features.GuessingGame;
import messages.*;

import java.io.*;
import java.util.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static util.Util.*;

public class Server {
    private final ObjectMapper mapper;
    private final String LOBBY_NAME_REGEX = "^[a-zA-Z0-9-_]+$";
    private final String USER_NAME_REGEX = "^[a-zA-Z0-9-_]{3,14}$";
    private final Set<Connection> users = new HashSet<>();
    private final ConcurrentHashMap<String, GuessingGame> activeGames = new ConcurrentHashMap<>();
    private final int GAME_UPPER_BOUND = 50, GAME_LOWER_BOUND = 1;
    private final String greeting = "Welcome to the chatroom! Please login to start chatting!";

    public Server(int SERVER_PORT) {
        this.mapper = new ObjectMapper();
        startServer(SERVER_PORT);
    }

    public static void main(String[] args) {
        new Server(1337);
    }

    // -----------------------------------   CONNECTION HANDLING   ------------------------------------------------

    private void startServer(int port) {
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

    // -----------------------------------   CONNECTION   ------------------------------------------------

    public class Connection implements Runnable {
        private final Socket allocatedSocket;
        private final PrintWriter out;
        private final BufferedReader in;
        private boolean alive = true, hasLoggedIn = false;
        private String username = "";
        private final long HEARTBEAT_REACTION = 1000 * 5;
        private final long HEARTBEAT_PERIOD = 1000 * 30;

        public Connection(Socket allocatedSocket) throws IOException {
            this.allocatedSocket = allocatedSocket;
            this.out = new PrintWriter(allocatedSocket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(allocatedSocket.getInputStream()));
        }

        // -----------------------------------   MESSAGE HANDLING   ------------------------------------------------

        @Override
        public void run() {
            try {
                System.out.println("New connection to the server established");
                out.println("GREET " + mapper.writeValueAsString(new SystemMessage(greeting)));
                while (!allocatedSocket.isClosed()) {
                    messageHandler(in.readLine());
                }
            } catch (IOException e) {
//                throw new RuntimeException(e);
                System.err.println(e.getMessage());
            }
        }

        private void messageHandler(String message) throws IOException {
//            System.out.println(message);
            String[] messageParts = message.split(" ", 2);
            String type = messageParts[0];
            String json = messageParts.length == 2 ? messageParts[1] : "";

            if (type.contains("GAME")) {
                if (type.equalsIgnoreCase("GAME_LAUNCH")) {
                    handleGameLaunch(json);
                    return;
                }
                String lobbyName = getPropertyFromJson(json, "lobby");
                GuessingGame game = activeGames.get(lobbyName);
                if (game != null) {
                    switch (type) {
                        case "GAME_JOIN" -> handleGameJoin(game, json);
                        case "GAME_GUESS" -> handleGameGuess(game, json);
                    }
                } else sendResponse(type, 858, lobbyName);
                return;
            }

            try {
                switch (type) {
                    case "PONG" -> handleHeartbeat();
                    case "LOGIN" -> handleLogin(json);
                    case "BROADCAST" -> handleBroadcast(json);
                    case "PRIVATE" -> handlePrivate(json);
                    case "LIST" -> handleList();
                    case "SEND_FILE" -> handleTransferRequest(json);
                    case "TRANSFER_RESPONSE" -> handleTransferResponse(json);
                    case "LEAVE" -> disconnect(700);
                    default -> out.println("UNKNOWN_ACTION");
                }
            } catch (JsonProcessingException e) {
                out.println("PARSE_ERROR");
            }
        }

        // -----------------------------------   HANDLERS   ------------------------------------------------

        private void handleTransferRequest(String json) throws JsonProcessingException {
            if (isLoggedIn()) return;

            String filename = getPropertyFromJson(json, "filename");
            String receiverName = getPropertyFromJson(json, "receiver");

            try {
                Connection receiver = findUserByUsername(receiverName);
                receiver.out.println("TRANSFER_REQUEST " + mapper.writeValueAsString(new FileTransferRequest(filename, receiverName, this.username)));
                sendResponse("SEND_FILE", 800, "OK");
            } catch (UserNotFoundException e) {
                System.err.println(e.getMessage());
                sendResponse("SEND_FILE", 711, username);
            }
        }

        private void handleTransferResponse(String json) throws JsonProcessingException {
            if (isLoggedIn()) return;

            boolean status = Boolean.parseBoolean(getPropertyFromJson(json, "status"));
            String senderName = getPropertyFromJson(json, "sender");

            // todo: OR an error message

            FileTransferResponse fts = new FileTransferResponse(status, senderName, this.username);
            String response = mapper.writeValueAsString(fts);
            System.out.println(fts + " - response object");
            sendResponse("SEND_FILE", 800, response, senderName);
            sendResponse("TRANSFER_RESPONSE", 800, "OK");
            // todo: initiate the file transfer (in Client when receiving "true" in contents)
        }

        private void handleGameLaunch(String json) throws JsonProcessingException {
            if (isLoggedIn()) return;

            String lobby = getPropertyFromJson(json, "lobby");
            if (!lobby.matches(LOBBY_NAME_REGEX)) {
                sendResponse("GAME_LAUNCH", 850, lobby);
                return;
            }
            GuessingGame newGame = new GuessingGame(lobby, GAME_LOWER_BOUND, GAME_UPPER_BOUND);
            newGame.addPlayerToGame(this);
            activeGames.put(lobby, newGame);
            sendResponse("GAME_LAUNCH", 800, "OK");
            users.stream().filter(user -> !user.username.equals(this.username)).forEach(user -> user.out.println("GAME_LAUNCHED " + wrapInJson("lobby", lobby)));
             /*
             Every game is a separate thread, handling messages that contain "GAME" in it
             messages with "GAME" in it need to be forwarded to the corresponding game (lobby in message).
             The forwarding is done by getting the list of games, and checking if the
             list of players contains the one player that send the request.

             DESIGN:
             Along the game containing the players make the user contain what game they are in,
             storing either "" or the name of the lobby this allows for reducing the computations
             on the server side.
             (going through every active game and check for the presence of a user in it)

             TODO create a sequence diagram for the flow of a game
             */
        }

        private void handleGameJoin(GuessingGame game, String json) throws JsonProcessingException {
            if (isLoggedIn()) return;
            game.addPlayerToGame(this);
            sendResponse("GAME_JOIN", 800, "OK");
        }

        private void handleGameGuess(GuessingGame game, String json) throws JsonProcessingException {
            if (isLoggedIn()) return;
            sendResponse("GAME_GUESS", 800, "OK");
        }

        private void handleList() throws JsonProcessingException {
            if (isLoggedIn()) return;
            List<String> online = users.stream().filter(user -> !this.username.equals(user.username)).map(user -> user.username).collect(Collectors.toList());
            sendResponse("LIST", 800, online);
        }

        private void handlePrivate(String json) throws JsonProcessingException {
            if (isLoggedIn()) return;

            String message = getPropertyFromJson(json, "message");
            String receiverName = getPropertyFromJson(json, "username");
            if (this.username.equals(receiverName)) {
                sendResponse("BROADCAST", 822, "ERROR");
                return;
            }

            try {
                Connection receiver = findUserByUsername(username);
                receiver.out.println("PRIVATE " + mapper.writeValueAsString(new BroadcastMessage(this.username, message)));
            } catch (UserNotFoundException e) {
                sendResponse("BROADCAST", 821, username);
            }
        }

        private void handleBroadcast(String json) throws JsonProcessingException {
            if (isLoggedIn()) return;

            String message = getPropertyFromJson(json, "message");
            users.stream().filter(user -> !user.username.equals(this.username)).forEach(user -> {
                try {
                    user.out.println("BROADCAST " + mapper.writeValueAsString(new BroadcastMessage(this.username, message)));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        private void handleLogin(String json) throws JsonProcessingException {
            if (!username.isBlank() && hasLoggedIn) {
                sendResponse("LOGIN", 810, "ERROR");
                return;
            }

            String username = getPropertyFromJson(json, "username");

            Connection userWithThisUsername = users.stream().filter(user -> user.username.equals(username)).findAny().orElse(null);

            if (userWithThisUsername != null) {
                sendResponse("LOGIN", 812, "ERROR");
                return;
            }

            if (username.matches(USER_NAME_REGEX)) {
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
                hasLoggedIn = !hasLoggedIn;
            } else sendResponse("LOGIN", 811, "ERROR");
        }

        private void handleHeartbeat() throws JsonProcessingException {
            // TODO: is the PONG send without PING is an actual error code? It doesn't do anything???
            if (alive) {
                sendResponse("PONG", 830, "ERROR");
                return;
            }
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

        // -----------------------------------   UTILS   ------------------------------------------------


        private void sendResponse(String to, int status, Object content) throws JsonProcessingException {
            out.println("RESPONSE " + mapper.writeValueAsString(new Response<>(content, status, to)));
        }

        private void sendResponse(String to, int status, Object content, String username) throws JsonProcessingException {
            try {
                Connection user = findUserByUsername(username);
                user.out.println("RESPONSE " + mapper.writeValueAsString(new Response<>(content, status, to)));
            } catch (UserNotFoundException e) {
                System.err.println("Internal error: " + e);
            }
        }

        private Connection findUserByUsername(String username) throws UserNotFoundException {
            Connection receiver = users.stream().filter(user -> user.username.equals(username)).findAny().orElse(null);
            if (receiver == null) throw new UserNotFoundException(username);
            return receiver;
        }

        private boolean isLoggedIn() throws JsonProcessingException {
            if (username.isBlank() && !hasLoggedIn) {
                sendResponse("LOGIN", 710, "ERROR"); // Its not a response TO login, but its universal so-
                return false;
            }
            return true;
        }

        // -----------------------------------   HEARTBEAT   ------------------------------------------------

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

        @Override
        public String toString() {
            return "Connection{" + "username='" + username + '\'' + '}';
        }
    }
}

// todo: change all "not logged in" in doc and code to one error code
// todo: change all "user not found" in doc and code to one error code
// File Transfer is a separate thread.
// Open a new port and create a new server socket on it, PURELY to handle file transfer.
// When creating a connection to the FileTransfer socket, create tags (mark in bytes S or R).
// To know who is the sender and receiver, use unique UUID for the transfer session (created in sender).
// To understand the flow, follow the diagram in the slides of w5 (Michel's diagram)
// Calculating checksums - hashing alogs: MD5, SHA1