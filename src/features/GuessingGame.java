package features;

import server.Server.Connection;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class GuessingGame implements Runnable {

    private List<Connection> players = new LinkedList<>();
    private int answer;
    private String lobbyName;

    public GuessingGame(String lobbyName, int lower, int upper) {
        this.answer = (new Random()).nextInt(lower, upper);
        this.lobbyName = lobbyName;
    }

    public void handleGameOperation () {

    }

    @Override
    public void run() {

    }

}

// every request has a "GAME" in it is handled specially
// every game request has the name of the lobby they are interacting with
//