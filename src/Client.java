import java.io.*;
import java.net.Socket;

public class Client implements Runnable {

    private String host;
    private int port;

    public static void main(String[] args) {
        new Client().run();
    }

    public Client(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Client() {
        this.host = "127.0.0.1";
        this.port = 1337;
    }

    @Override
    public void run() {
        try {
            Socket socket = new Socket(host, port);
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


//        Thread listen = new Thread(() -> {
//            try {
//                // CLIENT LISTENING
//                InputStream is = null;
//                is = socket.getInputStream();
//
//                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
//                System.out.println(reader.readLine());
//
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        });
//
//        Thread sending = new Thread(() -> {
//            // CLIENT SENDING
//            OutputStream os = socket.getOutputStream();
//            PrintWriter writer = new PrintWriter(os);
//            writer.println("Hello world");
//            // The flush method sends the messages from the print writer buffer to client.
//            writer.flush();
//        });
    }
}