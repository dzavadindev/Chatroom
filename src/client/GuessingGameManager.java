package client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import messages.GameGuess;
import messages.Leaderboard;
import messages.Response;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static colors.ANSIColors.*;
import static util.Util.*;

public class GuessingGameManager {
    private final PrintWriter out;
    private final ObjectMapper mapper = new ObjectMapper();

    // --------------- props ----------------

    private String gameLobby = "";

    public GuessingGameManager(PrintWriter out) {
        this.out = out;
    }

    // ------------------------------   MESSAGE HANDLERS   -------------------------------------------


    public void handleCreate(String lobby) {
        out.println("GAME_LAUNCH " + wrapInJson("lobby", lobby.trim()));
        gameLobby = lobby.trim();
    }

    public void handleJoin(String lobby) {
        out.println("GAME_JOIN " + wrapInJson("lobby", lobby.trim()));
        gameLobby = lobby.trim();
    }

    public void handleGuess(String guess) {
        try {
            out.println("GAME_GUESS " + mapper.writeValueAsString(new GameGuess(gameLobby, Integer.parseInt(guess))));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (NumberFormatException e) {
            System.err.println("Please enter a number");
        }
    }

    // ------------------------------   RECEIVE HANDLERS   -------------------------------------------

    public void handleReceiveLaunched(String json) {
        try {
            coloredPrint(ANSI_YELLOW, "A new game is brewing in lobby '" + getPropertyFromJson(json, "lobby") + "'! Join in!");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleReceiveStart(String json) {
        try {
            coloredPrint(ANSI_YELLOW, "The game in lobby \"" + getPropertyFromJson(json, "lobby") + "\" elapsed!");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleReceiveGuessed(String json) {
        try {
            coloredPrint(ANSI_YELLOW, getPropertyFromJson(json, "username") + " has guessed the number!");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleReceiveEnd(String json) {
        try {
            Leaderboard leaderboard = mapper.readValue(json, Leaderboard.class);
            coloredPrint(ANSI_YELLOW, "Game in lobby " + leaderboard.lobby() + " has ended! \n --- Scoreboard ---");
            int index = 1;

            List<Map.Entry<String, Long>> scores = new ArrayList<>(leaderboard.leaderboard().entrySet());
            // sort the scores to get the quickest time on the first place
            scores.sort(Map.Entry.comparingByValue());

            for (Map.Entry<String, Long> score : scores) {
                if (scores.indexOf(score) == 0)
                    rainbowPrint(index + ".) " + scores.get(0).getKey() + ": " + scores.get(0).getValue() + "ms");
                else
                    coloredPrint(ANSI_YELLOW, index + ".) " + score.getKey() + ": " + score.getValue() + "ms");
                index++;
            }
            coloredPrint(ANSI_YELLOW, "------------------");
            gameLobby = "";
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleReceiveFailed(String json) {
        try {
            String lobby = getPropertyFromJson(json, "lobby");
            coloredPrint(ANSI_MAGENTA, "The game at " + lobby + " had has ended, due to lack of players");
            gameLobby = "";
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------   SUCCESSFUL RESPONSE HANDLERS   --------------------------------------

    public void handleSuccessfulLaunch() {
        coloredPrint(ANSI_YELLOW, "Game started!");
    }

    public void handleSuccessfulJoin(Response<?> response) {
        coloredPrint(ANSI_YELLOW, "Joined the game at " + response.content());
        gameLobby = (String) response.content();
    }

    public void handleSuccessfulGuess(Response<?> response) {
        switch ((int) response.content()) {
            case -1 -> coloredPrint(ANSI_YELLOW, "Guess bigger!");
            case 0 -> coloredPrint(ANSI_YELLOW, "You have guessed the number!");
            case 1 -> coloredPrint(ANSI_YELLOW, "Guess less!");
        }
    }

}