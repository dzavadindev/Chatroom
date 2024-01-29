package features;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FileTransfer implements Runnable {

    // -----------------------------------   CONSTANTS   ------------------------------------------------

    private int FILE_TRANSFER_PORT = 1338;
    private final Map<UUID, Session> sessions;

    public FileTransfer(int port) {
        this.FILE_TRANSFER_PORT = port;
        this.sessions = new HashMap<>();
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
                UUID sessionId = UUID.nameUUIDFromBytes(in.readNBytes(36));

                Session session = sessions.get(sessionId) != null ? sessions.get(sessionId) : new Session();
                sessions.putIfAbsent(sessionId, session);

                System.out.println("Role " + role);
                System.out.println("Session " + sessionId);

                switch (role) {
                    case "S" -> {
                        System.out.println("Setting sender");
                        session.setSender(this);
                    }
                    case "R" -> {
                        System.out.println("Setting receiver");
                        session.setReceiver(this);
                    }
                    default -> System.out.println("Unknown role: '" + role + "'"); // todo: what do I do?
                }

                // todo:keep sockets alive

                if (session.receiver != null && session.sender != null) {
                    System.out.println("Starting transfer");
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
