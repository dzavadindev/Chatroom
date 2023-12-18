package features;

import server.Server.Connection;

import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class GuessingGame implements Runnable {

    private List<Connection> players = new LinkedList<>();
    private OutputStream serverOut;
    private int answer;

    public GuessingGame(OutputStream serverOut, int lower, int upper) {
        this.answer = (new Random()).nextInt(lower, upper);
        this.serverOut = serverOut;
    }

    @Override
    public void run() {

    }

}
