package server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import exceptions.UserNotFoundException;
import features.FileTransfer;
import features.GuessingGame;
import messages.*;

import java.io.*;
import java.util.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static util.Util.*;

public class Server {

    // -----------------------------------   SETUP   ------------------------------------------------

    private final ObjectMapper mapper;
    private final Set<Connection> users = new HashSet<>();
    private final ConcurrentHashMap<String, GuessingGame> activeGames = new ConcurrentHashMap<>();

    // -----------------------------------   CONSTANTS   ------------------------------------------------

    private final String LOBBY_NAME_REGEX = "^[a-zA-Z0-9-_]+$"; // Name validity
    private final String USER_NAME_REGEX = "^[a-zA-Z0-9-_]{3,14}$"; // Name validity
    private final int FILE_TRANSFER_PORT = 1338; // Port for file transfer thread
    private final long HEARTBEAT_REACTION = 2; // Heartbeat Executor is working with seconds
    private final long HEARTBEAT_PERIOD = 10; // Heartbeat Executor is working with seconds

    // -----------------------------------   CONFIG   ------------------------------------------------

    private final String greeting = "Welcome to the chatroom! Please login to start chatting!";

    public Server(int SERVER_PORT) {
        this.mapper = new ObjectMapper();
        startServer(SERVER_PORT);
    }

    public static void main(String[] args) {
        Server server = new Server(1337);
        Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHandler(server)));
    }

    // -----------------------------------   CONNECTION HANDLING   ------------------------------------------------

    private void startServer(int port) {
        System.out.println("Server now running on port " + port);
        // File transferring server section, on different port
        new Thread(new FileTransfer(FILE_TRANSFER_PORT)).start();
        // Handle connections for protocol messages
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                Connection connection = new Connection(socket);
                new Thread(connection).start();
            }
        } catch (IOException e) {
            System.err.println("A client has disconnected abruptly");
        }
    }

    // -----------------------------------   CONNECTION   ------------------------------------------------

    public class Connection implements Runnable {
        private final Socket allocatedSocket;
        private final PrintWriter out;
        private final BufferedReader in;
        private boolean alive = true, hasLoggedIn = false, inGame = false;
        public String username = "";
        private final List<FileTransferRequest> pendingFTRequests = new LinkedList<>(); // todo: maybe change to a map UUID:FileTransferRequest?

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
                    String message = in.readLine();
                    if (message == null) {
                        users.remove(this);
                        // todo: if closed abruptly or due to an exception, spams the LEFT message
                        users.forEach(user -> {
                            try {
                                user.out.println("LEFT " + mapper.writeValueAsString(new SystemMessage(this.username)));
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                    assert message != null;
                    messageHandler(message);
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
                        case "GAME_JOIN" -> handleGameJoin(game);
                        case "GAME_GUESS" -> handleGameGuess(game, json);
                    }
                } else {
                    String notFoundJson = mapper.writeValueAsString(new NotFound("game", lobbyName));
                    sendResponse(type, 711, notFoundJson);
                }
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
                    case "PUBLIC_KEY_REQ" -> handlePublicKeyReq(json);
                    case "PUBLIC_KEY_RES" -> handlePublicKeyRes(json);
                    case "SESSION_KEY" -> handleSessionKeyReq(json);
                    case "SECURE_READY" -> handleSecureReady(json);
                    case "SECURE" -> handleSecure(json);
                    case "LEAVE" -> disconnect(700);
                    default -> out.println("UNKNOWN_ACTION");
                }
            } catch (JsonProcessingException e) {
                out.println("PARSE_ERROR");
            }
        }

        // -----------------------------------   HANDLERS   ------------------------------------------------

        // REQUEST FROM THE INITIATOR TO THE SUBJECT.
        // SUBJECT: receiver.
        // INITIATOR: sender.
        private void handleTransferRequest(String json) throws JsonProcessingException {
            if (isNotLoggedIn()) return;

            String filename = getPropertyFromJson(json, "filename");
            String receiverName = getPropertyFromJson(json, "receiver");
            String checksum = getPropertyFromJson(json, "checksum");
            UUID sessionId = UUID.fromString(getPropertyFromJson(json, "sessionId"));

            if (receiverName.equalsIgnoreCase(this.username)) {
                sendResponse("SEND_FILE", 861, "ERROR");
                return;
            }

            try {
                Connection receiver = findUserByUsername(receiverName);
                FileTransferRequest request = new FileTransferRequest(filename, receiver.username, this.username, sessionId, checksum);
                receiver.out.println("TRANSFER_REQUEST " + mapper.writeValueAsString(request));
                receiver.addPendingFileTransferRequest(request);
                sendResponse("SEND_FILE", 800, "OK");
            } catch (UserNotFoundException e) {
                System.err.println(e.getMessage());
                String notFoundJson = mapper.writeValueAsString(new NotFound("user", receiverName));
                sendResponse("SEND_FILE", 711, notFoundJson);
            }
        }

        // RESPONSE FROM THE SUBJECT TO THE INITIATOR.
        // SUBJECT: sender.
        // INITIATOR: receiver.
        private void handleTransferResponse(String json) throws JsonProcessingException {
            if (isNotLoggedIn()) return;

            boolean status = Boolean.parseBoolean(getPropertyFromJson(json, "status"));
            UUID sessionId = UUID.fromString(getPropertyFromJson(json, "sessionId"));

            FileTransferRequest ftr = pendingFTRequests.stream()
                    .filter(req -> req.sessionId().equals(sessionId))
                    .findAny()
                    .orElse(null);

            if (ftr == null) {
                sendResponse("TRANSFER_RESPONSE", 860, "ERROR");
                return;
            }

            FileTransferResponse fts = new FileTransferResponse(status, this.username, ftr.sessionId());
            String response = mapper.writeValueAsString(fts);
            sendResponse("SEND_FILE", 800, response, ftr.sender());
            sendResponse("TRANSFER_RESPONSE", 800, "OK");
            pendingFTRequests.remove(ftr);
        }

        private void handleGameLaunch(String json) throws JsonProcessingException {
            if (isNotLoggedIn()) return;

            String lobbyName = getPropertyFromJson(json, "lobby");
            if (!lobbyName.matches(LOBBY_NAME_REGEX)) {
                sendResponse("GAME_LAUNCH", 850, lobbyName);
                return;
            }

            GuessingGame newGame = new GuessingGame(lobbyName, this);
            inGame = true;
            new Thread(newGame).start();
            sendResponse("GAME_LAUNCH", 800, "OK");
            activeGames.put(lobbyName, newGame);
            users.stream()
                    .filter(user -> !user.username.equals(this.username))
                    .forEach(user -> user.out.println("GAME_LAUNCHED " + wrapInJson("lobby", lobbyName)));
        }

        private void handleGameJoin(GuessingGame game) throws JsonProcessingException {
            if (isNotLoggedIn()) return;
            if (inGame) {
                sendResponse("GAME_JOIN", 855, "ERROR");
                return;
            }
            if (game.handleGameJoin(this))
                inGame = true;
        }

        private void handleGameGuess(GuessingGame game, String json) throws JsonProcessingException {
            if (isNotLoggedIn()) return;
            if (!inGame) {
                sendResponse("GAME_GUESS", 852, "ERROR");
                return;
            }
            try {
                int guess = Integer.parseInt(getPropertyFromJson(json, "guess"));
                game.handleGameGuess(this, guess);
            } catch (NumberFormatException e) {
                sendResponse("GAME_GUESS", 853, "ERROR");
            }
        }

        private void handleList() throws JsonProcessingException {
            List<String> online = users.stream().filter(user -> !this.username.equals(user.username)).map(user -> user.username).collect(Collectors.toList());
            sendResponse("LIST", 800, online);
        }

        private void handlePrivate(String json) throws JsonProcessingException {
            if (isNotLoggedIn()) return;

            String message = getPropertyFromJson(json, "message");
            String receiverName = getPropertyFromJson(json, "username");

            if (this.username.equals(receiverName)) {
                sendResponse("PRIVATE", 822, "ERROR");
                return;
            }

            try {
                Connection receiver = findUserByUsername(receiverName);
                receiver.out.println("PRIVATE " + mapper.writeValueAsString(new TextMessage(this.username, message)));
            } catch (UserNotFoundException e) {
                String notFoundJson = mapper.writeValueAsString(new NotFound("receiver", receiverName));
                sendResponse("PRIVATE", 711, notFoundJson);
            }
        }

        private void handleSecure(String json) throws JsonProcessingException {
            if (isNotLoggedIn()) return;

            String message = getPropertyFromJson(json, "message");
            String receiverName = getPropertyFromJson(json, "username");

            if (this.username.equals(receiverName)) {
                sendResponse("SECURE", 822, "ERROR");
                return;
            }
        }

        private void handlePublicKeyReq(String json) throws JsonProcessingException {
            KeyExchange ke = mapper.readValue(json, KeyExchange.class);
            try {
                findUserByUsername(ke.username()).out.println("PUBLIC_KEY_REQ "
                        + mapper.writeValueAsString(new KeyExchange(this.username, ke.key())));
            } catch (UserNotFoundException e) {
                String notFoundJson = mapper.writeValueAsString(new NotFound("user", ke.username()));
                sendResponse("PUBLIC_KEY_REQ", 711, notFoundJson);
            }
        }

        private void handlePublicKeyRes(String json) throws JsonProcessingException {
            KeyExchange ke = mapper.readValue(json, KeyExchange.class);
            try {
                findUserByUsername(ke.username()).out.println("PUBLIC_KEY_RES "
                        + mapper.writeValueAsString(new KeyExchange(this.username, ke.key())));
            } catch (UserNotFoundException e) {
                String notFoundJson = mapper.writeValueAsString(new NotFound("user", ke.username()));
                sendResponse("PUBLIC_KEY_RES", 711, notFoundJson);
            }
        }

        private void handleSessionKeyReq(String json) {

        }

        private void handleSecureReady(String json) {

        }


        private void handleBroadcast(String json) throws JsonProcessingException {
            if (isNotLoggedIn()) return;

            String message = getPropertyFromJson(json, "message");
            users.stream().filter(user -> !user.username.equals(this.username)).forEach(user -> {
                try {
                    user.out.println("BROADCAST " + mapper.writeValueAsString(new TextMessage(this.username, message)));
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

        private Connection findUserByUsername(String username) throws UserNotFoundException {
            Connection receiver = users.stream().filter(user -> user.username.equals(username)).findAny().orElse(null);
            if (receiver == null) throw new UserNotFoundException(username);
            return receiver;
        }

        private boolean isNotLoggedIn() throws JsonProcessingException {
            if (username.isBlank() && !hasLoggedIn) {
                sendResponse("LOGIN", 710, "ERROR"); // Its not a response TO login, but its universal so-
                return true;
            }
            return false;
        }

        public void sendMessageToClient(String message) {
            out.println(message);
        }

        public <T> void sendResponse(String to, int status, T content) throws JsonProcessingException {
            out.println("RESPONSE " + mapper.writeValueAsString(new Response<>(content, status, to)));
        }

        public <T> void sendResponse(String to, int status, T content, String username) throws JsonProcessingException {
            try {
                Connection user = findUserByUsername(username);
                user.out.println("RESPONSE " + mapper.writeValueAsString(new Response<>(content, status, to)));
            } catch (UserNotFoundException e) {
                System.err.println("Internal error: " + e);
            }
        }

        public void addPendingFileTransferRequest(FileTransferRequest ftr) {
            pendingFTRequests.add(ftr);
        }

        public void leaveGame() {
            inGame = false;
        }

        // -----------------------------------   HEARTBEAT   ------------------------------------------------

        private class Heartbeat implements Runnable {
            @Override
            public void run() {
                ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
                // Every X milliseconds execute a heartbeat.
                executor.scheduleAtFixedRate(new HeartbeatTask(), HEARTBEAT_PERIOD, HEARTBEAT_PERIOD, TimeUnit.SECONDS);
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

    // -----------------------------------   SHUTDOWN HANDLER   ------------------------------------------------
    private static class ShutdownHandler implements Runnable {

        Server server;

        public ShutdownHandler(Server server) {
            this.server = server;
        }

        @Override
        public void run() {
            for (Connection user : server.users) {
                try {
                    user.disconnect(702);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}