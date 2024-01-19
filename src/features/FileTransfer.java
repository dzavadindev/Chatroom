package features;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FileTransfer implements Runnable {

    // -----------------------------------   CONSTANTS   ------------------------------------------------

    private int FILE_TRANSFER_PORT = 1338;
    private Map<UUID, Session> sessions;

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

    private static class FileTransferActor implements Runnable {
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
            // [1byte-S][36byte-Session UUID][?bytes-File Bytes]

//            in.readNBytes(1) -> Save as String, will be role
//            in.readNBytes(36) -> Save as String, will be sessionID
            try {
                String role = new String(in.readNBytes(1), StandardCharsets.UTF_8);
                UUID sessionId = UUID.nameUUIDFromBytes(in.readNBytes(36));
                System.out.println("Actor role: " + role);
                System.out.println("Actors session: " + sessionId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

// -----------------------------------   MESSAGE HANDLING   ------------------------------------------------


    public static UUID convertBytesToUUID(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        long high = byteBuffer.getLong();
        long low = byteBuffer.getLong();
        return new UUID(high, low);
    }

// -----------------------------------   MESSAGE HANDLING   ------------------------------------------------


    private static class Session {
        private FileTransferActor receiver;
        private FileTransferActor sender;

        public Session(FileTransferActor receiver, FileTransferActor sender) {
            this.receiver = receiver;
            this.sender = sender;
        }

        public void setReceiver(FileTransferActor receiver) {
            this.receiver = receiver;
        }

        public void setSender(FileTransferActor sender) {
            this.sender = sender;
        }
    }

}
