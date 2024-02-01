package features;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FileTransfer implements Runnable {

    // -----------------------------------   CONSTANTS   ------------------------------------------------

    private int FILE_TRANSFER_PORT = 1338;
    private final Map<UUID, Session> sessions;

    public FileTransfer(int port) {
        this.FILE_TRANSFER_PORT = port;
        this.sessions = new ConcurrentHashMap<>();
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(FILE_TRANSFER_PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new FileTransferActor(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("A file transfer actor has disconnected abruptly");
        }
    }

    private class FileTransferActor implements Runnable {
        private final OutputStream out;
        private final InputStream in;

        public FileTransferActor(Socket clientSocket) {
            try {
                this.out = clientSocket.getOutputStream();
                this.in = clientSocket.getInputStream();
                System.out.println("New file transfer actor");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            try {
                String role = new String(in.readNBytes(1), StandardCharsets.UTF_8);
                System.out.println("Role: " + role);
                UUID sessionId = UUID.nameUUIDFromBytes(in.readNBytes(36));
                System.out.println("UUID: " + sessionId);
                // I think it gets stuck here? Thats what the debugger seems to show
                // Also, from what I see only one thread of the actor is present, meaning it might have been interrupted

                Session session = sessions.computeIfAbsent(sessionId, k -> new Session());

                System.out.println("Session " + sessionId);

                switch (role) {
                    case "S" -> {
                        System.out.println("Setting sender");
                        session.setSender(this);
                        sessions.put(sessionId, session);
                    }
                    case "R" -> {
                        System.out.println("Setting receiver");
                        session.setReceiver(this);
                        sessions.put(sessionId, session);
                    }
                    default -> System.out.println("Unknown role: '" + role + "'");
                }

                if (session.receiver != null && session.sender != null) {
                    System.out.println("Starting transfer");
                    // todo: doesn't get in here
                    session.sender.in.transferTo(session.receiver.out);
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


// -----------------------------------   MESSAGE HANDLING   ------------------------------------------------

    private static class Session {
        private FileTransferActor receiver;
        private FileTransferActor sender;

        public Session() {
            this.receiver = null;
            this.sender = null;
        }

        public void setReceiver(FileTransferActor receiver) {
            this.receiver = receiver;
        }

        public void setSender(FileTransferActor sender) {
            this.sender = sender;
        }
    }

}
