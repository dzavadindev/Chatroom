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
    private long startTime;
    private final ObjectMapper mapper = new ObjectMapper();


    // -----------------------------------   CONSTANTS   ------------------------------------------------

    private final int COLLECTION_PERIOD = 20; // SECONDS
    private final int GAME_TIMER = 4 * 60; // SECONDS
    private final int GAME_UPPER_BOUND = 50;
    private final int GAME_LOWER_BOUND = 1;

    // -----------------------------------   CONFIG   ------------------------------------------------

    private final int answer;
    private final String lobbyName;
    private GameState gameState = GameState.COLLECTION;
    private int playersGuessed = 0;


    public GuessingGame(String lobbyName, Connection initiator) {
        players.add(initiator);
        this.answer = (new Random()).nextInt(GAME_LOWER_BOUND, GAME_UPPER_BOUND + 1);
        this.lobbyName = lobbyName;

    }

//     Potential expansion of configurable upper and lower bounds of the game
//    public GuessingGame(String lobbyName, Connection initiator, int lower, int upper) {
//        players.add(initiator);
//        this.answer = (new Random()).nextInt(lower, upper + 1);
//        this.lobbyName = lobbyName;
//    }


    @Override
    public void run() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        // collection period is short, thus seconds
        executor.schedule(new CollectionPeriod(), COLLECTION_PERIOD, TimeUnit.SECONDS);
        // the game itself is longer, and because both tasks are being scheduled now
        // the game timer will be equal the collection period + the game time itself
        int GAME_TIMER_SUMMED = COLLECTION_PERIOD + GAME_TIMER;
        executor.schedule(new EndGame(true), GAME_TIMER_SUMMED, TimeUnit.SECONDS);
    }

    public boolean handleGameJoin(Connection player) {
        try {
            if (gameState == GameState.COLLECTION) {
                if (isInGame(player)) {
                    player.sendResponse("GAME_JOIN", 856, lobbyName);
                    return false;
                }
                players.add(player);
//              notifyEveryone(); // todo: should other players be informed of a new one joining?
                player.sendResponse("GAME_JOIN", 800, lobbyName);
                return true;
            } else {
                player.sendResponse("GAME_JOIN", 857, lobbyName);
                return false;
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleGameGuess(Connection player, int guess) {
        try {
            if (gameState == GameState.ELAPSED) {
                if (!isInGame(player)) {
                    player.sendResponse("GAME_GUESS", 852, lobbyName);
                    return;
                }

                if (guess < GAME_LOWER_BOUND || guess > GAME_UPPER_BOUND) {
                    player.sendResponse("GAME_GUESS", 854, GAME_LOWER_BOUND + " - " + GAME_UPPER_BOUND);
                    return;
                }

                if (guess > answer)
                    player.sendResponse("GAME_GUESS", 800, 1);
                else if (guess < answer)
                    player.sendResponse("GAME_GUESS", 800, -1);
                else {
                    player.sendResponse("GAME_GUESS", 800, 0);
                    // nanoTime of System returns the current time to nanoseconds
                    // division by 1_000_000 is the conversion to milliseconds
                    long playerGuessTimeMs = (System.nanoTime() - startTime) / 1_000_000;
                    leaderboard.put(player.username, playerGuessTimeMs);
                    playersGuessed++;
                    if (playersGuessed == players.size())
                        new EndGame(true).run();
                }

            } else player.sendResponse("GAME_GUESS", 851, "ERROR");
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
            if (players.size() == 1) {
                notifyEveryone("GAME_FAIL " + wrapInJson("lobby", lobbyName));
                new EndGame(false).run();
            }
            System.out.println("Started the game at '" + lobbyName + "', answer is " + answer);
            System.out.println("Players: " + players);
            for (Connection player : players) {
                leaderboard.put(player.username, (long) GAME_TIMER);
                player.sendMessageToClient("GAME_START " + wrapInJson("lobby", lobbyName));
            }
            gameState = GameState.ELAPSED;
            startTime = System.nanoTime();
        }
    }

    class EndGame implements Runnable {

        boolean showLeaderboard;

        public EndGame(boolean showLeaderboard) {
            this.showLeaderboard = showLeaderboard;
        }

        @Override
        public void run() {
            try {
                System.out.println("Ended the game at lobby '" + lobbyName + "'");
                if (showLeaderboard) {
                    notifyEveryone("GAME_END " + mapper.writeValueAsString(new Leaderboard(lobbyName, leaderboard)));
                }
                players.forEach(Connection::leaveGame);
                Thread.currentThread().interrupt();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private enum GameState {
        COLLECTION,
        ELAPSED
    }

}