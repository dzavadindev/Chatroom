package features;

import server.Server.Connection;

import java.util.*;

public class GuessingGame implements Runnable {
    private final Set<Connection> players = new HashSet<>();
    private final Map<String, Long> leaderboard = new HashMap<>();
    private final int answer;
    private final String lobbyName;

    public GuessingGame(String lobbyName, int lower, int upper) {
        this.answer = (new Random()).nextInt(lower, upper);
        this.lobbyName = lobbyName;
    }

    public void addPlayerToGame(Connection player) {
        players.add(player);
    }

    @Override
    public void run() {
//      implement the collection period
//      implement the game timer
//      implement the game closure (all guessed / timeout)
    }

}