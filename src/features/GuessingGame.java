package features;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import messages.Leaderboard;
import server.Server.Connection;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GuessingGame implements Runnable {
    // -----------------------------------   SETUP   ------------------------------------------------

    private final Set<Connection> players = new HashSet<>();
    private final Map<String, Long> leaderboard = new HashMap<>();

    // -----------------------------------   CONSTANTS   ------------------------------------------------

    private final int COLLECTION_PERIOD = 1;
    private final int GAME_TIMER = 5;

    // -----------------------------------   CONFIG   ------------------------------------------------

    private final int answer;
    private final String lobbyName;
    private boolean acceptingPlayers = true;


    public GuessingGame(String lobbyName, int lower, int upper) {
        this.answer = (new Random()).nextInt(lower, upper);
        this.lobbyName = lobbyName;
    }

    @Override
    public void run() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(new CollectionPeriod(), COLLECTION_PERIOD, TimeUnit.MINUTES);
        executor.schedule(new EndGame(), COLLECTION_PERIOD + GAME_TIMER, TimeUnit.MINUTES);
    }

    public void handleGameJoin(Connection player) {
        try {
            if (acceptingPlayers) {
                if (isInGame(player)) {
                    player.sendResponse("GAME_JOIN", 858, lobbyName);
                    return;
                }
                players.add(player);
//              notifyEveryone(); // todo?
                player.sendResponse("GAME_JOIN", 800, "OK");
            } else {
                player.sendResponse("GAME_JOIN", 859, lobbyName);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleGameGuess(Connection player, int guess) {
        try {
            if (isInGame(player)) {
                player.sendResponse("GAME_JOIN", 858, lobbyName);
                return;
            }
            if (guess > answer)
                player.sendResponse("GAME_GUESS", 800, 1);
            else if (guess < answer)
                player.sendResponse("GAME_GUESS", 800, -1);
            else
                player.sendResponse("GAME_GUESS", 800, 0);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // -----------------------------------   UTILS   ------------------------------------------------

    private boolean isInGame(Connection player) {
        Connection foundPlayer = players.stream()
                .filter(pl -> pl.equals(player))
                .findAny()
                .orElse(null);
        return foundPlayer != null;
    }

    private void notifyEveryone(String message) {
        players.forEach(player -> player.sendMessageToClient(message));
    }

    // -----------------------------------   GAME LIFECYCLE   ------------------------------------------------

    class CollectionPeriod extends TimerTask {
        @Override
        public void run() {
            for (Connection player : players) {
                leaderboard.put(player.username, (long) GAME_TIMER);
            }
            acceptingPlayers = false;
        }
    }

    class EndGame extends TimerTask {
        @Override
        public void run() {
            try {
                ObjectMapper mapper = new ObjectMapper();
                notifyEveryone("GAME_END " + mapper.writeValueAsString(new Leaderboard(lobbyName, leaderboard)));
                Thread.currentThread().interrupt(); // todo: does this interrupt the main, or the task thread?
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}