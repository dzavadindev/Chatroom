package server;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class Server {

    private final ObjectMapper mapper;

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
                new Thread(new Connection(socket)).start();
            }
        } catch (Exception err) {
            throw new RuntimeException(err);
        }
    }

    static class Connection implements Runnable {
        private final Socket allocatedSocket;
        private final PrintWriter out;
        private final BufferedReader in;

        public Connection(Socket allocatedSocket) throws IOException {
            this.allocatedSocket = allocatedSocket;
            this.out = new PrintWriter(allocatedSocket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(allocatedSocket.getInputStream()));
        }

        @Override
        public void run() {
            try {
                new Thread(new Heartbeat(out)).start();
                System.out.println("New connection to the server");
                while (!allocatedSocket.isClosed()) {
                    messageHandler(in.readLine());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void messageHandler (String message) {
            String command, contents;
            String[] messageParts;
            try {
                messageParts = message.split(" ", 2);
                command = messageParts[0];
                contents = messageParts[1];
            } catch (ArrayIndexOutOfBoundsException err) {
                command = message;
                contents = "";
            }
            switch (command) {
                case "PONG" -> handleHeartbeat();
                case "LOGIN" -> System.out.println("Tried to log in");
                default -> System.out.println("Unknown command");
            }
        }

        private void handleHeartbeat () {
            System.out.println("Heartbeat successful");
        }

    }

    static class Heartbeat implements Runnable {
        private final PrintWriter out;

        public Heartbeat(PrintWriter out) {
            this.out = out;
        }

        @Override
        public void run() {
            Timer timer = new Timer("Heartbeat");
            // Every 3 minutes execute a heartbeat.
            timer.scheduleAtFixedRate(new HeartbeatTask(),0, (long) 1000 * 60);
        }

        class HeartbeatTask extends TimerTask {
            @Override
            public void run() {
                out.println("PING");
            }
        }
    }
}
