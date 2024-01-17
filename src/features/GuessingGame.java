package features;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import messages.Leaderboard;
import server.Server.Connection;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static util.Util.*;

public class GuessingGame implements Runnable {
    // -----------------------------------   SETUP   ------------------------------------------------

    private final Set<Connection> players = new HashSet<>();
    private final Map<String, Long> leaderboard = new HashMap<>();

    // -----------------------------------   CONSTANTS   ------------------------------------------------

    private final int COLLECTION_PERIOD = 1;
    private final int GAME_TIMER = 1;

    // -----------------------------------   CONFIG   ------------------------------------------------

    private final int answer;
    private final String lobbyName;
    private boolean collectionPeriod = true, guessingPeriod = false;


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
            if (collectionPeriod) {
                if (isInGame(player)) {
                    player.sendResponse("GAME_JOIN", 858, lobbyName);
                    return;
                }
                players.add(player);
//              notifyEveryone(); // todo?
                player.sendResponse("GAME_JOIN", 800, lobbyName);
            } else {
                player.sendResponse("GAME_JOIN", 859, lobbyName);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleGameGuess(Connection player, int guess) {
        try {
            if (guessingPeriod) {
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
            } else player.sendResponse("GAME_GUESS", 851, "ERROR");
            // todo add 851 as code for guessing during collection period
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

    class CollectionPeriod implements Runnable {
        @Override
        public void run() {
            System.out.println("Started the game at '" + lobbyName + "'");
            for (Connection player : players) {
                leaderboard.put(player.username, (long) GAME_TIMER);
                player.sendMessageToClient("GAME_START " + wrapInJson("lobby", lobbyName));
            }
            collectionPeriod = false;
            guessingPeriod = true;
        }
    }

    class EndGame implements Runnable {
        @Override
        public void run() {
            try {
                System.out.println("Ended the game at lobby '" + lobbyName + "'");
                ObjectMapper mapper = new ObjectMapper();
                players.forEach(Connection::leaveGame);
                notifyEveryone("GAME_END " + mapper.writeValueAsString(new Leaderboard(lobbyName, leaderboard)));
                Thread.currentThread().interrupt(); // todo: does this interrupt the main, or the task thread?
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}