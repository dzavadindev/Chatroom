package client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import messages.FileTransferRequest;
import messages.FileTransferResponse;
import messages.Response;

import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.UUID;

import static colors.ANSIColors.*;

public class FileTransferManager {

    private final PrintWriter out;
    private final ObjectMapper mapper = new ObjectMapper();
    // --------------- props ----------------
    private FileTransferRequest latestFTR;
    private File latestSelectedFile;
    private String SERVER_ADDRESS = "127.0.0.1";
    private final int FILE_TRANSFER_PORT = 1338;

    // --------------- config ---------------

    private final static String FILE_TRANSFER_DIRECTORY = "../exchange";
    // ^^^ CHANGE THIS TO YOUR DIRECTORY OR CHOICE, FOR ME IT WAS THE EXCHANGE FOLDER ^^^
    // ^^^ THAT I PUT OUTSIDE THE INTELLIJ PROJECT. REASON FOR THAT IS UNFATHOMABLY ^^^
    // ^^^ LARGE BUILD TIMES. MAYBE SHOULD HAVE CONSIDERED AN .env OR USING A PATH VARIABLE^^^
    private final static String EXTENSION_SPLITTING_REGEXP = "\\.(?=[^.]*$)";

    public FileTransferManager(PrintWriter out, String address) {
        this.SERVER_ADDRESS = address;
        this.out = out;
    }


    // ------------------------------   MESSAGE HANDLERS   -------------------------------------------

    public void handleAccept() {
        try {
            out.println("TRANSFER_RESPONSE " + mapper.writeValueAsString(new FileTransferResponse(true, "this.username", latestFTR.sessionId())));
            System.out.println("Exchange initiated");
            initFileTransfer(latestFTR.sessionId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleReject() {
        try {
            out.println("TRANSFER_RESPONSE " + mapper.writeValueAsString(new FileTransferResponse(false, "this.username", latestFTR.sessionId())));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleSendFile(String content) {
        try {
            String[] params = content.split(" ", 2);
            if (params.length != 2) {
                coloredPrint(ANSI_RED, "Provide both the file name and the receiving user");
                return;
            }

            String filename = params[0].trim();
            String receiver = params[1].trim();
            latestSelectedFile = new File(String.format("%s/%s", FILE_TRANSFER_DIRECTORY, filename));
            if (!latestSelectedFile.exists()) {
                coloredPrint(ANSI_MAGENTA, "FILE WITH NAME " + filename + " DOES NOT EXIST");
                return;
            }

            System.out.println("Loading your file...");
            String checksum = calculateChecksum(latestSelectedFile);

            FileTransferRequest ftr = new FileTransferRequest(filename, receiver, "", UUID.randomUUID(), checksum);
            out.println("SEND_FILE " + mapper.writeValueAsString(ftr));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleShowFilesInDirectory() {
        File directory = new File(FILE_TRANSFER_DIRECTORY);

        if (!directory.isDirectory()) {
            System.out.println("Provided path is not a directory.");
            return;
        }

        File[] filesList = directory.listFiles();
        if (filesList != null) {
            for (File file : filesList) {
                printFileDetails(file);
            }
        }
    }

    // ------------------------------   RECEIVE HANDLERS   ------------------------------------------

    public void handleReceiveTransferRequest(String json) {
        try {
            FileTransferRequest ftr = mapper.readValue(json, FileTransferRequest.class);
            coloredPrint(ANSI_GREEN, "You are receiving an inquiry for file exchange from " + ftr.sender() + " (" + ftr.filename() + ")");
            latestFTR = ftr;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------   SUCCESSFUL RESPONSE HANDLERS   -------------------------------------

    public void handleResponseSendFile(Response<?> response) {
        if (response.content().equals("OK")) {
            coloredPrint(ANSI_GREEN, "Request sent to the user");
            return;
        }
        try {
            FileTransferResponse ftr = mapper.readValue((String) response.content(), FileTransferResponse.class);
            if (ftr.status()) {
                coloredPrint(ANSI_GREEN, ftr.sender() + " has ACCEPTED your file transfer inquiry! Preparing transmission...");
                System.out.println("Exchange initiated");
                initFileTransfer(ftr.sessionId(), latestSelectedFile);
            } else coloredPrint(ANSI_GREEN, ftr.sender() + " has REJECTED your file transfer inquiry.");
        } catch (JsonProcessingException e) {
            coloredPrint(ANSI_RED, "Failed to parse the response to the file transfer response format");
        }
    }

    // -------------------------------------   UTIL   ------------------------------------------------

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
            output.write(receiverData);
            output.flush();

            String[] filename = latestFTR.filename().split(EXTENSION_SPLITTING_REGEXP);
            File file = new File(String.format("%s/%s_new.%s", FILE_TRANSFER_DIRECTORY, filename[0], filename[1]));

            try (FileOutputStream fo = new FileOutputStream(file)) {
                input.transferTo(fo);
            }

            System.out.println("Finished the file transfer!");
            System.out.println("Let us see if the file is intact...");
            if (latestFTR.checksum().equals(calculateChecksum(file)))
                coloredPrint(ANSI_CYAN, "File is intact!");
            else
                System.err.println("The file got corrupted during transfer, please try again.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] convertUUIDToBytes(UUID uuid) {
//        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
//        bb.putLong(uuid.getMostSignificantBits());
//        bb.putLong(uuid.getLeastSignificantBits());
//        return bb.array();
        return uuid.toString().getBytes();
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

    private String calculateChecksum(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] byteArray = new byte[1024];
                int bytesCount;

                // previously I have received a funny OutOfMemoryException because calculating the
                // checksum of a chonker of 6GB has proven problematic for readAllBytes of Files util
                // takes a shitton of time for large files, but hey, it is what it is.
                while ((bytesCount = fis.read(byteArray)) != -1) {
                    digest.update(byteArray, 0, bytesCount);
                }
            }

            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void printFileDetails(File file) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");

        String type = file.isDirectory() ? "directory" : "file";
        String size = formatFileSize(file.length());
        String modifiedDate = sdf.format(file.lastModified());

        System.out.printf("%s + %s %s | %s \n", file.getName(), type, size, modifiedDate);
    }

    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    // ----------------------------------   GETTERS   ------------------------------------------------

    public String getFileTransferDirectory() {
        return FILE_TRANSFER_DIRECTORY;
    }
}
