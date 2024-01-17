package features;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FileTransferThread implements Runnable {

    // -----------------------------------   CONSTANTS   ------------------------------------------------

    private int FILE_TRANSFER_PORT = 1338;
    private final int UUID_LENGTH = 16;
    private Map<UUID, Session> sessions;

    public FileTransferThread(int port) {
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
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            // [1byte-S][16byte-Session UUID][?bytes-File Bytes]
//            try {
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
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
        private UUID sessionId;
        private FileTransferActor receiver;
        private FileTransferActor sender;

        public Session(UUID sessionId, FileTransferActor receiver, FileTransferActor sender) {
            this.sessionId = sessionId;
            this.receiver = receiver;
            this.sender = sender;
        }

        public void setSessionId(UUID sessionId) {
            this.sessionId = sessionId;
        }

        public void setReceiver(FileTransferActor receiver) {
            this.receiver = receiver;
        }

        public void setSender(FileTransferActor sender) {
            this.sender = sender;
        }
    }

}
