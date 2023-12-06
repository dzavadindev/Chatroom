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

    private static class Connection implements Runnable {
        private final Socket allocatedSocket;
        private final PrintWriter out;
        private final BufferedReader in;
        private boolean alive = true;
        private final long HEARTBEAT_REACTION = 1000 * 5;
        private final long HEARTBEAT_PERIOD = 1000 * 30;

        public Connection(Socket allocatedSocket) throws IOException {
            this.allocatedSocket = allocatedSocket;
            this.out = new PrintWriter(allocatedSocket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(allocatedSocket.getInputStream()));
        }

        @Override
        public void run() {
            try {
                new Thread(new Heartbeat()).start();
                System.out.println("New connection to the server");
                while (!allocatedSocket.isClosed()) {
                    messageHandler(in.readLine());
                }
            } catch (IOException | InterruptedException e) {
//                throw new RuntimeException(e);
                System.err.println(e.getMessage());
            }
        }

        private void messageHandler(String message) throws InterruptedException {
            String[] messageParts = message.split(" ", 2);
            String command = messageParts[0];
            String contents = messageParts.length == 2 ? messageParts[1] : "";

            switch (command) {
                case "PONG" -> handleHeartbeat();
                case "LOGIN" -> System.out.println("Tried to log in");
                default -> System.out.println("Unknown command");
            }
        }

        private void handleHeartbeat(){
            alive = true;
            System.out.println("Heartbeat successful");
        }

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
                        if (!alive) {
                            out.println("DISCONNECTED {\"reason\": \"Heartbeat failed\"}");
                            allocatedSocket.close();
                        }
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}
