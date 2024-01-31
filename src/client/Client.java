package client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import features.SecureManager;
import messages.*;

import javax.crypto.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.*;
import java.util.*;
import java.util.Map.Entry;

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
    private String gameLobby = "";
    private FileTransferRequest latestFTR;
    private File latestSelectedFile;
    private SecureManager secureManager;
    // --------------- config ---------------
    private final static String FILE_TRANSFER_DIRECTORY = "resources/";
    private final static String EXTENSION_SPLITTING_REGEXP = "\\.(?=[^.]*$)";
    private final static String SERVER_ADDRESS = "127.0.0.1";
    private final static int SERVER_PORT = 1337, FILE_TRANSFER_PORT = 1338;

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
        System.out.println("### !file <filename> <receiver> - send a file to the specified user");
        System.out.println("###### NOTE:");
        System.out.printf("###### The file you're planning to send must be in the \"%s\" directory.\n", FILE_TRANSFER_DIRECTORY);
        System.out.println("###### When specifying the file for transmission, include only the name and extension");
        System.out.println("### !accept/reject - accept or decline the latest file transfer offered");
        coloredPrint(ANSI_GRAY, "Planning to move that functionality to private message");
    }

    private void login(String username) {
        out.println("LOGIN " + wrapInJson("username", username.trim()));
        this.secureManager = new SecureManager(out);
    }

    private void leave() {
        try {
            out.println("LEAVE");
            in.close();
            out.close();
            socket.close();
            System.out.println("Bye bye!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void create(String lobby) {
        out.println("GAME_LAUNCH " + wrapInJson("lobby", lobby.trim()));
        gameLobby = lobby.trim();
    }

    private void join(String lobby) {
        out.println("GAME_JOIN " + wrapInJson("lobby", lobby.trim()));
        gameLobby = lobby.trim();
    }

    private void guess(String guess) throws JsonProcessingException {
        out.println("GAME_GUESS " + mapper.writeValueAsString(new GameGuess(gameLobby, Integer.parseInt(guess))));
    }

    private void direct(String data) throws JsonProcessingException {
        out.println("PRIVATE " + mapper.writeValueAsString(textMessageFromCommand(data)));
    }

    private void secure(String data) throws JsonProcessingException {
        secureManager.handleSendSecure(data);
    }

    private void list() {
        out.println("LIST");
    }

    private void send(String message) throws JsonProcessingException {
        if (!message.isBlank()) {
            out.println("BROADCAST " + mapper.writeValueAsString(new TextMessage("", message.trim())));
        } else System.out.println("Invalid message format");
    }

    private void file(String content) {
        String[] params = content.split(" ", 2);
        if (params.length != 2) {
            coloredPrint(ANSI_RED, "Provide both the file name and the receiving user");
            return;
        }
        String filename = params[0].trim();
        String receiver = params[1].trim();
        latestSelectedFile = new File(FILE_TRANSFER_DIRECTORY + filename);
        if (!latestSelectedFile.exists()) {
            coloredPrint(ANSI_MAGENTA, "FILE WITH NAME " + filename + " DOES NOT EXIST");
            return;
        }
        String checksum;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(Files.readAllBytes(latestSelectedFile.toPath()));
            byte[] digest = md.digest();
            checksum = HexFormat.of().formatHex(digest).toLowerCase();
            System.out.println(checksum);
            out.println("SEND_FILE " + mapper.writeValueAsString(
                    new FileTransferRequest(filename, receiver, "", UUID.randomUUID()))
            );
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void accept() throws JsonProcessingException {
        out.println("TRANSFER_RESPONSE " + mapper.writeValueAsString(new FileTransferResponse(true, "this.username", latestFTR.sessionId())));
        System.out.println("Initiating file transfer receiver side");
        initFileTransfer(latestFTR.sessionId());
    }

    private void reject() throws JsonProcessingException {
        out.println("TRANSFER_RESPONSE " + mapper.writeValueAsString(new FileTransferResponse(false, "this.username", latestFTR.sessionId())));
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

            case "LOGIN" -> coloredPrint(ANSI_CYAN, "Logged in successfully!");
            case "LIST" -> System.out.println(response.content());
            case "TRANSFER_RESPONSE" -> coloredPrint(ANSI_GREEN, "Your response was sent to the sender");
            case "GAME_LAUNCH" -> coloredPrint(ANSI_YELLOW, "Game started!");
            case "GAME_JOIN" -> {
                coloredPrint(ANSI_YELLOW, "Joined the game at " + response.content());
                gameLobby = (String) response.content();
            }
            case "GAME_GUESS" -> {
                switch ((int) response.content()) {
                    case -1 -> coloredPrint(ANSI_YELLOW, "Guess bigger!");
                    case 0 -> coloredPrint(ANSI_YELLOW, "You have guessed the number!");
                    case 1 -> coloredPrint(ANSI_YELLOW, "Guess lesser!");
                }
            }
            case "SEND_FILE" -> {
                if (response.content().equals("OK")) {
                    coloredPrint(ANSI_GREEN, "Request sent to the user");
                    return;
                }
                try {
                    FileTransferResponse ftr = mapper.readValue((String) response.content(), FileTransferResponse.class);

                    if (ftr.status()) {
                        coloredPrint(ANSI_GREEN, ftr.sender() + " has ACCEPTED your file transfer inquiry! Preparing transmission...");
                        System.out.println("Initiating file transfer senders side");
                        initFileTransfer(ftr.sessionId(), latestSelectedFile);
                    } else coloredPrint(ANSI_GREEN, ftr.sender() + " has REJECTED your file transfer inquiry.");
                } catch (JsonProcessingException e) {
                    coloredPrint(ANSI_RED, "Failed to parse the response to the file transfer response format");
                }
            }
            default ->
                    coloredPrint(ANSI_GRAY, "OK status received. Unknown destination of the response: " + response.to());
        }
    }


    // -----------------------------------   UTILS   ------------------------------------------------
    private void initFileTransfer(UUID sessionId, File file) {
        try (Socket senderSocket = new Socket(SERVER_ADDRESS, FILE_TRANSFER_PORT)) {
            OutputStream output = senderSocket.getOutputStream();
            byte[] senderData = createByteArray('S', sessionId);
            output.write(senderData);
            try (FileInputStream fs = new FileInputStream(file)) {
                fs.transferTo(output);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initFileTransfer(UUID sessionId) {
        try (Socket receiverSocket = new Socket(SERVER_ADDRESS, FILE_TRANSFER_PORT)) {
            OutputStream output = receiverSocket.getOutputStream();
            InputStream input = receiverSocket.getInputStream();
            byte[] receiverData = createByteArray('R', sessionId);
            System.out.println("Byte array " + Arrays.toString(receiverData));
            output.write(receiverData);
            String[] filename = latestFTR.filename().split(EXTENSION_SPLITTING_REGEXP);
            System.out.println(latestFTR.filename());
            System.out.println(Arrays.toString(filename));
            File file = new File(FILE_TRANSFER_DIRECTORY + filename[0] + "_new." + filename[1]);
            // socket is already closed at this point, so is the stream
            try (FileOutputStream fo = new FileOutputStream(file)) {
                input.transferTo(fo); // thus this input stream proves unusable and throws a Socket Closed exception
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Finished the file transfer!");
        // todo: verify checksum
    }

    public static byte[] convertUUIDToBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    private byte[] createByteArray(char letter, UUID uuid) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // Write Letter
        outputStream.write((byte) letter);
        // Write UUID
        byte[] uuidBytes = convertUUIDToBytes(uuid);
        outputStream.write(uuidBytes);
        return outputStream.toByteArray();
    }

    private void showGameLeaderboard(String json) throws JsonProcessingException {
        Leaderboard leaderboard = mapper.readValue(json, Leaderboard.class);
        coloredPrint(ANSI_YELLOW, "Game in lobby " + leaderboard.lobby() + " has ended! \n --- Scoreboard ---");
        int index = 2;

        List<Entry<String, Long>> scores = new ArrayList<>(leaderboard.leaderboard().entrySet());
        // sort the scores to get the quickest time on the first place
        scores.sort(Entry.comparingByValue());

        for (Entry<String, Long> score : scores) {
            if (scores.indexOf(score) == 0)
                rainbowPrint(index + ".) " + scores.get(0).getKey() + ": " + scores.get(0).getValue() + "ms");
            coloredPrint(ANSI_YELLOW, index + ".) " + score.getKey() + ": " + score.getValue() + "ms");
            index++;
        }
        System.out.println("------------------");
        gameLobby = "";
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
            case "GAME_LAUNCHED" ->
                    coloredPrint(ANSI_YELLOW, "A new game is brewing in lobby '" + getPropertyFromJson(json, "lobby") + "'! Join in!");
            case "GAME_START" ->
                    coloredPrint(ANSI_YELLOW, "The game in lobby \"" + getPropertyFromJson(json, "lobby") + "\" elapsed!");
            case "GAME_GUESSED" ->
                    coloredPrint(ANSI_YELLOW, getPropertyFromJson(json, "username") + " has guessed the number!");
            case "GAME_END" -> showGameLeaderboard(json);
            case "GAME_FAIL" -> {
                String lobby = getPropertyFromJson(json, "lobby");
                coloredPrint(ANSI_MAGENTA, "The game at " + lobby + " had has ended, due to lack of players");
            }
            case "SECURE" -> secureManager.handleReceiveSecure(json);
            case "PUBLIC_KEY_REQ" -> secureManager.handlePublicKeyReq(json);
            case "PUBLIC_KEY_RES" -> secureManager.handlePublicKeyRes(json);
            case "SESSION_KEY" -> secureManager.handleSessionKey(json);
            case "SECURE_READY" -> secureManager.handleSecureReady(json);
            case "TRANSFER_REQUEST" -> {
                FileTransferRequest ftr = mapper.readValue(json, FileTransferRequest.class);
                coloredPrint(ANSI_GREEN, "You are receiving an inquiry for file exchange from " + ftr.sender() + " (" + ftr.filename() + ")");
                latestFTR = ftr;
            }
            case "PARSE_ERROR" -> coloredPrint(ANSI_MAGENTA, "Parse error occurred processing your message");
            default -> coloredPrint(ANSI_MAGENTA, "Unknown message type or command from the server");
        }
    }

}