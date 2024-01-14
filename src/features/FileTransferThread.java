package features;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FileTransferThread implements Runnable {

    private int FILE_TRANSFER_PORT = 1338;
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
        private final int UUID_LENGTH = 32;
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
            // [1byte-S][32byte-Session UUID][?bytes-File Bytes]
            try {
                byte[] buffer = new byte[1024];
                int bytesRead;

                // Temporary buffer to store the first 33 bytes (1 byte for letter, 32 bytes for UUID)
                byte[] tempBuffer = new byte[1 + UUID_LENGTH];
                int tempBufferFilled = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    for (int i = 0; i < bytesRead; i++) {
                        if (tempBufferFilled < tempBuffer.length) {
                            tempBuffer[tempBufferFilled++] = buffer[i];
                        }

                        if (tempBufferFilled == tempBuffer.length) {
                            // We have our 1 byte letter and 32 bytes UUID, process them
                            processFirstByteAndUUID(tempBuffer);
                        }
                    }
                }

                // Handle case if the stream ends before we get 33 bytes
                if (tempBufferFilled > 0) {
                    System.out.println("Stream ended before we could read 33 bytes");
                    // Handle this situation as required
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void processFirstByteAndUUID(byte[] data) {
            byte firstByte = data[0];
            byte[] uuidBytes = new byte[UUID_LENGTH];
            System.arraycopy(data, 1, uuidBytes, 0, UUID_LENGTH);

            // Now process the first byte and UUID bytes
            System.out.println("First byte: " + (char) firstByte);
            System.out.println("UUID bytes: " + new String(uuidBytes));
            // Add your processing logic here
        }
    }

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
